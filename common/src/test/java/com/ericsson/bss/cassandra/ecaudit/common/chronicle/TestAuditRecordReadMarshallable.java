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

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.FieldSelector.Field;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.common.record.StoredAuditRecord;
import com.ericsson.bss.cassandra.ecaudit.test.chronicle.RecordValues;
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

        StoredAuditRecord actualAuditRecord = readMarshallable.getAuditRecord();

        assertThatRecordIsSame(actualAuditRecord, defaultValues);
    }

    @Test
    public void testReadBatchBatch() throws Exception
    {
        RecordValues expectedValues = defaultValues
                                      .butWithBatchId(UUID.fromString("bd92aeb1-3373-4d6a-b65a-0d60295f66c9"));
        givenNextRecordIs(expectedValues);

        readMarshallable.readMarshallable(wireInMock);

        StoredAuditRecord actualAuditRecord = readMarshallable.getAuditRecord();

        assertThatRecordIsSame(actualAuditRecord, expectedValues);
    }

     @Test
     public void testReadSubjectMatch() throws Exception
     {
         RecordValues expectedValues = defaultValues.butWithSubject("bob-subject");

         givenNextRecordIs(expectedValues);

         readMarshallable.readMarshallable(wireInMock);

         StoredAuditRecord actualAuditRecord = readMarshallable.getAuditRecord();

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

    private void givenNextRecordIs(RecordValues values)
    {
        ValueIn versionValueMock = mock(ValueIn.class);
        when(versionValueMock.int16()).thenReturn(values.getVersion());
        when(wireInMock.read(eq("version"))).thenReturn(versionValueMock);

        ValueIn typeValueMock = mock(ValueIn.class);
        when(typeValueMock.text()).thenReturn(values.getType());
        when(wireInMock.read(eq("type"))).thenReturn(typeValueMock);

        FieldSelector selectedFields = FieldSelector.DEFAULT_FIELDS;
        if (values.getBatchId() == null)
        {
            selectedFields = selectedFields.withoutField(Field.BATCH_ID);
        }

        if (values.getSubject() != null)
        {
            selectedFields = selectedFields.withField(Field.SUBJECT);
        }

        ValueIn fieldsValueMock = mock(ValueIn.class);
        when(fieldsValueMock.int32()).thenReturn(selectedFields.getBitmap());
        when(wireInMock.read(eq("fields"))).thenReturn(fieldsValueMock);

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

        if (values.getSubject() != null)
        {
            ValueIn subjectValueMock = mock(ValueIn.class);
            when(subjectValueMock.text()).thenReturn(values.getSubject());
            when(wireInMock.read(eq("subject"))).thenReturn(subjectValueMock);
        }
    }

    private void assertThatRecordIsSame(StoredAuditRecord actualAuditRecord, RecordValues expectedValues) throws UnknownHostException
    {
        assertThat(actualAuditRecord.getBatchId()).isEqualTo(Optional.ofNullable(expectedValues.getBatchId()));
        assertThat(actualAuditRecord.getClientAddress()).contains(InetAddress.getByAddress(expectedValues.getClientAddress()));
        assertThat(actualAuditRecord.getClientPort()).contains(expectedValues.getClientPort());
        assertThat(actualAuditRecord.getCoordinatorAddress()).contains(InetAddress.getByAddress(expectedValues.getCoordinatorAddress()));
        assertThat(actualAuditRecord.getStatus().map(Status::name)).contains(expectedValues.getStatus());
        assertThat(actualAuditRecord.getOperation()).contains(expectedValues.getOperation());
        assertThat(actualAuditRecord.getNakedOperation()).isEmpty();
        assertThat(actualAuditRecord.getUser()).contains(expectedValues.gethUser());
        assertThat(actualAuditRecord.getTimestamp()).contains(expectedValues.getTimestamp());
        assertThat(actualAuditRecord.getSubject()).isEqualTo(Optional.ofNullable(expectedValues.getSubject()));
    }
}
