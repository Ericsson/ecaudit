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
import java.net.InetSocketAddress;
import java.util.UUID;

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueBuilder;
import net.openhft.chronicle.queue.ExcerptAppender;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Util for creating chronicle test data used for verifying backwards compatibility.
 * Should be used when the chronicle data version ({@link WireTags#VALUE_VERSION_CURRENT}) is stepped.
 * <p>
 * Create the chronicle test data by updating the <b>version</b> and <b>data</b> below and run the main method,
 * then create a new unit test for that version (similar to {@link TestReadVersion1}).
 * <p>
 * Both the created resource files and the new test case should be checked into git.
 */
public class WriteTestDataUtil
{
    public static void main(String[] args) throws Exception
    {
        String version = "X"; // Set the version here!

        // Data
        AuditRecord record = SimpleAuditRecord.builder()
                                              .withClientAddress(new InetSocketAddress(InetAddress.getByName("0.1.2.3"), 777))
                                              .withCoordinatorAddress(InetAddress.getByName("4.5.6.7"))
                                              .withUser("bob")
                                              .withBatchId(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"))
                                              .withStatus(Status.SUCCEEDED)
                                              .withOperation(mockOperation("SELECT SOMETHING", "SELECT SOMETHING NAKED"))
                                              .withTimestamp(1554188832013L)
                                              .withSubject("bob-the-subject")
                                              .build();

        // Write Data to Queue
        ChronicleQueue chronicleQueue = ChronicleQueueBuilder
                                        .single(new File("common/src/test/resources/q" + version))
                                        .blockSize(1024)
                                        .build();
        ExcerptAppender appender = chronicleQueue.acquireAppender();

        appender.writeDocument(new AuditRecordWriteMarshallable(record, FieldSelector.DEFAULT_FIELDS));
        appender.writeDocument(new AuditRecordWriteMarshallable(record, FieldSelector.NO_FIELDS));
        appender.writeDocument(new AuditRecordWriteMarshallable(record, FieldSelector.ALL_FIELDS));
        appender.writeDocument(new AuditRecordWriteMarshallable(record, FieldSelector.fromFields(asList("USER", "OPERATION_NAKED", "STATUS")))); // Custom fields

        chronicleQueue.close();
    }

    private static AuditOperation mockOperation(String operation, String nakedOperation)
    {
        AuditOperation operationMock = mock(AuditOperation.class);
        when(operationMock.getOperationString()).thenReturn(operation);
        when(operationMock.getNakedOperationString()).thenReturn(nakedOperation);
        return operationMock;
    }
}
