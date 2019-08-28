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

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.AuditRecordReadMarshallable;
import com.ericsson.bss.cassandra.ecaudit.common.record.StoredAuditRecord;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueBuilder;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

/**
 * Read AuditRecord entries from a Chronicle queue.
 *
 * The Chronicle queue is opened and scanned as defined by the supplied ToolOptions.
 */
public class QueueReader
{
    private final ExcerptTailer tailer;

    private StoredAuditRecord nextRecord;

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
            System.err.println(e.getMessage()); // NOPMD
            System.exit(2); // NOPMD
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
        return nextRecord != null;
    }

    private void maybeReadNext()
    {
        if (nextRecord == null)
        {
            readNext();
        }
    }

    private void readNext()
    {
        AuditRecordReadMarshallable recordMarshallable = new AuditRecordReadMarshallable();
        if (tailer.readDocument(recordMarshallable))
        {
            nextRecord = recordMarshallable.getAuditRecord();
        }
    }

    public StoredAuditRecord nextRecord()
    {
        maybeReadNext();
        StoredAuditRecord entry = nextRecord;
        nextRecord = null; // NOPMD
        return entry;
    }
}
