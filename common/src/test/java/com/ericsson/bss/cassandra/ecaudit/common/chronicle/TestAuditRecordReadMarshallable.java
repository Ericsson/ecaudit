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
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

import org.junit.Before;
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

    private RecordValues defaultValues;
    private AuditRecordReadMarshallable readMarshallable;

    @Before
    public void before() throws UnknownHostException
    {
        defaultValues = RecordValues.defaultValues();
        readMarshallable = new AuditRecordReadMarshallable();
    }

    @Test
    public void testReadSingleRecord() throws Exception
    {
        givenNextRecordIs(defaultValues);

        readMarshallable.readMarshallable(wireInMock);

        AuditRecord actualAuditRecord = readMarshallable.getAuditRecord();

        assertThatRecordIsSame(actualAuditRecord, defaultValues);
    }

    @Test
    public void testReadBatchBatch() throws Exception
    {
        RecordValues expectedValues = defaultValues
                                      .butWithType("ecaudit-batch")
                                      .butWithBatchId(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"));
        givenNextRecordIs(expectedValues);

        readMarshallable.readMarshallable(wireInMock);

        AuditRecord actualAuditRecord = readMarshallable.getAuditRecord();

        assertThatRecordIsSame(actualAuditRecord, expectedValues);
    }

    @Test
    public void testReuseMarshallable()
    {
        givenNextRecordIs(defaultValues);

        readMarshallable.readMarshallable(wireInMock);

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Tried to read from wire with used marshallable");
    }

    @Test
    public void testGetBeforeRead()
    {
        assertThatIllegalStateException()
        .isThrownBy(readMarshallable::getAuditRecord)
        .withMessageContaining("No record has been read from the wire");
    }

    @Test
    public void testUnknownVersion()
    {
        givenNextRecordIs(defaultValues.butWithVersion((short) 999));

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Unsupported record version")
        .withMessageContaining("999");
    }

    @Test
    public void testUnknownType()
    {
        givenNextRecordIs(defaultValues.butWithType("fake-entry"));

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Unsupported record type field")
        .withMessageContaining("fake-entry");
    }

    @Test
    public void testIllegalClientAddress()
    {
        givenNextRecordIs(defaultValues.butWithClientAddress(new byte[]{ 1, 2, 3 }));

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Corrupt client_ip field");
    }

    @Test
    public void testIllegalCoordinatorAddress()
    {
        givenNextRecordIs(defaultValues.butWithCoordinatorAddress(new byte[]{ 1, 2, 3 }));

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Corrupt coordinator_ip field");
    }

    @Test
    public void testUnknownStatus()
    {
        givenNextRecordIs(defaultValues.butWithStatus("GUCK"));

        assertThatExceptionOfType(IORuntimeException.class)
        .isThrownBy(() -> readMarshallable.readMarshallable(wireInMock))
        .withMessageContaining("Corrupt record status field");
    }

    private static class RecordValues
    {
        private short version = 0;
        private String type = "ecaudit-single";
        private long timestamp = 42;
        private byte[] clientAddress;
        private int clientPort = 555;
        private byte[] coordinatorAddress;
        private String user = "john";
        private UUID batchId = null;
        private String status = Status.ATTEMPT.toString();
        private String operation = "Some operation";

        private RecordValues() throws UnknownHostException
        {
            clientAddress = InetAddress.getByName("1.2.3.4").getAddress();
            coordinatorAddress = InetAddress.getByName("5.6.7.8").getAddress();
        }

        static RecordValues defaultValues() throws UnknownHostException
        {
            return new RecordValues();
        }

        RecordValues butWithVersion(short version)
        {
            this.version = version;
            return this;
        }

        RecordValues butWithType(String type)
        {
            this.type = type;
            return this;
        }

        RecordValues butWithClientAddress(byte[] clientAddress)
        {
            this.clientAddress = clientAddress;
            return this;
        }

        RecordValues butWithCoordinatorAddress(byte[] coordinatorAddress)
        {
            this.coordinatorAddress = coordinatorAddress;
            return this;
        }

        RecordValues butWithBatchId(UUID batchId)
        {
            this.batchId = batchId;
            return this;
        }

        RecordValues butWithStatus(String status)
        {
            this.status = status;
            return this;
        }

        public short getVersion()
        {
            return version;
        }

        public String getType()
        {
            return type;
        }

        public long getTimestamp()
        {
            return timestamp;
        }

        byte[] getClientAddress()
        {
            return clientAddress;
        }

        int getClientPort()
        {
            return clientPort;
        }

        byte[] getCoordinatorAddress()
        {
            return coordinatorAddress;
        }

        String gethUser()
        {
            return user;
        }

        UUID getBatchId()
        {
            return batchId;
        }

        String getStatus()
        {
            return status;
        }

        String getOperation()
        {
            return operation;
        }
    }

    private void givenNextRecordIs(RecordValues values)
    {
        ValueIn versionValueMock = mock(ValueIn.class);
        when(versionValueMock.int16()).thenReturn(values.getVersion());
        when(wireInMock.read(eq("version"))).thenReturn(versionValueMock);

        ValueIn typeValueMock = mock(ValueIn.class);
        when(typeValueMock.text()).thenReturn(values.getType());
        when(wireInMock.read(eq("type"))).thenReturn(typeValueMock);

        ValueIn timestampValueMock = mock(ValueIn.class);
        when(timestampValueMock.int64()).thenReturn(values.getTimestamp());
        when(wireInMock.read(eq("timestamp"))).thenReturn(timestampValueMock);

        ValueIn clientIpValueMock = mock(ValueIn.class);
        when(clientIpValueMock.bytes()).thenReturn(values.getClientAddress());
        when(wireInMock.read(eq("client_ip"))).thenReturn(clientIpValueMock);

        ValueIn clientPortValueMock = mock(ValueIn.class);
        when(clientPortValueMock.int32()).thenReturn(values.getClientPort());
        when(wireInMock.read(eq("client_port"))).thenReturn(clientPortValueMock);

        ValueIn coordinatorValueMock = mock(ValueIn.class);
        when(coordinatorValueMock.bytes()).thenReturn(values.getCoordinatorAddress());
        when(wireInMock.read(eq("coordinator_ip"))).thenReturn(coordinatorValueMock);

        ValueIn userValueMock = mock(ValueIn.class);
        when(userValueMock.text()).thenReturn(values.gethUser());
        when(wireInMock.read(eq("user"))).thenReturn(userValueMock);

        if (values.getBatchId() != null)
        {
            ValueIn batchIdValueMock = mock(ValueIn.class);
            when(batchIdValueMock.uuid()).thenReturn(values.getBatchId());
            when(wireInMock.read(eq("batchId"))).thenReturn(batchIdValueMock);
        }

        ValueIn statusValueMock = mock(ValueIn.class);
        when(statusValueMock.text()).thenReturn(values.getStatus());
        when(wireInMock.read(eq("status"))).thenReturn(statusValueMock);

        ValueIn operationValueMock = mock(ValueIn.class);
        when(operationValueMock.text()).thenReturn(values.getOperation());
        when(wireInMock.read(eq("operation"))).thenReturn(operationValueMock);
    }

    private void assertThatRecordIsSame(AuditRecord actualAuditRecord, RecordValues expectedValues) throws UnknownHostException
    {
        assertThat(actualAuditRecord.getBatchId()).isEqualTo(Optional.ofNullable(expectedValues.getBatchId()));
        assertThat(actualAuditRecord.getClientAddress().getAddress()).isEqualTo(InetAddress.getByAddress(expectedValues.getClientAddress()));
        assertThat(actualAuditRecord.getClientAddress().getPort()).isEqualTo(expectedValues.getClientPort());
        assertThat(actualAuditRecord.getCoordinatorAddress()).isEqualTo(InetAddress.getByAddress(expectedValues.getCoordinatorAddress()));
        assertThat(actualAuditRecord.getStatus().name()).isEqualTo(expectedValues.getStatus());
        assertThat(actualAuditRecord.getOperation().getOperationString()).isEqualTo(expectedValues.getOperation());
        assertThat(actualAuditRecord.getUser()).isEqualTo(expectedValues.gethUser());
        assertThat(actualAuditRecord.getTimestamp()).isEqualTo(expectedValues.getTimestamp());
    }
}
