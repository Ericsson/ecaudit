//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.logger;

import java.net.InetAddress;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.Status;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestSlf4jAuditLogger
{
    @Mock
    Logger mockLogger;

    @InjectMocks
    Slf4jAuditLogger logger;

    @Test
    public void testAuditEntryNoBatch() throws Exception
    {
        String expectedStatement = "select * from ks.tbl";
        InetAddress expectedAddress = mock(InetAddress.class);
        String expectedHostAddress = "127.0.0.1";
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;

        when(expectedAddress.getHostAddress()).thenReturn(expectedHostAddress);

        AuditEntry logEntry = AuditEntry.newBuilder()
                                        .user(expectedUser)
                                        .client(expectedAddress)
                                        .operation(new SimpleAuditOperation(expectedStatement))
                                        .status(expectedStatus)
                                        .build();

        logger.log(logEntry);

        // Capture and perform validation
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger, times(1)).info(captor.capture());

        String auditLogEntry = captor.getValue();
        assertThat(StringUtils.split(auditLogEntry, '|').length).isEqualTo(4);
        assertThat(auditLogEntry).contains(
        String.format("user:'%s'", expectedUser),
        String.format("client:'%s'", expectedHostAddress),
        String.format("operation:'%s'", expectedStatement),
        String.format("status:'%s'", expectedStatus.toString()));
        assertThat(auditLogEntry).doesNotContain("batchId:");
    }

    @Test
    public void testAuditEntryWithBatch() throws Exception
    {
        String expectedStatement = "select * from ks.tbl";
        InetAddress expectedAddress = mock(InetAddress.class);
        String expectedHostAddress = "127.0.0.1";
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;
        UUID expectedBatchId = UUID.randomUUID();

        when(expectedAddress.getHostAddress()).thenReturn(expectedHostAddress);

        AuditEntry logEntry = AuditEntry.newBuilder()
                                        .user(expectedUser)
                                        .client(expectedAddress)
                                        .operation(new SimpleAuditOperation(expectedStatement))
                                        .status(expectedStatus)
                                        .batch(expectedBatchId)
                                        .build();

        logger.log(logEntry);

        // Capture and perform validation
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger, times(1)).info(captor.capture());

        String auditLogEntry = captor.getValue();
        assertThat(StringUtils.split(auditLogEntry, '|').length).isEqualTo(5);
        assertThat(auditLogEntry).contains(
        String.format("user:'%s'", expectedUser),
        String.format("client:'%s'", expectedHostAddress),
        String.format("operation:'%s'", expectedStatement),
        String.format("batchId:'%s'", expectedBatchId.toString()),
        String.format("status:'%s'", expectedStatus.toString()));
    }
}
