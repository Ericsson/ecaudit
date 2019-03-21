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

import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestLogPrinter
{
    @Mock
    private PrintStream stream;

    @After
    public void after()
    {
        verifyNoMoreInteractions(stream);
    }

    @Test(timeout = 5000)
    public void testFiveRecords() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().build();
        LogPrinter printer = givenPrinter(options);
        QueueReader reader = givenRecords(false, 5, 0);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFiveBatchRecords() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().build();
        LogPrinter printer = givenPrinter(options);
        QueueReader reader = givenRecords(true, 5, 0);

        printer.print(reader);

        verifyBatchRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFivePlusFiveWithoutFollowWillSkipFiveLastRecords() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().build();
        LogPrinter printer = givenPrinter(options);
        QueueReader reader = givenRecords(false, 5, 5);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFivePlusTenWithFollowAndLimitRecords() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().withFollow(true).withLimit(10).build();
        LogPrinter printer = givenPrinter(options);
        QueueReader reader = givenRecords(false, 5, 10);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(10);
    }

    private LogPrinter givenPrinter(ToolOptions options)
    {
        return new LogPrinter(options, stream, 10);
    }

    private QueueReader givenRecords(boolean withBatchId, int count1, int count2) throws UnknownHostException
    {
        QueueReader reader = mock(QueueReader.class);
        OngoingStubbing<Boolean> hasNextStub = when(reader.hasRecordAvailable());
        for (int i = 0; i < count1; i++)
        {
            hasNextStub = hasNextStub.thenReturn(true);
        }
        hasNextStub.thenReturn(false);
        for (int i = 0; i < count2; i++)
        {
            hasNextStub = hasNextStub.thenReturn(true);
        }
        hasNextStub.thenReturn(false);

        List<AuditRecord> records = new ArrayList<>();
        for (int i = 0; i < count1 + count2; i++)
        {
            records.add(mockRecord(i, "1.2.3.4", "king", "Attempt", withBatchId ? UUID.fromString("b23534c7-93af-497f-b00c-1edaaa335caa") : null, "SELECT QUERY"));
        }
        OngoingStubbing<AuditRecord> recordStub = when(reader.nextRecord());
        for (AuditRecord record : records)
        {
            recordStub = recordStub.thenReturn(record);
        }

        return reader;
    }

    private AuditRecord mockRecord(long timestamp, String clientHost, String user, String status, UUID batchId, String operation) throws UnknownHostException
    {
        AuditRecord record = mock(AuditRecord.class);
        when(record.getTimestamp()).thenReturn(timestamp);
        when(record.getClient()).thenReturn(InetAddress.getByName(clientHost));
        when(record.getUser()).thenReturn(user);
        when(record.getBatchId()).thenReturn(Optional.ofNullable(batchId));
        when(record.getStatus()).thenReturn(status);
        when(record.getOperation()).thenReturn(operation);
        return record;
    }

    private void verifySingleRecordsFromStartOfSequence(int count)
    {
        for (int i = 0; i < count; i++)
        {
            verify(stream).println(eq(i + "|1.2.3.4|king|Attempt|SELECT QUERY"));
        }
    }

    private void verifyBatchRecordsFromStartOfSequence(int count)
    {
        for (int i = 0; i < count; i++)
        {
            verify(stream).println(eq(i + "|1.2.3.4|king|Attempt|b23534c7-93af-497f-b00c-1edaaa335caa|SELECT QUERY"));
        }
    }
}
