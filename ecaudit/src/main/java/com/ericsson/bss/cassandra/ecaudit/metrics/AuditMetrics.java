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

import com.codahale.metrics.Timer;
import org.apache.cassandra.metrics.CassandraMetricsRegistry;

/**
 * Helper class to create and update audit metrics.
 */
public class AuditMetrics
{
    private static final String GROUP_NAME = "com.ericsson.bss.cassandra.ecaudit";
    private static final String METRIC_TYPE = "Audit";
    private static final String METRIC_SCOPE = "ClientRequests";
    private static final String METRIC_NAME_FILTER = "AuditFilter";
    private static final String METRIC_NAME_AUDIT = "Audit";

    private final Timer auditFilterTimer;
    private final Timer auditTimer;

    public AuditMetrics()
    {
        this(CassandraMetricsRegistry.Metrics::timer);
    }

    AuditMetrics(Function<CassandraMetricsRegistry.MetricName, Timer> timerFunction)
    {
        auditFilterTimer = timerFunction.apply(createMetricName(METRIC_TYPE, METRIC_NAME_FILTER, METRIC_SCOPE));
        auditTimer = timerFunction.apply(createMetricName(METRIC_TYPE, METRIC_NAME_AUDIT, METRIC_SCOPE));
    }

    /**
     * Add timing for filtering a request for audit
     *
     * @param time the time spent filtering
     * @param timeUnit the time unit of the provided time
     */
    public void filterAuditRequest(long time, TimeUnit timeUnit)
    {
        auditFilterTimer.update(time, timeUnit);
    }

    /**
     * Add timing for audit logging of a request.
     *
     * @param time the time spent logging
     * @param timeUnit the time unit of the provided time
     */
    public void auditRequest(long time, TimeUnit timeUnit)
    {
        auditTimer.update(time, timeUnit);
    }

    /**
     * Copied from org.apache.cassandra.metrics.DefaultNameFactory but with tailored group name.
     * @return a Cassandra metric name
     */
    private static CassandraMetricsRegistry.MetricName createMetricName(String type, String metricName, String scope)
    {
        return new CassandraMetricsRegistry.MetricName(GROUP_NAME, type, metricName, scope, createMBeanName(type, metricName, scope));
    }

    /**
     * Copied from org.apache.cassandra.metrics.DefaultNameFactory but with tailored group name.
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
