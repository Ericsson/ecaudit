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
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
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
        logger = new ChronicleAuditLogger(mockWriter);
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
        AuditEntry auditEntry = givenAuditEntry(Instant.parse("1987-06-30T14:39:00Z").toEpochMilli(), "Patrik Sjoberg", "2.42.2.42", "High Jump", Status.ATTEMPT, null);

        logger.log(auditEntry);

        verifyWire(Instant.parse("1987-06-30T14:39:00Z").toEpochMilli(),"Patrik Sjoberg", "2.42.2.42", "High Jump", Status.ATTEMPT, null);
    }

    @Test
    public void batchStatement() throws Exception
    {
        AuditEntry auditEntry = givenAuditEntry(Instant.parse("1987-06-30T14:56:00Z").toEpochMilli(), "Patrik Sjoberg", "2.44.2.44", "High Jump", Status.FAILED, UUID.fromString("4910e9a6-9d26-40f8-ad8c-5c0436784969"));

        logger.log(auditEntry);

        verifyWire(Instant.parse("1987-06-30T14:56:00Z").toEpochMilli(),"Patrik Sjoberg", "2.44.2.44", "High Jump", Status.FAILED, UUID.fromString("4910e9a6-9d26-40f8-ad8c-5c0436784969"));
    }

    @Test
    public void interruptOnPut() throws Exception
    {
        AuditEntry auditEntry = givenAuditEntry(Instant.parse("1993-07-27T18:15:30Z").toEpochMilli(), "Javier Sotomayor", "2.45.2.45", "High Jump", Status.ATTEMPT, UUID.fromString("4910e9a6-9d26-40f8-ad8c-5c0436784969"));
        doThrow(InterruptedException.class).when(mockWriter).put(any());

        logger.log(auditEntry);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // Clear interrupt flag
        Thread.interrupted();
    }

    private AuditEntry givenAuditEntry(Long timestamp, String user, String ip, String operation, Status status, UUID batchId) throws UnknownHostException
    {
        return AuditEntry.newBuilder()
                         .timestamp(timestamp)
                         .user(user)
                         .client(InetAddress.getByName(ip))
                         .operation(new SimpleAuditOperation(operation))
                         .status(status)
                         .batch(batchId)
                         .build();
    }

    private void verifyWire(Long timestamp, String user, String ip, String operation, Status status, UUID batchId) throws Exception
    {
        ArgumentCaptor<WriteMarshallable> marshallableArgumentCaptor = ArgumentCaptor.forClass(WriteMarshallable.class);

        verify(mockWriter).put(marshallableArgumentCaptor.capture());

        WriteMarshallable writeMarshallable = marshallableArgumentCaptor.getValue();
        when(mockWire.write(anyString())).thenReturn(mockValue);
        writeMarshallable.writeMarshallable(mockWire);

        verify(mockWire).write(eq("version"));
        verify(mockValue).int16(eq((short) 800));
        verify(mockWire).write(eq("type"));
        if (batchId == null)
        {
            verify(mockValue).text(eq("single-entry"));
        }
        else
        {
            verify(mockValue).text(eq("batch-entry"));
        }

        verify(mockWire).write(eq("timestamp"));
        verify(mockValue).int64(eq(timestamp));
        verify(mockWire).write(eq("client"));
        verify(mockValue).bytes(eq(InetAddress.getByName(ip).getAddress()));
        verify(mockWire).write(eq("user"));
        verify(mockValue).text(eq(user));
        verify(mockWire).write(eq("status"));
        verify(mockValue).text(eq(status.name()));
        verify(mockWire).write(eq("operation"));
        verify(mockValue).text(eq(operation));

        if (batchId != null)
        {
            verify(mockWire).write(eq("batchId"));
            verify(mockValue).uuid(eq(batchId));
        }
    }
}
