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

import java.net.InetAddress;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueBuilder;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.WriteMarshallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestWriteReadVersionCurrent
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ChronicleQueue chronicleQueue;

    @Before
    public void before()
    {
        chronicleQueue = ChronicleQueueBuilder.single(temporaryFolder.getRoot()).blockSize(1024).build();
    }

    @After
    public void after()
    {
        chronicleQueue.close();
    }

    @Test
    public void writeReadBatch() throws Exception
    {
        AuditRecord expectedAuditRecord = SimpleAuditRecord
                                          .builder()
                                          .withBatchId(UUID.randomUUID())
                                          .withClientAddress(InetAddress.getByName("0.1.2.3"))
                                          .withStatus(Status.ATTEMPT)
                                          .withOperation(new SimpleAuditOperation("SELECT SOMETHING"))
                                          .withUser("bob")
                                          .withTimestamp(System.currentTimeMillis())
                                          .build();

        writeAuditRecordToChronicle(expectedAuditRecord);

        AuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getBatchId()).isEqualTo(expectedAuditRecord.getBatchId());
        assertThat(actualAuditRecord.getClientAddress()).isEqualTo(expectedAuditRecord.getClientAddress());
        assertThat(actualAuditRecord.getStatus()).isEqualTo(expectedAuditRecord.getStatus());
        assertThat(actualAuditRecord.getOperation().getOperationString()).isEqualTo(expectedAuditRecord.getOperation().getOperationString());
        assertThat(actualAuditRecord.getUser()).isEqualTo(expectedAuditRecord.getUser());
        assertThat(actualAuditRecord.getTimestamp()).isEqualTo(expectedAuditRecord.getTimestamp());
    }

    @Test
    public void writeReadSingle() throws Exception
    {
        AuditRecord expectedAuditRecord = SimpleAuditRecord
                                          .builder()
                                          .withClientAddress(InetAddress.getByName("0.1.2.3"))
                                          .withStatus(Status.FAILED)
                                          .withOperation(new SimpleAuditOperation("SELECT SOMETHING"))
                                          .withUser("bob")
                                          .withTimestamp(System.currentTimeMillis())
                                          .build();

        writeAuditRecordToChronicle(expectedAuditRecord);

        AuditRecord actualAuditRecord = readAuditRecordFromChronicle();

        assertThat(actualAuditRecord.getBatchId()).isEmpty();
        assertThat(actualAuditRecord.getClientAddress()).isEqualTo(expectedAuditRecord.getClientAddress());
        assertThat(actualAuditRecord.getStatus()).isEqualTo(expectedAuditRecord.getStatus());
        assertThat(actualAuditRecord.getOperation().getOperationString()).isEqualTo(expectedAuditRecord.getOperation().getOperationString());
        assertThat(actualAuditRecord.getUser()).isEqualTo(expectedAuditRecord.getUser());
        assertThat(actualAuditRecord.getTimestamp()).isEqualTo(expectedAuditRecord.getTimestamp());
    }

    @Test
    public void tryReuseOnRead() throws Exception
    {
        AuditRecord expectedAuditRecord = SimpleAuditRecord
                                          .builder()
                                          .withClientAddress(InetAddress.getByName("0.1.2.3"))
                                          .withStatus(Status.FAILED)
                                          .withOperation(new SimpleAuditOperation("SELECT SOMETHING"))
                                          .withUser("bob")
                                          .withTimestamp(System.currentTimeMillis())
                                          .build();

        writeAuditRecordToChronicle(expectedAuditRecord);
        writeAuditRecordToChronicle(expectedAuditRecord);

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        ExcerptTailer tailer = chronicleQueue.createTailer();
        tailer.readDocument(readMarshallable);

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> tailer.readDocument(readMarshallable))
        .withMessage("Tried to read from wire with used marshallable");
    }

    private void writeAuditRecordToChronicle(AuditRecord auditRecord)
    {
        WriteMarshallable writeMarshallable = new AuditRecordWriteMarshallable(auditRecord);

        ExcerptAppender appender = chronicleQueue.acquireAppender();
        appender.writeDocument(writeMarshallable);
    }

    private AuditRecord readAuditRecordFromChronicle()
    {
        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        ExcerptTailer tailer = chronicleQueue.createTailer();
        tailer.readDocument(readMarshallable);

        return readMarshallable.getAuditRecord();
    }
}
