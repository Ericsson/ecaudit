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

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditRecord;
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
        readBatch();
        readSingle();
    }

    private void readBatch() throws Exception
    {
        AuditRecord expectedAuditRecord = SimpleAuditRecord
                                          .builder()
                                          .withBatchId(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"))
                                          .withClientAddress(InetAddress.getByName("0.1.2.3"))
                                          .withCoordinatorAddress(InetAddress.getByName("4.5.6.7"))
                                          .withStatus(Status.ATTEMPT)
                                          .withOperation(new SimpleAuditOperation("SELECT SOMETHING"))
                                          .withUser("bob")
                                          .withTimestamp(1554188832013L)
                                          .build();

        AuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getBatchId()).isEqualTo(expectedAuditRecord.getBatchId());
        assertThat(actualAuditRecord.getClientAddress()).isEqualTo(expectedAuditRecord.getClientAddress());
        assertThat(actualAuditRecord.getStatus()).isEqualTo(expectedAuditRecord.getStatus());
        assertThat(actualAuditRecord.getOperation().getOperationString()).isEqualTo(expectedAuditRecord.getOperation().getOperationString());
        assertThat(actualAuditRecord.getUser()).isEqualTo(expectedAuditRecord.getUser());
        assertThat(actualAuditRecord.getTimestamp()).isEqualTo(expectedAuditRecord.getTimestamp());
    }

    private void readSingle() throws Exception
    {
        AuditRecord expectedAuditRecord = SimpleAuditRecord
                                          .builder()
                                          .withClientAddress(InetAddress.getByName("0.1.2.3"))
                                          .withCoordinatorAddress(InetAddress.getByName("4.5.6.7"))
                                          .withStatus(Status.FAILED)
                                          .withOperation(new SimpleAuditOperation("SELECT SOMETHING"))
                                          .withUser("bob")
                                          .withTimestamp(1554188832323L)
                                          .build();

        AuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getBatchId()).isEmpty();
        assertThat(actualAuditRecord.getClientAddress()).isEqualTo(expectedAuditRecord.getClientAddress());
        assertThat(actualAuditRecord.getStatus()).isEqualTo(expectedAuditRecord.getStatus());
        assertThat(actualAuditRecord.getOperation().getOperationString()).isEqualTo(expectedAuditRecord.getOperation().getOperationString());
        assertThat(actualAuditRecord.getUser()).isEqualTo(expectedAuditRecord.getUser());
        assertThat(actualAuditRecord.getTimestamp()).isEqualTo(expectedAuditRecord.getTimestamp());
    }

    private AuditRecord readAuditRecordFromChronicle()
    {
        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        tailer.readDocument(readMarshallable);

        return readMarshallable.getAuditRecord();
    }
}
