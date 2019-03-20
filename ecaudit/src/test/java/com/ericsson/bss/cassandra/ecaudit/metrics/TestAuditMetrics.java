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
package com.ericsson.bss.cassandra.ecaudit.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.codahale.metrics.Timer;
import org.apache.cassandra.metrics.CassandraMetricsRegistry;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditMetrics
{
    private static final String METRIC_NAME_FILTER = "Filter";
    private static final String METRIC_NAME_LOG = "Log";

    @Mock
    private Function<CassandraMetricsRegistry.MetricName, Timer> mockTimerFunction;

    @Before
    public void init()
    {
        when(mockTimerFunction.apply(any())).thenReturn(mock(Timer.class));
    }

    @Test
    public void testFilterRequestTiming()
    {
        Timer mockTimer = mock(Timer.class);
        CassandraMetricsRegistry.MetricName metric = AuditMetrics.createMetricName(METRIC_NAME_FILTER);

        when(mockTimerFunction.apply(eq(metric))).thenReturn(mockTimer);

        AuditMetrics auditMetrics = new AuditMetrics(mockTimerFunction);
        verify(mockTimerFunction).apply(eq(metric));

        auditMetrics.filterAuditRequest(999L, TimeUnit.NANOSECONDS);
        verify(mockTimer).update(eq(999L), eq(TimeUnit.NANOSECONDS));
    }

    @Test
    public void testAuditRequestTiming()
    {
        Timer mockTimer = mock(Timer.class);
        CassandraMetricsRegistry.MetricName metric = AuditMetrics.createMetricName(METRIC_NAME_LOG);

        when(mockTimerFunction.apply(eq(metric))).thenReturn(mockTimer);

        AuditMetrics auditMetrics = new AuditMetrics(mockTimerFunction);
        verify(mockTimerFunction).apply(eq(metric));

        auditMetrics.logAuditRequest(999L, TimeUnit.NANOSECONDS);
        verify(mockTimer).update(eq(999L), eq(TimeUnit.NANOSECONDS));
    }

    @Test
    public void testCreateMetricName()
    {
        String groupName = "com.ericsson.bss.cassandra.ecaudit";
        String typeName = "Audit";
        String metricName = "test";
        String scopeName = null;
        String mbeanName = "com.ericsson.bss.cassandra.ecaudit:type=Audit,name=test";
        CassandraMetricsRegistry.MetricName expectedMetricName = new CassandraMetricsRegistry.MetricName(groupName,
                                                                                                         typeName,
                                                                                                         metricName,
                                                                                                         scopeName,
                                                                                                         mbeanName);

        CassandraMetricsRegistry.MetricName actualMetricName = AuditMetrics.createMetricName("test");
        assertThat(actualMetricName).isEqualToComparingFieldByField(expectedMetricName);
    }
}
