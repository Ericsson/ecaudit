/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
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

import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.AuditRecordWriteMarshallable;
import com.ericsson.bss.cassandra.ecaudit.common.chronicle.FieldSelector;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;

public class ChronicleAuditLogger implements AuditLogger
{
    private static final Logger LOG = LoggerFactory.getLogger(ChronicleAuditLogger.class);

    private final ChronicleWriter writer;
    private final FieldSelector configuredFields;

    public ChronicleAuditLogger(Map<String, String> parameters)
    {
        ChronicleAuditLoggerConfig config = new ChronicleAuditLoggerConfig(parameters);
        writer = new ChronicleWriter(config);
        configuredFields = config.getFields();
    }

    @VisibleForTesting
    ChronicleAuditLogger(ChronicleWriter writer, FieldSelector configuredFields)
    {
        this.writer = writer;
        this.configuredFields = configuredFields;
    }

    @Override
    public void log(AuditEntry logEntry)
    {
        AuditRecordWriteMarshallable auditRecordWriteMarshallable = new AuditRecordWriteMarshallable(logEntry, configuredFields);
        try
        {
            writer.put(auditRecordWriteMarshallable);
        }
        catch (InterruptedException e)
        {
            LOG.warn("Interrupted while sending message to Chronicle writer");
            Thread.currentThread().interrupt();
        }
    }
}
