/*
 * Copyright 2018 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.facade;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.metrics.AuditMetrics;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.AuditObfuscator;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.exceptions.CassandraException;
import org.apache.cassandra.exceptions.ReadTimeoutException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestDefaultAuditor
{
    @Mock
    private AuditLogger mockLogger;

    @Mock
    private AuditFilter mockFilter;

    @Mock
    private AuditObfuscator mockObfuscator;

    @Mock
    private AuditMetrics mockAuditMetrics;

    @Captor
    private ArgumentCaptor<Long> timingCaptor;

    private DefaultAuditor auditor;

    @Before
    public void before()
    {
        auditor = new DefaultAuditor(mockLogger, mockFilter, mockObfuscator, mockAuditMetrics);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockLogger, mockFilter, mockObfuscator);
    }

    @Test
    public void testSetupDelegation()
    {
        auditor.setup();

        verify(mockFilter).setup();
    }

    @Test
    public void testAuditFiltered()
    {
        AuditEntry logEntry = AuditEntry.newBuilder().build();
        when(mockFilter.isFiltered(logEntry)).thenReturn(true);

        long timeTaken = timedOperation(() -> auditor.audit(logEntry));

        verify(mockFilter, times(1)).isFiltered(logEntry);
        verify(mockAuditMetrics, times(1)).filterAuditRequest(timingCaptor.capture(), eq(TimeUnit.NANOSECONDS));
        verifyNoMoreInteractions(mockAuditMetrics);
        verifyZeroInteractions(mockLogger, mockObfuscator);

        long timeMeasured = timingCaptor.getValue();
        assertThat(timeMeasured).isLessThanOrEqualTo(timeTaken);
    }

    @Test
    public void testAuditFilteredThrowsException()
    {
        AuditEntry logEntry = AuditEntry.newBuilder().build();
        when(mockFilter.isFiltered(logEntry)).thenThrow(new ReadTimeoutException(ConsistencyLevel.QUORUM, 1, 1, false));

        long timeTaken = timedOperation(() -> auditor.audit(logEntry), CassandraException.class);

        verify(mockAuditMetrics, times(1)).filterAuditRequest(timingCaptor.capture(), eq(TimeUnit.NANOSECONDS));
        verifyNoMoreInteractions(mockAuditMetrics);
        verifyZeroInteractions(mockLogger, mockObfuscator);

        long timeMeasured = timingCaptor.getValue();
        assertThat(timeMeasured).isLessThanOrEqualTo(timeTaken);
    }

    @Test
    public void testAuditNotFiltered()
    {
        AuditEntry logEntry = AuditEntry.newBuilder().build();
        when(mockFilter.isFiltered(logEntry)).thenReturn(false);
        when(mockObfuscator.obfuscate(logEntry)).thenReturn(logEntry);

        long timeTaken = timedOperation(() -> auditor.audit(logEntry));

        verify(mockFilter, times(1)).isFiltered(logEntry);
        verify(mockObfuscator, times(1)).obfuscate(logEntry);
        verify(mockLogger, times(1)).log(logEntry);
        verify(mockAuditMetrics, times(1)).filterAuditRequest(timingCaptor.capture(), eq(TimeUnit.NANOSECONDS));
        verify(mockAuditMetrics, times(1)).auditRequest(timingCaptor.capture(), eq(TimeUnit.NANOSECONDS));

        long timeMeasured = timingCaptor.getAllValues().stream().mapToLong(l -> l).sum();
        assertThat(timeMeasured).isLessThanOrEqualTo(timeTaken);
    }

    private long timedOperation(Runnable runnable)
    {
        return timedOperation(runnable, null);
    }

    private long timedOperation(Runnable runnable, Class<? extends Exception> expectedExceptionClass)
    {
        long start = System.nanoTime();

        if (expectedExceptionClass != null)
        {
            assertThatExceptionOfType(expectedExceptionClass).isThrownBy(runnable::run);
        }
        else
        {
            runnable.run();
        }

        return System.nanoTime() - start;
    }
}
