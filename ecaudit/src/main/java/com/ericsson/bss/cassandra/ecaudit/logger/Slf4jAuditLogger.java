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
import java.util.Map;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.common.formatter.LogMessageFormatter;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import org.apache.cassandra.exceptions.ConfigurationException;

/**
 * Implements an {@link AuditLogger} that writes {@link AuditEntry} instance into file using {@link Logger}.
 * <br>
 * It is possible to configure a parameterized log message by providing a formatting string {@link Slf4jAuditLoggerConfig#getLogFormat()}.
 */
public class Slf4jAuditLogger implements AuditLogger
{
    public static final String AUDIT_LOGGER_NAME = "ECAUDIT";

    private final Logger auditLogger; // NOPMD
    private final LogMessageFormatter<AuditEntry> formatter;

    /**
     * Constructor, injects logger from {@link LoggerFactory}.
     *
     * @param parameters the custom strategy parameters
     */
    public Slf4jAuditLogger(Map<String, String> parameters)
    {
        this(new Slf4jAuditLoggerConfig(parameters), LoggerFactory.getLogger(AUDIT_LOGGER_NAME));
    }

    @VisibleForTesting
    public Slf4jAuditLogger(Map<String, String> parameters, String loggerName)
    {
        this(new Slf4jAuditLoggerConfig(parameters), LoggerFactory.getLogger(loggerName));
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
        formatter = createLogMessageFormatter(auditConfig);
    }

    private LogMessageFormatter<AuditEntry> createLogMessageFormatter(Slf4jAuditLoggerConfig auditConfig)
    {
        try
        {
            return LogMessageFormatter.<AuditEntry>builder()
                   .format(auditConfig.getLogFormat())
                   .anchor("{}")
                   .escape("\\{\\}", "\\\\{}")
                   .availableFields(getAvailableFieldFunctionMap(auditConfig))
                   .build();
        }
        catch (IllegalArgumentException e)
        {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    static Map<String, Function<AuditEntry, Object>> getAvailableFieldFunctionMap(Slf4jAuditLoggerConfig auditConfig)
    {
        return ImmutableMap.<String, Function<AuditEntry, Object>>builder()
               .put("CLIENT_IP", entry -> entry.getClientAddress().getAddress().getHostAddress())
               .put("CLIENT_PORT", entry -> getPortOrNull(entry.getClientAddress()))
               .put("COORDINATOR_IP", entry -> entry.getCoordinatorAddress().getHostAddress())
               .put("USER", entry -> sanitize(entry.getUser(), auditConfig))
               .put("BATCH_ID", entry -> entry.getBatchId().orElse(null))
               .put("STATUS", AuditEntry::getStatus)
               .put("OPERATION", entry -> sanitize(entry.getOperation().getOperationString(), auditConfig))
               .put("OPERATION_NAKED", entry -> sanitize(entry.getOperation().getNakedOperationString(), auditConfig))
               .put("TIMESTAMP", getTimeFunction(auditConfig))
               .put("SUBJECT", entry -> entry.getSubject().map(s -> sanitize(s, auditConfig)).orElse(null))
               .build();
    }

    private static String sanitize(String input, Slf4jAuditLoggerConfig auditConfig)
    {
        String sanitized = input;
        for (String escapeCharacter : auditConfig.getEscapeCharacters())
        {
            sanitized = sanitized.replaceAll(escapeCharacter, "\\\\" + escapeCharacter);
        }
        return sanitized;
    }

    private static Integer getPortOrNull(InetSocketAddress address)
    {
        return address.getPort() == AuditEntry.UNKNOWN_PORT ? null : address.getPort();
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

    @Override
    public void log(AuditEntry logEntry)
    {
        auditLogger.info(formatter.getLogTemplate(), formatter.getArgumentsForEntry(logEntry));
    }
}
