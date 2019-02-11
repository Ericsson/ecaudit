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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.AuditConfig;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.AuditConfigurationLoader;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.AuditYamlConfigurationLoader;
import org.apache.cassandra.exceptions.ConfigurationException;

import static java.util.stream.Collectors.toList;

/**
 * Implements an {@link AuditLogger} that writes {@link AuditEntry} instance into file using {@link Logger}.
 * <br>
 * It is possible to configure a parameterized log message by providing a formatting string {@link AuditConfig#getLogFormat()}.
 * The format for a parameter is {@code ${<Parameter Name>}}. With a formatting string like this: {@code "${USER} executed '${OPERATION}' from ${CLIENT}"},
 * the logged string could look like this:
 * <ul>
 * <li>"Duke executed 'select * from students;' from 1.2.3.4"</li>
 * </ul>
 * <p>
 * Conditional formatting of parameters is also available, which makes it possible to only log the parameter value and
 * its descriptive text if a value exists. The formatting string {@code "user:${USER}, client:${CLIENT}{?, executed from batch:${BATCH_ID}?}"}
 * will log different things depending on if there is a BATCH_ID or not:
 * <ul>
 * <li>"user:Duke, client:1.2.3.4, executed from batch:e501f872-9aab-4f6b-9a52-8ed2f67b1320"</li>
 * <li>"user:Duke, client:1.2.3.4"</li>
 * </ul>
 */
public class Slf4jAuditLogger implements AuditLogger
{
    public static final String AUDIT_LOGGER_NAME = "ECAUDIT";

    private static final String PARAMETER_EXP = "\\$\\{(.*?)}"; // Non-greedy matching of parameter
    private static final String OPTIONAL_PARAMETER_EXP = "\\{\\?(.*?)\\$\\{(.*?)}(.*?)\\?}";
    private static final String COMBINED_PARAMETERS_EXP = PARAMETER_EXP + '|' + OPTIONAL_PARAMETER_EXP;
    private static final Pattern PARAMETER_PATTERN = Pattern.compile(COMBINED_PARAMETERS_EXP);

    static final ImmutableMap<String, Function<AuditEntry, Object>> AVAILABLE_PARAMETER_FUNCTIONS =
    ImmutableMap.<String, Function<AuditEntry, Object>>builder().put("CLIENT", entry -> entry.getClientAddress().getHostAddress())
                                                                .put("USER", AuditEntry::getUser)
                                                                .put("BATCH_ID", entry -> entry.getBatchId().orElse(null))
                                                                .put("STATUS", AuditEntry::getStatus)
                                                                .put("OPERATION", entry -> entry.getOperation().getOperationString())
                                                                .build();

    private final Logger auditLogger; // NOSONAR
    private final String logTemplate;
    private final List<Function<AuditEntry, Object>> parameterFunctions;

    /**
     * Default constructor, injects logger from {@link LoggerFactory}.
     */
    public Slf4jAuditLogger()
    {
        this(LoggerFactory.getLogger(AUDIT_LOGGER_NAME), AuditYamlConfigurationLoader.withSystemProperties());
    }

    /**
     * Test constructor.
     *
     * @param logger              the logger backend to use for audit logs
     * @param configurationLoader the configuration to load the log format from
     */
    @VisibleForTesting
    Slf4jAuditLogger(Logger logger, AuditConfigurationLoader configurationLoader)
    {
        auditLogger = logger;
        String logFormat = getLogFormatConfiguration(configurationLoader);
        logTemplate = getTemplateFromFormatString(logFormat);
        parameterFunctions = getParameterFunctions(logFormat);
    }

    static String getLogFormatConfiguration(AuditConfigurationLoader configurationLoader)
    {
        return configurationLoader.configExist()
               ? configurationLoader.loadConfig().getLogFormat()
               : AuditConfig.DEFAULT_FORMAT;
    }

    private static String getTemplateFromFormatString(String logFormat)
    {
        return logFormat.replaceAll(OPTIONAL_PARAMETER_EXP, "{}{}{}").replaceAll(PARAMETER_EXP, "{}");
    }

    static List<Function<AuditEntry, Object>> getParameterFunctions(String logFormat)
    {
        List<Function<AuditEntry, Object>> parameterFunctions = new ArrayList<>();
        Matcher matcher = PARAMETER_PATTERN.matcher(logFormat);
        while (matcher.find())
        {
            String normalParameter = matcher.group(1);
            if (normalParameter != null)
            {
                Function<AuditEntry, Object> valueSupplier = AVAILABLE_PARAMETER_FUNCTIONS.computeIfAbsent(normalParameter, throwConfigurationException());
                parameterFunctions.add(valueSupplier);
            }
            else // Optional parameter
            {
                String descriptionLeft = matcher.group(2);
                String innerParameter = matcher.group(3);
                String descriptionRight = matcher.group(4);
                Function<AuditEntry, Object> valueSupplier = AVAILABLE_PARAMETER_FUNCTIONS.computeIfAbsent(innerParameter, throwConfigurationException());
                parameterFunctions.add(valueSupplier.andThen(getDescriptionIfValuePresent(descriptionLeft)));
                parameterFunctions.add(valueSupplier.andThen(Slf4jAuditLogger::getValueOrEmptyString));
                parameterFunctions.add(valueSupplier.andThen(getDescriptionIfValuePresent(descriptionRight)));
            }
        }
        return parameterFunctions;
    }

    private static Function<String, Function<AuditEntry, Object>> throwConfigurationException()
    {
        return key -> {
            throw new ConfigurationException("Unknown log format parameter: " + key);
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
        List<ToStringer> parameters = parameterFunctions.stream()
                                                        .map(valueSupplier -> new ToStringer<>(logEntry, valueSupplier))
                                                        .collect(toList());
        auditLogger.info(logTemplate, parameters.toArray());
    }
}
