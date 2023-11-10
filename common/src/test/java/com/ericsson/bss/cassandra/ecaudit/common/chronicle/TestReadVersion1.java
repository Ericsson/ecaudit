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
import com.ericsson.bss.cassandra.ecaudit.common.record.StoredAuditRecord;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test reading records stored in the binary log file produced by {@link WriteTestDataUtil} using entry version 1 with the following data:
 * <ul>
 *   <li> CLIENT_IP = 0.1.2.3
 *   <li> CLIENT_PORT = 777
 *   <li> COORDINATOR_IP = 4.5.6.7
 *   <li> USER = "bob"
 *   <li> BATCH_ID = bd92aeb1-3373-4d6a-b65a-0d60295f66c9
 *   <li> STATUS = SUCCEEDED
 *   <li> OPERATION = "SELECT SOMETHING"
 *   <li> OPERATION_NAKED = "SELECT SOMETHING NAKED"
 *   <li> TIMESTAMP = 1554188832013L
 * </ul>
 * <p>
 * Is written in 4 records with different fields selected:
 * <ul>
 *   <li> record1 - Default fields selected (TIMESTAMP, CLIENT_IP, CLIENT_PORT, COORDINATOR_IP, USER, BATCH_ID, STATUS, OPERATION)
 *   <li> record2 - No fields selected
 *   <li> record3 - All fields selected (TIMESTAMP, CLIENT_IP, CLIENT_PORT, COORDINATOR_IP, USER, BATCH_ID, STATUS, OPERATION, OPERATION_NAKED)
 *   <li> record4 - Custom fields selected (USER, STATUS, OPERATION_NAKED)
 * </ul>
 */
public class TestReadVersion1
{
    private static ChronicleQueue chronicleQueue;
    private static ExcerptTailer tailer;

    @BeforeClass
    public static void beforeClass()
    {
        File queueDirVersion1 = new File("src/test/resources/q1");
        chronicleQueue = SingleChronicleQueueBuilder
                         .single(queueDirVersion1)
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
        readDefault();
        readEmpty();
        readFull();
        readCustom();
    }

    private void readDefault() throws Exception
    {
        StoredAuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getClientAddress()).contains(InetAddress.getByName("0.1.2.3"));
        assertThat(actualAuditRecord.getClientPort()).contains(777);
        assertThat(actualAuditRecord.getCoordinatorAddress()).contains(InetAddress.getByName("4.5.6.7"));
        assertThat(actualAuditRecord.getUser()).contains("bob");
        assertThat(actualAuditRecord.getBatchId()).contains(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"));
        assertThat(actualAuditRecord.getStatus()).contains(Status.SUCCEEDED);
        assertThat(actualAuditRecord.getOperation()).contains("SELECT SOMETHING");
        assertThat(actualAuditRecord.getNakedOperation()).isEmpty();
        assertThat(actualAuditRecord.getTimestamp()).contains(1554188832013L);
    }

    private void readEmpty()
    {
        StoredAuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getClientAddress()).isEmpty();
        assertThat(actualAuditRecord.getClientPort()).isEmpty();
        assertThat(actualAuditRecord.getCoordinatorAddress()).isEmpty();
        assertThat(actualAuditRecord.getUser()).isEmpty();
        assertThat(actualAuditRecord.getBatchId()).isEmpty();
        assertThat(actualAuditRecord.getStatus()).isEmpty();
        assertThat(actualAuditRecord.getOperation()).isEmpty();
        assertThat(actualAuditRecord.getNakedOperation()).isEmpty();
        assertThat(actualAuditRecord.getTimestamp()).isEmpty();
    }

    private void readFull() throws Exception
    {
        StoredAuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getClientAddress()).contains(InetAddress.getByName("0.1.2.3"));
        assertThat(actualAuditRecord.getClientPort()).contains(777);
        assertThat(actualAuditRecord.getCoordinatorAddress()).contains(InetAddress.getByName("4.5.6.7"));
        assertThat(actualAuditRecord.getUser()).contains("bob");
        assertThat(actualAuditRecord.getBatchId()).contains(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"));
        assertThat(actualAuditRecord.getStatus()).contains(Status.SUCCEEDED);
        assertThat(actualAuditRecord.getOperation()).contains("SELECT SOMETHING");
        assertThat(actualAuditRecord.getNakedOperation()).contains("SELECT SOMETHING NAKED");
        assertThat(actualAuditRecord.getTimestamp()).contains(1554188832013L);
    }

    private void readCustom()
    {
        StoredAuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getClientAddress()).isEmpty();
        assertThat(actualAuditRecord.getClientPort()).isEmpty();
        assertThat(actualAuditRecord.getCoordinatorAddress()).isEmpty();
        assertThat(actualAuditRecord.getUser()).contains("bob");
        assertThat(actualAuditRecord.getBatchId()).isEmpty();
        assertThat(actualAuditRecord.getStatus()).contains(Status.SUCCEEDED);
        assertThat(actualAuditRecord.getOperation()).isEmpty();
        assertThat(actualAuditRecord.getNakedOperation()).contains("SELECT SOMETHING NAKED");
        assertThat(actualAuditRecord.getTimestamp()).isEmpty();
    }

    private StoredAuditRecord readAuditRecordFromChronicle()
    {
        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        tailer.readDocument(readMarshallable);

        return readMarshallable.getAuditRecord();
    }
}
