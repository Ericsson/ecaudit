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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.AuditRecordReadMarshallable;
import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.test.chronicle.RecordValues;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.ReadMarshallable;
import net.openhft.chronicle.wire.ValueIn;
import net.openhft.chronicle.wire.WireIn;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestQueueReader
{
    @Mock
    private ChronicleQueue queue;

    @Mock
    private ExcerptTailer tailer;

    private RecordValues defaultValues;

    @Before
    public void before() throws UnknownHostException
    {
        defaultValues = RecordValues.defaultValues();

        when(queue.createTailer()).thenReturn(tailer);
        when(tailer.toEnd()).thenReturn(tailer);
        when(tailer.index()).thenReturn(0L).thenReturn(1000L);
    }

    @Test
    public void testNothingToRead()
    {
        when(tailer.readDocument(any(ReadMarshallable.class))).thenReturn(false);
        QueueReader reader = givenReader();

        assertThat(reader.hasRecordAvailable()).isFalse();
    }

    @Test
    public void testNothingToReadDirect()
    {
        when(tailer.readDocument(any(ReadMarshallable.class))).thenReturn(false);
        QueueReader reader = givenReader();

        assertThat(reader.nextRecord()).isNull();
    }

    @Test
    public void testValidSingleRecord() throws UnknownHostException
    {
        givenNextRecordIs(defaultValues);
        QueueReader reader = givenReader();

        assertThat(reader.hasRecordAvailable()).isTrue();
        AuditRecord auditRecord = reader.nextRecord();
        assertRecordMatchesWire(auditRecord, defaultValues);
    }

    @Test
    public void testValidSingleTailRecord() throws UnknownHostException
    {
        givenNextRecordIs(defaultValues);
        QueueReader reader = givenReader(ToolOptions.builder().withTail(1).build());

        verify(tailer).moveToIndex(eq(999L));
        assertThat(reader.hasRecordAvailable()).isTrue();
        AuditRecord auditRecord = reader.nextRecord();
        assertRecordMatchesWire(auditRecord, defaultValues);
    }

    @Test
    public void testValidSingleRecordDirect() throws UnknownHostException
    {
        givenNextRecordIs(defaultValues);
        QueueReader reader = givenReader();

        AuditRecord auditRecord = reader.nextRecord();
        assertRecordMatchesWire(auditRecord, defaultValues);
    }

    @Test
    public void testValidBatchRecord() throws UnknownHostException
    {
        givenNextRecordIs(defaultValues.butWithType("ecaudit-batch").butWithBatchId(UUID.fromString("b23534c7-93af-497f-b00c-1edaaa335caa")));
        QueueReader reader = givenReader();

        assertThat(reader.hasRecordAvailable()).isTrue();
        AuditRecord auditRecord = reader.nextRecord();
        assertRecordMatchesWire(auditRecord, defaultValues);
    }

    @Test
    public void testFailOnCorruptRecord()
    {
        givenNextRecordIs(defaultValues.butWithClientAddress(new byte[]{ 1, 2, 3 }));
        QueueReader reader = givenReader();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(reader::hasRecordAvailable)
        .withMessageContaining("Corrupt");
    }

    private void givenNextRecordIs(RecordValues recordValues)
    {
        WireIn wireMock = mock(WireIn.class);

        ValueIn versionValueMock = mock(ValueIn.class);
        when(versionValueMock.int16()).thenReturn(recordValues.getVersion());
        when(wireMock.read(eq("version"))).thenReturn(versionValueMock);

        ValueIn typeValueMock = mock(ValueIn.class);
        when(typeValueMock.text()).thenReturn(recordValues.getType());
        when(wireMock.read(eq("type"))).thenReturn(typeValueMock);

        ValueIn timestampValueMock = mock(ValueIn.class);
        when(timestampValueMock.int64()).thenReturn(recordValues.getTimestamp());
        when(wireMock.read(eq("timestamp"))).thenReturn(timestampValueMock);

        ValueIn clientIpValueMock = mock(ValueIn.class);
        when(clientIpValueMock.bytes()).thenReturn(recordValues.getClientAddress());
        when(wireMock.read(eq("client_ip"))).thenReturn(clientIpValueMock);

        ValueIn clientPortValueMock = mock(ValueIn.class);
        when(clientPortValueMock.int32()).thenReturn(recordValues.getClientPort());
        when(wireMock.read(eq("client_port"))).thenReturn(clientPortValueMock);

        ValueIn coordinatorIpValueMock = mock(ValueIn.class);
        when(coordinatorIpValueMock.bytes()).thenReturn(recordValues.getCoordinatorAddress());
        when(wireMock.read(eq("coordinator_ip"))).thenReturn(coordinatorIpValueMock);

        ValueIn userValueMock = mock(ValueIn.class);
        when(userValueMock.text()).thenReturn(recordValues.gethUser());
        when(wireMock.read(eq("user"))).thenReturn(userValueMock);

        if (recordValues.getBatchId() != null)
        {
            ValueIn batchIdValueMock = mock(ValueIn.class);
            when(batchIdValueMock.uuid()).thenReturn(recordValues.getBatchId());
            when(wireMock.read(eq("batchId"))).thenReturn(batchIdValueMock);
        }

        ValueIn statusValueMock = mock(ValueIn.class);
        when(statusValueMock.text()).thenReturn(recordValues.getStatus());
        when(wireMock.read(eq("status"))).thenReturn(statusValueMock);

        ValueIn operationValueMock = mock(ValueIn.class);
        when(operationValueMock.text()).thenReturn(recordValues.getOperation());
        when(wireMock.read(eq("operation"))).thenReturn(operationValueMock);

        when(tailer.readDocument(any(ReadMarshallable.class)))
        .thenAnswer((Answer<Boolean>) invocation -> {
                        AuditRecordReadMarshallable readMarshallable = invocation.getArgument(0);
                        readMarshallable.readMarshallable(wireMock);
                        return true;
                    }
        );
    }

    private QueueReader givenReader()
    {
        return givenReader(ToolOptions.builder().build());
    }

    private QueueReader givenReader(ToolOptions toolOptions)
    {
        return new QueueReader(toolOptions, queue);
    }

    private void assertRecordMatchesWire(AuditRecord actualAuditRecord, RecordValues expectedValues) throws UnknownHostException
    {
        assertThat(actualAuditRecord.getTimestamp()).isEqualTo(expectedValues.getTimestamp());
        assertThat(actualAuditRecord.getClientAddress()).isEqualTo(new InetSocketAddress(InetAddress.getByAddress(expectedValues.getClientAddress()), expectedValues.getClientPort()));
        assertThat(actualAuditRecord.getCoordinatorAddress()).isEqualTo(InetAddress.getByAddress(expectedValues.getCoordinatorAddress()));
        assertThat(actualAuditRecord.getUser()).isEqualTo(expectedValues.gethUser());
        if (expectedValues.getBatchId() != null)
        {
            assertThat(actualAuditRecord.getBatchId()).contains(expectedValues.getBatchId());
        }
        else
        {
            assertThat(actualAuditRecord.getBatchId()).isEmpty();
        }
        assertThat(actualAuditRecord.getStatus().name()).isEqualTo(expectedValues.getStatus());
        assertThat(actualAuditRecord.getOperation().getOperationString()).isEqualTo(expectedValues.getOperation());
    }
}
