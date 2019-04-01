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

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;

import static org.assertj.core.api.Assertions.assertThat;
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
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(false, 5, 0);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFiveBatchRecords() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(true, 5, 0);

        printer.print(reader);

        verifyBatchRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFivePlusFiveWithoutFollowWillSkipFiveLastRecords() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(false, 5, 5);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFivePlusTenWithFollowAndLimitRecords() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().withFollow(true).withLimit(10).build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(false, 5, 10);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(10);
    }

    @Test(timeout = 5000)
    public void testFivePlusFiveWithFollowHasPollDelay() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().withFollow(true).withLimit(10).build();
        LogPrinter printer = givenPrinter(options, 200);
        QueueReader reader = givenRecords(false, 5, 5);

        long start = System.currentTimeMillis();
        printer.print(reader);
        long end = System.currentTimeMillis();

        verifySingleRecordsFromStartOfSequence(10);
        
        assertThat(end).isGreaterThanOrEqualTo(start + 200L);
    }

    @Test(timeout = 5000)
    public void testFivePlusTenWithFollowAndLimitToZeroRecords() throws UnknownHostException
    {
        ToolOptions options = ToolOptions.builder().withFollow(true).withLimit(0).build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(false, 5, 10);

        printer.print(reader);
    }

    private LogPrinter givenPrinter(ToolOptions options, long pollIntervallMs)
    {
        return new LogPrinter(options, stream, pollIntervallMs);
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
            records.add(mockRecord(i, "1.2.3.4", "king", Status.ATTEMPT, withBatchId ? UUID.fromString("b23534c7-93af-497f-b00c-1edaaa335caa") : null, "SELECT QUERY"));
        }
        OngoingStubbing<AuditRecord> recordStub = when(reader.nextRecord());
        for (AuditRecord record : records)
        {
            recordStub = recordStub.thenReturn(record);
        }

        return reader;
    }

    private AuditRecord mockRecord(long timestamp, String clientHost, String user, Status status, UUID batchId, String operation) throws UnknownHostException
    {
        AuditRecord record = mock(AuditRecord.class);
        when(record.getTimestamp()).thenReturn(timestamp);
        when(record.getClientAddress()).thenReturn(InetAddress.getByName(clientHost));
        when(record.getUser()).thenReturn(user);
        when(record.getBatchId()).thenReturn(Optional.ofNullable(batchId));
        when(record.getStatus()).thenReturn(status);
        when(record.getOperation()).thenReturn(new SimpleAuditOperation(operation));
        return record;
    }

    private void verifySingleRecordsFromStartOfSequence(int count)
    {
        for (int i = 0; i < count; i++)
        {
            verify(stream).println(eq(i + "|1.2.3.4|king|ATTEMPT|SELECT QUERY"));
        }
    }

    private void verifyBatchRecordsFromStartOfSequence(int count)
    {
        for (int i = 0; i < count; i++)
        {
            verify(stream).println(eq(i + "|1.2.3.4|king|ATTEMPT|b23534c7-93af-497f-b00c-1edaaa335caa|SELECT QUERY"));
        }
    }
}
