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

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import net.openhft.chronicle.wire.WireOut;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.jetbrains.annotations.NotNull;

public class ChronicleAuditLogger implements AuditLogger
{
    private static final Logger LOG = LoggerFactory.getLogger(ChronicleAuditLogger.class);

    private static final String WIRE_KEY_VERSION = "version";
    private static final String WIRE_KEY_TYPE = "type";
    private static final String WIRE_KEY_TIMESTAMP = "timestamp";
    private static final String WIRE_KEY_CLIENT = "client";
    private static final String WIRE_KEY_USER = "user";
    private static final String WIRE_KEY_BATCH_ID = "batchId";
    private static final String WIRE_KEY_STATUS = "status";
    private static final String WIRE_KEY_OPERATION = "operation";

    private static final short WIRE_VALUE_CURRENT_VERSION = 800;
    private static final String WIRE_VALUE_BATCH_ENTRY = "batch-entry";
    private static final String WIRE_VALUE_SINGLE_ENTRY = "single-entry";

    private final ChronicleWriter writer;

    public ChronicleAuditLogger(Map<String, String> parameters)
    {
        ChronicleAuditLoggerConfig config = new ChronicleAuditLoggerConfig(parameters);
        writer = new ChronicleWriter(config);
    }

    @VisibleForTesting
    ChronicleAuditLogger(ChronicleWriter writer)
    {
        this.writer = writer;
    }

    @Override
    public void log(AuditEntry logEntry)
    {
        MarshallableAuditEntry marshallableAuditEntry = new MarshallableAuditEntry(logEntry);
        try
        {
            writer.put(marshallableAuditEntry);
        }
        catch (InterruptedException e)
        {
            LOG.warn("Interrupted while sending message to Chronicle writer");
            Thread.currentThread().interrupt();
        }
    }

    private class MarshallableAuditEntry implements WriteMarshallable
    {
        private final AuditEntry auditEntry;

        private MarshallableAuditEntry(AuditEntry auditEntry)
        {
            this.auditEntry = auditEntry;
        }

        @Override
        public void writeMarshallable(@NotNull WireOut wire)
        {
            wire.write(WIRE_KEY_VERSION).int16(WIRE_VALUE_CURRENT_VERSION);
            if (auditEntry.getBatchId().isPresent())
            {
                wire.write(WIRE_KEY_TYPE).text(WIRE_VALUE_BATCH_ENTRY);
            }
            else
            {
                wire.write(WIRE_KEY_TYPE).text(WIRE_VALUE_SINGLE_ENTRY);
            }
            wire.write(WIRE_KEY_TIMESTAMP).int64(auditEntry.getTimestamp());
            wire.write(WIRE_KEY_CLIENT).bytes(auditEntry.getClientAddress().getAddress());
            wire.write(WIRE_KEY_USER).text(auditEntry.getUser());
            if (auditEntry.getBatchId().isPresent())
            {
                wire.write(WIRE_KEY_BATCH_ID).uuid(auditEntry.getBatchId().get());
            }
            wire.write(WIRE_KEY_STATUS).text(auditEntry.getStatus().name());
            wire.write(WIRE_KEY_OPERATION).text(auditEntry.getOperation().getOperationString());
        }
    }
}
