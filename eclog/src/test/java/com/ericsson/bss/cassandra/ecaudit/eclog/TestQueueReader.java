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
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
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

    @Before
    public void before()
    {
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
        givenNextRecordIsSingle();
        QueueReader reader = givenReader();

        assertThat(reader.hasRecordAvailable()).isTrue();
        AuditRecord auditRecord = reader.nextRecord();
        assertRecordMatchesSingle(auditRecord);
    }

    @Test
    public void testValidSingleTailRecord() throws UnknownHostException
    {
        givenNextRecordIsSingle();
        QueueReader reader = givenReader(ToolOptions.builder().withTail(1).build());

        verify(tailer).moveToIndex(eq(999L));
        assertThat(reader.hasRecordAvailable()).isTrue();
        AuditRecord auditRecord = reader.nextRecord();
        assertRecordMatchesSingle(auditRecord);
    }

    @Test
    public void testValidSingleRecordDirect() throws UnknownHostException
    {
        givenNextRecordIsSingle();
        QueueReader reader = givenReader();

        AuditRecord auditRecord = reader.nextRecord();
        assertRecordMatchesSingle(auditRecord);
    }

    @Test
    public void testValidBatchRecord() throws UnknownHostException
    {
        givenNextRecordIs((short) 0, "ecaudit-batch", 42L, InetAddress.getByName("1.2.3.4").getAddress(), 123, InetAddress.getByName("5.6.7.8").getAddress(), "john", UUID.fromString("b23534c7-93af-497f-b00c-1edaaa335caa"), Status.ATTEMPT, "Some operation");
        QueueReader reader = givenReader();

        assertThat(reader.hasRecordAvailable()).isTrue();
        AuditRecord auditRecord = reader.nextRecord();
        assertRecordMatches(auditRecord, 42L, InetAddress.getByName("1.2.3.4"), 123, InetAddress.getByName("5.6.7.8"), "john", UUID.fromString("b23534c7-93af-497f-b00c-1edaaa335caa"), Status.ATTEMPT, "Some operation");
    }

    @Test
    public void testFailOnCorruptRecord() throws UnknownHostException
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, new byte[]{ 1, 2, 3 }, 123, InetAddress.getByName("5.6.7.8").getAddress(), "john", null, Status.ATTEMPT, "Some operation");
        QueueReader reader = givenReader();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(reader::hasRecordAvailable)
        .withMessageContaining("Corrupt");
    }

    private void givenNextRecordIsSingle() throws UnknownHostException
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, InetAddress.getByName("1.2.3.4").getAddress(), 123, InetAddress.getByName("5.6.7.8").getAddress(), "john", null, Status.ATTEMPT, "Some operation");
    }

    private void givenNextRecordIs(short version, String type, long timestamp, byte[] clientAddress, int clientPort, byte[] coordinatorAddress, String user, UUID batchId, Status status, String operation)
    {
        WireIn wireMock = mock(WireIn.class);

        ValueIn versionValueMock = mock(ValueIn.class);
        when(versionValueMock.int16()).thenReturn(version);
        when(wireMock.read(eq("version"))).thenReturn(versionValueMock);

        ValueIn typeValueMock = mock(ValueIn.class);
        when(typeValueMock.text()).thenReturn(type);
        when(wireMock.read(eq("type"))).thenReturn(typeValueMock);

        ValueIn timestampValueMock = mock(ValueIn.class);
        when(timestampValueMock.int64()).thenReturn(timestamp);
        when(wireMock.read(eq("timestamp"))).thenReturn(timestampValueMock);

        ValueIn clientIpValueMock = mock(ValueIn.class);
        when(clientIpValueMock.bytes()).thenReturn(clientAddress);
        when(wireMock.read(eq("client_ip"))).thenReturn(clientIpValueMock);

        ValueIn clientPortValueMock = mock(ValueIn.class);
        when(clientPortValueMock.int32()).thenReturn(clientPort);
        when(wireMock.read(eq("client_port"))).thenReturn(clientPortValueMock);

        ValueIn coordinatorIpValueMock = mock(ValueIn.class);
        when(coordinatorIpValueMock.bytes()).thenReturn(coordinatorAddress);
        when(wireMock.read(eq("coordinator_ip"))).thenReturn(coordinatorIpValueMock);

        ValueIn userValueMock = mock(ValueIn.class);
        when(userValueMock.text()).thenReturn(user);
        when(wireMock.read(eq("user"))).thenReturn(userValueMock);

        if (batchId != null)
        {
            ValueIn batchIdValueMock = mock(ValueIn.class);
            when(batchIdValueMock.uuid()).thenReturn(batchId);
            when(wireMock.read(eq("batchId"))).thenReturn(batchIdValueMock);
        }

        ValueIn statusValueMock = mock(ValueIn.class);
        when(statusValueMock.text()).thenReturn(status.toString());
        when(wireMock.read(eq("status"))).thenReturn(statusValueMock);

        ValueIn operationValueMock = mock(ValueIn.class);
        when(operationValueMock.text()).thenReturn(operation);
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

    private void assertRecordMatchesSingle(AuditRecord auditRecord) throws UnknownHostException
    {
        assertRecordMatches(auditRecord, 42L, InetAddress.getByName("1.2.3.4"), 123, InetAddress.getByName("5.6.7.8"), "john", null, Status.ATTEMPT, "Some operation");
    }

    private void assertRecordMatches(AuditRecord auditRecord, long timestamp, InetAddress clientAddress, int clientPort, InetAddress coordinatorAddress, String user, UUID batchId, Status status, String operation)
    {
        assertThat(auditRecord.getTimestamp()).isEqualTo(timestamp);
        assertThat(auditRecord.getClientAddress()).isEqualTo(new InetSocketAddress(clientAddress, clientPort));
        assertThat(auditRecord.getCoordinatorAddress()).isEqualTo(coordinatorAddress);
        assertThat(auditRecord.getUser()).isEqualTo(user);
        if (batchId != null)
        {
            assertThat(auditRecord.getBatchId()).contains(batchId);
        }
        else
        {
            assertThat(auditRecord.getBatchId()).isEmpty();
        }
        assertThat(auditRecord.getStatus()).isEqualTo(status);
        assertThat(auditRecord.getOperation().getOperationString()).isEqualTo(operation);
    }
}
