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
package com.ericsson.bss.cassandra.ecaudit.common.chronicle;

import java.io.File;
import java.net.InetAddress;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueBuilder;
import net.openhft.chronicle.queue.ExcerptTailer;

import static org.assertj.core.api.Assertions.assertThat;

public class TestReadVersion0
{
    private static ChronicleQueue chronicleQueue;
    private static ExcerptTailer tailer;

    @BeforeClass
    public static void beforeClass()
    {
        File queueDirVersion0 = new File("src/test/resources/q0");
        chronicleQueue = ChronicleQueueBuilder
                         .single(queueDirVersion0)
                         .blockSize(1024)
                         .readOnly(true)
                         .build();
        tailer = chronicleQueue.createTailer();
    }

    @AfterClass
    public static void afterClass()
    {
        chronicleQueue.close();
    }

    @Test
    public void test() throws Exception
    {
        readSingle();
        readBatch();
    }

    private void readSingle() throws Exception
    {
        StoredAuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getBatchId()).isEmpty();
        assertThat(actualAuditRecord.getClientAddress()).contains(InetAddress.getByName("0.1.2.3"));
        assertThat(actualAuditRecord.getClientPort()).contains(777);
        assertThat(actualAuditRecord.getStatus()).contains(Status.FAILED);
        assertThat(actualAuditRecord.getOperation()).contains("SELECT SOMETHING");
        assertThat(actualAuditRecord.getUser()).contains("bob");
        assertThat(actualAuditRecord.getTimestamp()).contains(1554188832323L);
    }

    private void readBatch() throws Exception
    {
        StoredAuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getBatchId()).contains(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"));
        assertThat(actualAuditRecord.getClientAddress()).contains(InetAddress.getByName("0.1.2.3"));
        assertThat(actualAuditRecord.getClientPort()).contains(777);
        assertThat(actualAuditRecord.getStatus()).contains(Status.ATTEMPT);
        assertThat(actualAuditRecord.getOperation()).contains("SELECT SOMETHING");
        assertThat(actualAuditRecord.getUser()).contains("bob");
        assertThat(actualAuditRecord.getTimestamp()).contains(1554188832013L);
    }

    private StoredAuditRecord readAuditRecordFromChronicle()
    {
        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        tailer.readDocument(readMarshallable);

        return readMarshallable.getAuditRecord();
    }
}
