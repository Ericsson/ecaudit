/*
 * Copyright 2018 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecaudit.logger;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.jetbrains.annotations.Nullable;

/**
 * Implements an {@link AuditLogger} that writes {@link AuditEntry} instance into file using {@link Logger}.
 * <br>
 * It is possible to configure a parameterized log message by providing a formatting string {@link Slf4jAuditLoggerConfig#getLogFormat()}.
 * The format for a field is {@code ${<Field Name>}}. With a formatting string like this: {@code "${USER} executed '${OPERATION}' from ${CLIENT_IP}"},
 * the logged string could look like this:
 * <ul>
 * <li>"Duke executed 'select * from students;' from 1.2.3.4"</li>
 * </ul>
 * <p>
 * Conditional formatting of fields is also available, which makes it possible to only log the field value and
 * its descriptive text if a value exists. The formatting string {@code "user:${USER}, client:${CLIENT_IP}{?, executed from batch:${BATCH_ID}?}"}
 * will log different things depending on if there is a BATCH_ID or not:
 * <ul>
 * <li>"user:Duke, client:1.2.3.4, executed from batch:e501f872-9aab-4f6b-9a52-8ed2f67b1320"</li>
 * <li>"user:Duke, client:1.2.3.4"</li>
 * </ul>
 */
public class Slf4jAuditLogger implements AuditLogger
{
    public static final String AUDIT_LOGGER_NAME = "ECAUDIT";

    private static final String FIELD_EXP = "\\$\\{(.*?)}"; // Non-greedy matching of field
    private static final String OPTIONAL_FIELD_EXP = "\\{\\?(.*?)\\$\\{(.*?)}(.*?)\\?}";
    private static final String COMBINED_FIELDS_EXP = FIELD_EXP + '|' + OPTIONAL_FIELD_EXP;
    private static final Pattern FIELD_PATTERN = Pattern.compile(COMBINED_FIELDS_EXP);

    private final Map<String, Function<AuditEntry, Object>> availableFieldFunctionMap;
    private final Logger auditLogger; // NOPMD
    private final String logTemplate;
    private final List<Function<AuditEntry, Object>> fieldFunctions;

    /**
     * Constructor, injects logger from {@link LoggerFactory}.
     *
     * @param parameters the custom strategy parameters
     */
    public Slf4jAuditLogger(Map<String, String> parameters)
    {
        this(new Slf4jAuditLoggerConfig(parameters), LoggerFactory.getLogger(AUDIT_LOGGER_NAME));
    }

    /**
     * Test constructor.
     *
     * @param auditConfig the audit configuration which provide the log format
     * @param logger      the logger backend to use for audit logs
     */
    @VisibleForTesting
    Slf4jAuditLogger(Slf4jAuditLoggerConfig auditConfig, Logger logger)
    {
        auditLogger = logger;
        availableFieldFunctionMap = getAvailableFieldFunctionMap(auditConfig);
        String logFormat = auditConfig.getLogFormat();
        logTemplate = getTemplateFromFormatString(logFormat);
        fieldFunctions = getFieldFunctions(logFormat);
    }

    static Map<String, Function<AuditEntry, Object>> getAvailableFieldFunctionMap(Slf4jAuditLoggerConfig auditConfig)
    {
        return ImmutableMap.<String, Function<AuditEntry, Object>>builder()
               .put("CLIENT_IP", entry -> entry.getClientAddress().getAddress().getHostAddress())
               .put("CLIENT_PORT", entry -> getPortOrNull(entry.getClientAddress()))
               .put("COORDINATOR_IP", entry -> entry.getCoordinatorAddress().getHostAddress())
               .put("USER", AuditEntry::getUser)
               .put("BATCH_ID", entry -> entry.getBatchId().orElse(null))
               .put("STATUS", AuditEntry::getStatus)
               .put("OPERATION", entry -> entry.getOperation().getOperationString())
               .put("OPERATION_NAKED", entry -> entry.getOperation().getOperationStringWithoutBoundValues())
               .put("TIMESTAMP", getTimeFunction(auditConfig))
               .build();
    }

    @Nullable
    private static Integer getPortOrNull(InetSocketAddress address)
    {
        return address.getPort() != AuditEntry.UNKNOWN_PORT ? address.getPort() : null;
    }

    static Function<AuditEntry, Object> getTimeFunction(Slf4jAuditLoggerConfig auditConfig)
    {
        return auditConfig.getTimeFormatter()
                          .map(Slf4jAuditLogger::getFormattedTimestamp)
                          .orElse(AuditEntry::getTimestamp);
    }

    private static Function<AuditEntry, Object> getFormattedTimestamp(DateTimeFormatter formatter)
    {
        return auditEntry -> formatter.format(Instant.ofEpochMilli(auditEntry.getTimestamp()));
    }

    private static String getTemplateFromFormatString(String logFormat)
    {
        return logFormat.replaceAll(OPTIONAL_FIELD_EXP, "{}{}{}").replaceAll(FIELD_EXP, "{}");
    }

    final List<Function<AuditEntry, Object>> getFieldFunctions(String logFormat)
    {
        List<Function<AuditEntry, Object>> fieldFunctions = new ArrayList<>();
        Matcher matcher = FIELD_PATTERN.matcher(logFormat);
        while (matcher.find())
        {
            String normalField = matcher.group(1);
            if (normalField != null)
            {
                Function<AuditEntry, Object> valueSupplier = availableFieldFunctionMap.computeIfAbsent(normalField, throwConfigurationException());
                fieldFunctions.add(valueSupplier);
            }
            else // Optional field
            {
                String descriptionLeft = matcher.group(2);
                String innerField = matcher.group(3);
                String descriptionRight = matcher.group(4);
                Function<AuditEntry, Object> valueSupplier = availableFieldFunctionMap.computeIfAbsent(innerField, throwConfigurationException());
                fieldFunctions.add(valueSupplier.andThen(getDescriptionIfValuePresent(descriptionLeft)));
                fieldFunctions.add(valueSupplier.andThen(Slf4jAuditLogger::getValueOrEmptyString));
                fieldFunctions.add(valueSupplier.andThen(getDescriptionIfValuePresent(descriptionRight)));
            }
        }
        return fieldFunctions;
    }

    private static Function<String, Function<AuditEntry, Object>> throwConfigurationException()
    {
        return key -> {
            throw new ConfigurationException("Unknown log format field: " + key);
        };
    }

    static Function<Object, String> getDescriptionIfValuePresent(String description)
    {
        return value -> value != null ? description : "";
    }

    static Object getValueOrEmptyString(Object value)
    {
        return value != null ? value : "";
    }

    @Override
    public void log(AuditEntry logEntry)
    {
        Object[] fieldValues = fieldFunctions.stream()
                                             .map(valueSupplier -> new ToStringer<>(logEntry, valueSupplier))
                                             .toArray();
        auditLogger.info(logTemplate, fieldValues);
    }
}
