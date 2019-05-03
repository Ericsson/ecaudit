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
import java.util.Optional;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.wire.ValueIn;
import net.openhft.chronicle.wire.WireIn;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditRecordReadMarshallable
{
    @Mock
    private WireIn wireInMock;

    @Test
    public void testReadSingleRecord() throws Exception
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, InetAddress.getByName("1.2.3.4").getAddress(), InetAddress.getByName("5.6.7.8").getAddress(), "john", null, Status.ATTEMPT.toString(), "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        readMarshallable.readMarshallable(wireInMock);

        AuditRecord actualAuditRecord = readMarshallable.getAuditRecord();

        assertThat(actualAuditRecord.getBatchId()).isEmpty();
        assertThat(actualAuditRecord.getClientAddress()).isEqualTo(InetAddress.getByName("1.2.3.4"));
        assertThat(actualAuditRecord.getCoordinatorAddress()).isEqualTo(InetAddress.getByName("5.6.7.8"));
        assertThat(actualAuditRecord.getStatus()).isEqualTo(Status.ATTEMPT);
        assertThat(actualAuditRecord.getOperation().getOperationString()).isEqualTo("Some operation");
        assertThat(actualAuditRecord.getUser()).isEqualTo("john");
        assertThat(actualAuditRecord.getTimestamp()).isEqualTo(42L);
    }

    @Test
    public void testReadBatchBatch() throws Exception
    {
        givenNextRecordIs((short) 0, "ecaudit-batch", 42L, InetAddress.getByName("1.2.3.4").getAddress(), InetAddress.getByName("5.6.7.8").getAddress(), "john", UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"), Status.ATTEMPT.toString(), "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        readMarshallable.readMarshallable(wireInMock);

        AuditRecord actualAuditRecord = readMarshallable.getAuditRecord();

        assertThat(actualAuditRecord.getBatchId()).isEqualTo(Optional.of(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9")));
        assertThat(actualAuditRecord.getClientAddress()).isEqualTo(InetAddress.getByName("1.2.3.4"));
        assertThat(actualAuditRecord.getCoordinatorAddress()).isEqualTo(InetAddress.getByName("5.6.7.8"));
        assertThat(actualAuditRecord.getStatus()).isEqualTo(Status.ATTEMPT);
        assertThat(actualAuditRecord.getOperation().getOperationString()).isEqualTo("Some operation");
        assertThat(actualAuditRecord.getUser()).isEqualTo("john");
        assertThat(actualAuditRecord.getTimestamp()).isEqualTo(42L);
    }

    @Test
    public void testReuseMarshallable() throws Exception
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, InetAddress.getByName("1.2.3.4").getAddress(), InetAddress.getByName("5.6.7.8").getAddress(), "john", null, Status.ATTEMPT.toString(), "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        readMarshallable.readMarshallable(wireInMock);

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Tried to read from wire with used marshallable");
    }

    @Test
    public void testGetBeforeRead()
    {
        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        assertThatIllegalStateException()
        .isThrownBy(readMarshallable::getAuditRecord)
        .withMessageContaining("No record has been read from the wire");
    }

    @Test
    public void testUnknownVersion() throws Exception
    {
        givenNextRecordIs((short) 10, "ecaudit-single", 42L, InetAddress.getByName("1.2.3.4").getAddress(), InetAddress.getByName("5.6.7.8").getAddress(), "john", null, Status.ATTEMPT.toString(), "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Unsupported record version")
        .withMessageContaining("10");
    }

    @Test
    public void testUnknownType() throws Exception
    {
        givenNextRecordIs((short) 0, "fake-entry", 42L, InetAddress.getByName("1.2.3.4").getAddress(), InetAddress.getByName("5.6.7.8").getAddress(), "john", null, "GUCK", "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Unsupported record type field")
        .withMessageContaining("fake-entry");
    }

    @Test
    public void testIllegalClientAddress() throws Exception
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, new byte[]{ 1, 2, 3 }, InetAddress.getByName("5.6.7.8").getAddress(), "john", null, Status.ATTEMPT.toString(), "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Corrupt client IP address field");
    }

    @Test
    public void testNullClientAddress() throws Exception
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, null, InetAddress.getByName("5.6.7.8").getAddress(), "john", null, Status.ATTEMPT.toString(), "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Corrupt client IP address field");
    }

    @Test
    public void testIllegalCoordinatorAddress() throws Exception
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, InetAddress.getByName("1.2.3.4").getAddress(), new byte[]{ 1, 2, 3 }, "john", null, Status.ATTEMPT.toString(), "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Corrupt coordinator IP address field");
    }

    @Test
    public void testNullCoordinatorAddress() throws Exception
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, InetAddress.getByName("1.2.3.4").getAddress(), null, "john", null, Status.ATTEMPT.toString(), "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Corrupt coordinator IP address field");
    }

    @Test
    public void testUnknownStatus() throws Exception
    {
        givenNextRecordIs((short) 0, "ecaudit-single", 42L, InetAddress.getByName("1.2.3.4").getAddress(), InetAddress.getByName("5.6.7.8").getAddress(), "john", null, "GUCK", "Some operation");

        AuditRecordReadMarshallable readMarshallable = new AuditRecordReadMarshallable();

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Corrupt record status field");
    }

    private void givenNextRecordIs(short version, String type, long timestamp, byte[] clientAddress, byte[] coordinatorAddress, String user, UUID batchId, String status, String operation)
    {
        ValueIn versionValueMock = mock(ValueIn.class);
        when(versionValueMock.int16()).thenReturn(version);
        when(wireInMock.read(eq("version"))).thenReturn(versionValueMock);

        ValueIn typeValueMock = mock(ValueIn.class);
        when(typeValueMock.text()).thenReturn(type);
        when(wireInMock.read(eq("type"))).thenReturn(typeValueMock);

        ValueIn timestampValueMock = mock(ValueIn.class);
        when(timestampValueMock.int64()).thenReturn(timestamp);
        when(wireInMock.read(eq("timestamp"))).thenReturn(timestampValueMock);

        ValueIn clientValueMock = mock(ValueIn.class);
        when(clientValueMock.bytes()).thenReturn(clientAddress);
        when(wireInMock.read(eq("client"))).thenReturn(clientValueMock);

        ValueIn coordinatorValueMock = mock(ValueIn.class);
        when(coordinatorValueMock.bytes()).thenReturn(coordinatorAddress);
        when(wireInMock.read(eq("coordinator"))).thenReturn(coordinatorValueMock);

        ValueIn userValueMock = mock(ValueIn.class);
        when(userValueMock.text()).thenReturn(user);
        when(wireInMock.read(eq("user"))).thenReturn(userValueMock);

        if (batchId != null)
        {
            ValueIn batchIdValueMock = mock(ValueIn.class);
            when(batchIdValueMock.uuid()).thenReturn(batchId);
            when(wireInMock.read(eq("batchId"))).thenReturn(batchIdValueMock);
        }

        ValueIn statusValueMock = mock(ValueIn.class);
        when(statusValueMock.text()).thenReturn(status);
        when(wireInMock.read(eq("status"))).thenReturn(statusValueMock);

        ValueIn operationValueMock = mock(ValueIn.class);
        when(operationValueMock.text()).thenReturn(operation);
        when(wireInMock.read(eq("operation"))).thenReturn(operationValueMock);
    }
}
