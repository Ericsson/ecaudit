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

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueBuilder;
import net.openhft.chronicle.queue.ExcerptAppender;

/**
 * Util for creating chronicle test data used for verifying backwards compatibility.
 * Should be used when the chronicle data version ({@link WireTags#VALUE_VERSION_CURRENT}) is stepped.
 * <p>
 * Create the chronicle test data by updating the <b>version</b> and <b>data</b> below and run the main method,
 * then create a new unit test for that version (similar to {@link TestReadVersion0}).
 * <p>
 * Both the created resource files and the new test case should be checked into git.
 */
public class WriteTestDataUtil
{
    public static void main(String[] args) throws Exception
    {
        String version = "X"; // Set the version here!

        // Data
        AuditRecord singleRecord = SimpleAuditRecord.builder()
                                                    .withClientAddress(InetAddress.getByName("0.1.2.3"))
                                                    .withCoordinatorAddress(InetAddress.getByName("4.5.6.7"))
                                                    .withStatus(Status.FAILED)
                                                    .withOperation(new SimpleAuditOperation("SELECT SOMETHING"))
                                                    .withUser("bob")
                                                    .withTimestamp(1554188832323L)
                                                    .build();

        AuditRecord batchRecord = SimpleAuditRecord.builder()
                                                   .withBatchId(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"))
                                                   .withClientAddress(InetAddress.getByName("0.1.2.3"))
                                                   .withCoordinatorAddress(InetAddress.getByName("4.5.6.7"))
                                                   .withStatus(Status.ATTEMPT)
                                                   .withOperation(new SimpleAuditOperation("SELECT SOMETHING"))
                                                   .withUser("bob")
                                                   .withTimestamp(1554188832013L)
                                                   .build();

        // Write Data to Queue
        ChronicleQueue chronicleQueue = ChronicleQueueBuilder
                                        .single(new File("common/src/test/resources/q" + version))
                                        .blockSize(1024)
                                        .build();
        ExcerptAppender appender = chronicleQueue.acquireAppender();

        appender.writeDocument(new AuditRecordWriteMarshallable(singleRecord));
        appender.writeDocument(new AuditRecordWriteMarshallable(batchRecord));

        chronicleQueue.close();
    }
}
