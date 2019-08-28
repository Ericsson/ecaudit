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
package com.ericsson.bss.cassandra.ecaudit.logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.FieldSelector;
import com.ericsson.bss.cassandra.ecaudit.common.chronicle.FieldSelector.Field;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import net.openhft.chronicle.wire.ValueOut;
import net.openhft.chronicle.wire.WireOut;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestChronicleAuditLogger
{
    @Mock
    private ChronicleWriter mockWriter;

    @Mock
    private WireOut mockWire;

    @Mock
    private ValueOut mockValue;

    private ChronicleAuditLogger logger;

    @Before
    public void before()
    {
        logger = new ChronicleAuditLogger(mockWriter, FieldSelector.DEFAULT_FIELDS);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockWire);
        verifyNoMoreInteractions(mockValue);
    }

    @Test
    public void singleStatement() throws Exception
    {
        AuditEntry expectedAuditEntry = likeGenericRecord().build();

        logger.log(expectedAuditEntry);

        assertThatWireMatchRecord(expectedAuditEntry);
    }

    @Test
    public void batchStatement() throws Exception
    {
        AuditEntry expectedAuditEntry = likeGenericRecord().batch(UUID.fromString("4910e9a6-9d26-40f8-ad8c-5c0436784969")).build();

        logger.log(expectedAuditEntry);

        assertThatWireMatchRecord(expectedAuditEntry);
    }

    @Test
    public void interruptOnPut() throws Exception
    {
        AuditEntry expectedAuditEntry = likeGenericRecord().build();
        doThrow(InterruptedException.class).when(mockWriter).put(any());

        logger.log(expectedAuditEntry);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // Clear interrupt flag
        Thread.interrupted();
    }

    private AuditEntry.Builder likeGenericRecord() throws UnknownHostException
    {
        return AuditEntry.newBuilder()
                         .timestamp(Instant.parse("1993-07-27T18:15:30Z").toEpochMilli())
                         .user("Javier Sotomayor")
                         .client(new InetSocketAddress(InetAddress.getByName("2.45.2.45"), 245))
                         .coordinator(InetAddress.getByName("5.6.7.8"))
                         .operation(new SimpleAuditOperation("High Jump"))
                         .status(Status.ATTEMPT);
    }

    private void assertThatWireMatchRecord(AuditEntry expectedAuditEntry) throws Exception
    {
        ArgumentCaptor<WriteMarshallable> marshallableArgumentCaptor = ArgumentCaptor.forClass(WriteMarshallable.class);

        verify(mockWriter).put(marshallableArgumentCaptor.capture());

        WriteMarshallable writeMarshallable = marshallableArgumentCaptor.getValue();
        when(mockWire.write(anyString())).thenReturn(mockValue);
        writeMarshallable.writeMarshallable(mockWire);

        verify(mockWire).write(eq("version"));
        verify(mockValue).int16(eq((short) 1));
        verify(mockWire).write(eq("type"));
        verify(mockValue).text(eq("ecaudit"));
        verify(mockWire).write(eq("fields"));

        if (expectedAuditEntry.getBatchId().isPresent())
        {
            int bitmap = FieldSelector.DEFAULT_FIELDS.getBitmap();
            verify(mockValue).int32(eq(bitmap));
        }
        else
        {
            int bitmapWithoutBatch = FieldSelector.DEFAULT_FIELDS.withoutField(Field.BATCH_ID).getBitmap();
            verify(mockValue).int32(eq(bitmapWithoutBatch));
        }

        verify(mockWire).write(eq("timestamp"));
        verify(mockValue).int64(eq(expectedAuditEntry.getTimestamp()));
        verify(mockWire).write(eq("client_ip"));
        verify(mockValue).bytes(eq(expectedAuditEntry.getClientAddress().getAddress().getAddress()));
        verify(mockWire).write(eq("client_port"));
        verify(mockValue).int32(eq(expectedAuditEntry.getClientAddress().getPort()));
        verify(mockWire).write(eq("coordinator_ip"));
        verify(mockValue).bytes(eq(expectedAuditEntry.getCoordinatorAddress().getAddress()));
        verify(mockWire).write(eq("user"));
        verify(mockValue).text(eq(expectedAuditEntry.getUser()));
        verify(mockWire).write(eq("status"));
        verify(mockValue).text(eq(expectedAuditEntry.getStatus().name()));
        verify(mockWire).write(eq("operation"));
        verify(mockValue).text(eq(expectedAuditEntry.getOperation().getOperationString()));

        if (expectedAuditEntry.getBatchId().isPresent())
        {
            verify(mockWire).write(eq("batchId"));
            verify(mockValue).uuid(eq(expectedAuditEntry.getBatchId().get()));
        }
    }
}
