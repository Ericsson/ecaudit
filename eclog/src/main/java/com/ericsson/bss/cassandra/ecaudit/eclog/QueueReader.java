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
package com.ericsson.bss.cassandra.ecaudit.eclog;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueBuilder;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.ReadMarshallable;
import net.openhft.chronicle.wire.WireIn;
import org.jetbrains.annotations.NotNull;

/**
 * Read AuditRecord entries from a Chronicle queue.
 *
 * The Chronicle queue is opened and scanned as defined by the supplied ToolOptions.
 */
public class QueueReader
{
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

    private final ExcerptTailer tailer;

    private MarshallableAuditEntry nextEntry;

    public QueueReader(ToolOptions toolOptions)
    {
        this(toolOptions, getChronicleQueue(toolOptions));
    }

    // Visible for testing
    QueueReader(ToolOptions toolOptions, ChronicleQueue chronicleQueue)
    {
        tailer = getExcerptTailer(toolOptions, chronicleQueue);
    }

    private static ChronicleQueue getChronicleQueue(ToolOptions toolOptions)
    {
        SingleChronicleQueueBuilder chronicleBuilder = ChronicleQueueBuilder.single(toolOptions.path().toFile())
                                                                            .readOnly(true);
        toolOptions.rollCycle().ifPresent(chronicleBuilder::rollCycle);

        try
        {
            return chronicleBuilder.build();
        }
        catch (IllegalArgumentException e)
        {
            System.err.println(e.getMessage());
            System.exit(2);
            return null;
        }
    }

    private static ExcerptTailer getExcerptTailer(ToolOptions toolOptions, ChronicleQueue chronicle)
    {
        ExcerptTailer tempTailer = chronicle.createTailer();

        if (toolOptions.tail().isPresent())
        {
            long startIndex = tempTailer.index();

            tempTailer = tempTailer.toEnd();

            long newIndex = (tempTailer.index() - toolOptions.tail().get());
            newIndex = Math.max(newIndex, startIndex);

            tempTailer.moveToIndex(newIndex);
        }

        return tempTailer;
    }

    public boolean hasRecordAvailable()
    {
        maybeReadNext();
        return nextEntry != null;
    }

    private void maybeReadNext()
    {
        if (nextEntry == null)
        {
            readNext();
        }
    }

    private void readNext()
    {
        MarshallableAuditEntry auditEntry = new MarshallableAuditEntry();
        if (tailer.readDocument(auditEntry))
        {
            nextEntry = auditEntry;
        }
    }

    public AuditRecord nextRecord()
    {
        maybeReadNext();
        AuditRecord entry = nextEntry;
        nextEntry = null;
        return entry;
    }

    static class MarshallableAuditEntry implements AuditRecord, ReadMarshallable
    {
        private byte[] client;
        private String user;
        private UUID batchId;
        private String status;
        private String operation;
        private long timestamp;

        @Override
        public void readMarshallable(@NotNull WireIn wire) throws IORuntimeException
        {
            short version = wire.read(WIRE_KEY_VERSION).int16();

            if (version != WIRE_VALUE_CURRENT_VERSION)
            {
                throw new IllegalArgumentException("Unsupported record version: " + version);
            }

            String type = wire.read(WIRE_KEY_TYPE).text();

            timestamp = wire.read(WIRE_KEY_TIMESTAMP).int64();
            client = wire.read(WIRE_KEY_CLIENT).bytes();
            user = wire.read(WIRE_KEY_USER).text();
            if (WIRE_VALUE_BATCH_ENTRY.equals(type))
            {
                batchId = wire.read(WIRE_KEY_BATCH_ID).uuid();
            }
            status = wire.read(WIRE_KEY_STATUS).text();
            operation = wire.read(WIRE_KEY_OPERATION).text();
        }

        @Override
        public long getTimestamp()
        {
            return timestamp;
        }

        @Override
        public InetAddress getClient()
        {
            try
            {
                return InetAddress.getByAddress(client);
            }
            catch (UnknownHostException e)
            {
                return InetAddress.getLoopbackAddress();
            }
        }

        @Override
        public String getUser()
        {
            return user;
        }

        @Override
        public Optional<UUID> getBatchId()
        {
            return Optional.ofNullable(batchId);
        }

        @Override
        public String getStatus()
        {
            return status;
        }

        @Override
        public String getOperation()
        {
            return operation;
        }
    }
}
