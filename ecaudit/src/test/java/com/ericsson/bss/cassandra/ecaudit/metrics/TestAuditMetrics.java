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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditMetrics
{
    private static final String GROUP_NAME = "com.ericsson.bss.cassandra.ecaudit";
    private static final String METRIC_TYPE = "Audit";
    private static final String METRIC_SCOPE = "ClientRequests";
    private static final String METRIC_NAME_FILTER = "AuditFilter";
    private static final String METRIC_NAME_AUDIT = "Audit";

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
        CassandraMetricsRegistry.MetricName metric = createMetricName(METRIC_TYPE, METRIC_NAME_FILTER, METRIC_SCOPE);

        when(mockTimerFunction.apply(eq(metric))).thenReturn(mockTimer);

        AuditMetrics auditMetrics = new AuditMetrics(mockTimerFunction);
        verify(mockTimerFunction, times(1)).apply(eq(metric));

        auditMetrics.filterAuditRequest(999L, TimeUnit.NANOSECONDS);
        verify(mockTimer, times(1)).update(eq(999L), eq(TimeUnit.NANOSECONDS));
    }

    @Test
    public void testAuditRequestTiming()
    {
        Timer mockTimer = mock(Timer.class);
        CassandraMetricsRegistry.MetricName metric = createMetricName(METRIC_TYPE, METRIC_NAME_AUDIT, METRIC_SCOPE);

        when(mockTimerFunction.apply(eq(metric))).thenReturn(mockTimer);

        AuditMetrics auditMetrics = new AuditMetrics(mockTimerFunction);
        verify(mockTimerFunction, times(1)).apply(eq(metric));

        auditMetrics.auditRequest(999L, TimeUnit.NANOSECONDS);
        verify(mockTimer, times(1)).update(eq(999L), eq(TimeUnit.NANOSECONDS));
    }

    /**
     * Borrowed from org.apache.cassandra.metrics.DefaultNameFactory but with tailored group name.
     * @return a Cassandra metric name
     */
    private static CassandraMetricsRegistry.MetricName createMetricName(String type, String metricName, String scope)
    {
        return new CassandraMetricsRegistry.MetricName(GROUP_NAME, type, metricName, scope, createMBeanName(type, metricName, scope));
    }

    /**
     * Borrowed from org.apache.cassandra.metrics.DefaultNameFactory but with tailored group name.
     * @return the name of the mBean.
     */
    private static String createMBeanName(String type, String name, String scope)
    {
        final StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(GROUP_NAME);
        nameBuilder.append(":type=");
        nameBuilder.append(type);
        if (scope != null)
        {
            nameBuilder.append(",scope=");
            nameBuilder.append(scope);
        }
        if (name.length() > 0)
        {
            nameBuilder.append(",name=");
            nameBuilder.append(name);
        }
        return nameBuilder.toString();
    }
}
