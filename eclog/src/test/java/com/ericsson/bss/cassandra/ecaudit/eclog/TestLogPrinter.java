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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.common.record.StoredAuditRecord;
import com.ericsson.bss.cassandra.ecaudit.eclog.config.EcLogYamlConfig;
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
    private static final Path DEFAULT_PATH = Paths.get(".");
    private static final StoredAuditRecord FULL_RECORD = mockRecord(123L, "1.2.3.4", 42, "5.6.7.8", "king", Status.ATTEMPT, UUID.fromString("12345678-aaaa-bbbb-cccc-123456789abc"), "select something");
    private static final String ALL_FIELDS_OPTIONAL = "{?timestamp:${TIMESTAMP}?}{?|client:${CLIENT_IP}?}{?:${CLIENT_PORT}?}{?|coordinator:${COORDINATOR_IP}?}{?|user:${USER}?}{?|batchId:${BATCH_ID}?}{?|status:${STATUS}?}{?|operation:'${OPERATION}'?}{?|operation-naked:'${OPERATION_NAKED}'?}";

    @Mock
    private PrintStream stream;

    @After
    public void after()
    {
        verifyNoMoreInteractions(stream);
    }

    @Test(timeout = 5000)
    public void testAuthRecord()
    {
        ToolOptions options = ToolOptions.builder().withPath(DEFAULT_PATH).build();
        LogPrinter printer = givenPrinter(options, 10);
        StoredAuditRecord authRecord = mockRecord(0L, "1.2.3.4", null, "5.6.7.8", "king", Status.ATTEMPT, null, "Authentication operation");
        QueueReader reader = givenReaderWithSingleRecord(authRecord);

        printer.print(reader);

        verify(stream).println(eq("0|1.2.3.4|5.6.7.8|king|ATTEMPT|Authentication operation"));
    }

    @Test(timeout = 5000)
    public void testFiveRecords()
    {
        ToolOptions options = ToolOptions.builder().withPath(DEFAULT_PATH).build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(false, 5, 0);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFiveBatchRecords()
    {
        ToolOptions options = ToolOptions.builder().withPath(DEFAULT_PATH).build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(true, 5, 0);

        printer.print(reader);

        verifyBatchRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFivePlusFiveWithoutFollowWillSkipFiveLastRecords()
    {
        ToolOptions options = ToolOptions.builder().withPath(DEFAULT_PATH).build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(false, 5, 5);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(5);
    }

    @Test(timeout = 5000)
    public void testFivePlusTenWithFollowAndLimitRecords()
    {
        ToolOptions options = ToolOptions.builder().withPath(DEFAULT_PATH).withFollow(true).withLimit(10).build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(false, 5, 10);

        printer.print(reader);

        verifySingleRecordsFromStartOfSequence(10);
    }

    @Test(timeout = 5000)
    public void testFivePlusFiveWithFollowHasPollDelay()
    {
        ToolOptions options = ToolOptions.builder().withPath(DEFAULT_PATH).withFollow(true).withLimit(10).build();
        LogPrinter printer = givenPrinter(options, 200);
        QueueReader reader = givenRecords(false, 5, 5);

        long start = System.currentTimeMillis();
        printer.print(reader);
        long end = System.currentTimeMillis();

        verifySingleRecordsFromStartOfSequence(10);

        assertThat(end).isGreaterThanOrEqualTo(start + 200L);
    }

    @Test(timeout = 5000)
    public void testFivePlusTenWithFollowAndLimitToZeroRecords()
    {
        ToolOptions options = ToolOptions.builder().withPath(DEFAULT_PATH).withFollow(true).withLimit(0).build();
        LogPrinter printer = givenPrinter(options, 10);
        QueueReader reader = givenRecords(false, 5, 10);

        printer.print(reader);
    }

    @Test(timeout = 5000)
    public void testEmptyRecord()
    {
        ToolOptions options = ToolOptions.builder().withPath(DEFAULT_PATH).build();
        LogPrinter printer = givenPrinter(options, 10);
        StoredAuditRecord emptyRecord = mock(StoredAuditRecord.class);
        QueueReader reader = givenReaderWithSingleRecord(emptyRecord);

        printer.print(reader);

        verify(stream).println(eq(""));
    }

    @Test(timeout = 5000)
    public void testCustomFormatWithAllFields()
    {
        EcLogYamlConfig config = mockConfig(ALL_FIELDS_OPTIONAL);
        LogPrinter printer = givenPrinterWithConfig(config);
        QueueReader reader = givenReaderWithSingleRecord(FULL_RECORD);

        printer.print(reader);

        verify(stream).println(eq("timestamp:123|client:1.2.3.4:42|coordinator:5.6.7.8|user:king|batchId:12345678-aaaa-bbbb-cccc-123456789abc|status:ATTEMPT|operation:'select something'|operation-naked:'select something - naked'"));
    }

    @Test(timeout = 5000)
    public void testCustomTimeFormat()
    {
        EcLogYamlConfig config = mockConfig("Timestamp = ${TIMESTAMP}, User = ${USER}, Status = ${STATUS}, Query = '${OPERATION}'");
        when(config.getTimeFormatter()).thenReturn(Optional.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC)));
        LogPrinter printer = givenPrinterWithConfig(config);
        QueueReader reader = givenReaderWithSingleRecord(FULL_RECORD);

        printer.print(reader);

        verify(stream).println(eq("Timestamp = 1970-01-01T00:00:00.123, User = king, Status = ATTEMPT, Query = 'select something'"));
    }

    @Test(timeout = 5000)
    public void testCustomOptionalFields()
    {
        EcLogYamlConfig config = mockConfig(ALL_FIELDS_OPTIONAL);
        StoredAuditRecord record = mock(StoredAuditRecord.class);
        when(record.getUser()).thenReturn(Optional.ofNullable("king"));
        LogPrinter printer = givenPrinterWithConfig(config);
        QueueReader reader = givenReaderWithSingleRecord(record);

        printer.print(reader);

        verify(stream).println(eq("|user:king"));
    }

    @Test
    public void testAvailableFieldFunctions()
    {
        EcLogYamlConfig configMock = mock(EcLogYamlConfig.class);
        Map<String, Function<StoredAuditRecord, Object>> availableFieldFunctions = LogPrinter.getAvailableFieldFunctionMap(configMock);
        StoredAuditRecord emptyRecord = StoredAuditRecord.builder().build();

        assertThat(availableFieldFunctions).containsOnlyKeys("CLIENT_IP", "CLIENT_PORT", "COORDINATOR_IP", "USER", "BATCH_ID", "STATUS", "OPERATION", "OPERATION_NAKED", "TIMESTAMP");


        assertThat(availableFieldFunctions.get("CLIENT_IP").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("CLIENT_IP").apply(FULL_RECORD)).isEqualTo("1.2.3.4");

        assertThat(availableFieldFunctions.get("CLIENT_PORT").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("CLIENT_PORT").apply(FULL_RECORD)).isEqualTo(42);

        assertThat(availableFieldFunctions.get("COORDINATOR_IP").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("COORDINATOR_IP").apply(FULL_RECORD)).isEqualTo("5.6.7.8");

        assertThat(availableFieldFunctions.get("USER").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("USER").apply(FULL_RECORD)).isEqualTo("king");

        assertThat(availableFieldFunctions.get("BATCH_ID").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("BATCH_ID").apply(FULL_RECORD)).isEqualTo(UUID.fromString("12345678-aaaa-bbbb-cccc-123456789abc"));

        assertThat(availableFieldFunctions.get("STATUS").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("STATUS").apply(FULL_RECORD)).isEqualTo(Status.ATTEMPT);

        assertThat(availableFieldFunctions.get("OPERATION").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("OPERATION").apply(FULL_RECORD)).isEqualTo("select something");

        assertThat(availableFieldFunctions.get("OPERATION_NAKED").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("OPERATION_NAKED").apply(FULL_RECORD)).isEqualTo("select something - naked");

        assertThat(availableFieldFunctions.get("TIMESTAMP").apply(emptyRecord)).isNull();
        assertThat(availableFieldFunctions.get("TIMESTAMP").apply(FULL_RECORD)).isEqualTo("123");
    }

    private QueueReader givenReaderWithSingleRecord(StoredAuditRecord authRecord)
    {
        QueueReader reader = mock(QueueReader.class);
        when(reader.hasRecordAvailable()).thenReturn(true).thenReturn(false);
        when(reader.nextRecord()).thenReturn(authRecord);
        return reader;
    }

    private EcLogYamlConfig mockConfig(String format)
    {
        EcLogYamlConfig config = mock(EcLogYamlConfig.class);
        when(config.getLogFormat()).thenReturn(format);
        return config;
    }

    private LogPrinter givenPrinter(ToolOptions options, long pollIntervalMs)
    {
        return new LogPrinter(options, stream, pollIntervalMs, new EcLogYamlConfig());
    }

    private LogPrinter givenPrinterWithConfig(EcLogYamlConfig config)
    {
        return new LogPrinter(ToolOptions.builder().withPath(DEFAULT_PATH).build(), stream, 10, config);
    }

    private QueueReader givenRecords(boolean withBatchId, int count1, int count2)
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

        List<StoredAuditRecord> records = new ArrayList<>();
        for (int i = 0; i < count1 + count2; i++)
        {
            records.add(mockRecord((long) i, "1.2.3.4", 222, "5.6.7.8", "king", Status.ATTEMPT, withBatchId ? UUID.fromString("b23534c7-93af-497f-b00c-1edaaa335caa") : null, "SELECT QUERY"));
        }
        OngoingStubbing<StoredAuditRecord> recordStub = when(reader.nextRecord());
        for (StoredAuditRecord record : records)
        {
            recordStub = recordStub.thenReturn(record);
        }

        return reader;
    }

    private static StoredAuditRecord mockRecord(Long timestamp, String clientHost, Integer clientPort, String coordinatorHost, String user, Status status, UUID batchId, String operation)
    {
        StoredAuditRecord record = mock(StoredAuditRecord.class);
        when(record.getTimestamp()).thenReturn(Optional.ofNullable(timestamp));
        when(record.getClientAddress()).thenReturn(Optional.ofNullable(createAddress(clientHost)));
        when(record.getClientPort()).thenReturn(Optional.ofNullable(clientPort));
        when(record.getCoordinatorAddress()).thenReturn(Optional.ofNullable(createAddress(coordinatorHost)));
        when(record.getUser()).thenReturn(Optional.ofNullable(user));
        when(record.getBatchId()).thenReturn(Optional.ofNullable(batchId));
        when(record.getStatus()).thenReturn(Optional.ofNullable(status));
        when(record.getOperation()).thenReturn(Optional.ofNullable(operation));
        when(record.getNakedOperation()).thenReturn(Optional.ofNullable(operation).map(op -> op + " - naked"));
        return record;
    }

    private static InetAddress createAddress(String hostname)
    {
        try
        {
            return InetAddress.getByName(hostname);
        }
        catch (UnknownHostException e)
        {
            throw new AssertionError("Error creating address", e);
        }
    }

    private void verifySingleRecordsFromStartOfSequence(int count)
    {
        for (int i = 0; i < count; i++)
        {
            verify(stream).println(eq(i + "|1.2.3.4:222|5.6.7.8|king|ATTEMPT|SELECT QUERY"));
        }
    }

    private void verifyBatchRecordsFromStartOfSequence(int count)
    {
        for (int i = 0; i < count; i++)
        {
            verify(stream).println(eq(i + "|1.2.3.4:222|5.6.7.8|king|ATTEMPT|b23534c7-93af-497f-b00c-1edaaa335caa|SELECT QUERY"));
        }
    }
}
