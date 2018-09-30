//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;

/**
 * Implements an {@link AuditLogger} that writes {@link AuditEntry} instance into file using {@link Logger}.
 */
public class Slf4jAuditLogger implements AuditLogger
{
    public static final Logger LOG = LoggerFactory.getLogger(Slf4jAuditLogger.class);

    public static final String AUDIT_LOGGER_NAME = "ECAUDIT";
    private final Logger auditLogger; // NOSONAR

    /**
     * Default constructor, injects logger from {@link LoggerFactory}.
     */
    public Slf4jAuditLogger()
    {
        this(LoggerFactory.getLogger(AUDIT_LOGGER_NAME));
    }

    /**
     * Test constructor.
     *
     * @param logger the logger backend to use for audit logs
     */
    Slf4jAuditLogger(Logger logger)
    {
        auditLogger = logger;
    }

    @Override
    public void log(AuditEntry logEntry)
    {
        auditLogger.info(getLogString(logEntry));
    }

    public static String getLogString(AuditEntry logEntry)
    {
        StringBuilder builder = new StringBuilder();

        builder.append("client:'").append(logEntry.getClientAddress().getHostAddress());
        builder.append("'|user:'").append(logEntry.getUser());
        if (logEntry.getBatchId().isPresent())
        {
            builder.append("'|batchId:'").append(logEntry.getBatchId().get());
        }
        builder.append("'|status:'").append(logEntry.getStatus());
        builder.append("'|operation:'").append(logEntry.getOperation().getOperationString()).append("'");

        return builder.toString();
    }
}
