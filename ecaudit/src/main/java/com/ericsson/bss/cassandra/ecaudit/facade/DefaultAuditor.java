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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.LogTimingStrategy;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.metrics.AuditMetrics;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.AuditObfuscator;

/**
 * Default implementation of {@link Auditor} which will do following task required to auditing:
 * <p>
 * - Filtering populated {@link AuditEntry} instance using {@link AuditFilter}
 * - Obfuscation on filtered using {@link AuditObfuscator}
 * - Write log entry using {@link AuditLogger}
 */
public class DefaultAuditor implements Auditor
{
    private final List<AuditLogger> loggers = new ArrayList<>();
    private final AuditFilter filter;
    private final AuditObfuscator obfuscator;
    private final AuditMetrics auditMetrics;
    private LogTimingStrategy logTimingStrategy;
	
	private static final Logger LOG = LoggerFactory.getLogger(DefaultAuditor.class);

    public DefaultAuditor(AuditLogger logger, AuditFilter filter, AuditObfuscator obfuscator, LogTimingStrategy logTimingStrategy)
    {
        this(logger, filter, obfuscator, new AuditMetrics(), logTimingStrategy);
    }

    DefaultAuditor(AuditLogger logger, AuditFilter filter, AuditObfuscator obfuscator, AuditMetrics auditMetrics, LogTimingStrategy logTimingStrategy)
    {
        loggers.add(logger);
        this.filter = filter;
        this.obfuscator = obfuscator;
        this.auditMetrics = auditMetrics;
        this.logTimingStrategy = logTimingStrategy;
    }

    @Override
    public void setup()
    {
        filter.setup();
    }

    @Override
    public void audit(AuditEntry logEntry)
    {
        if (shouldAudit(logEntry))
        {
            AuditEntry obfuscatedEntry = obfuscator.obfuscate(logEntry);
            performAudit(obfuscatedEntry);
        }
    }

    private boolean shouldAudit(AuditEntry logEntry)
    {
        long start = System.nanoTime();
        try
        {
            return !filter.isWhitelisted(logEntry);
        }
        catch(RuntimeException e) {
        	LOG.error("cannot fetch whitelist user, cause={}, message={}", e.getCause(), e.getMessage());
        	return true;
        }
        finally
        {
            long end = System.nanoTime();
            auditMetrics.filterAuditRequest(end - start, TimeUnit.NANOSECONDS);
        }
    }

    private void performAudit(AuditEntry logEntry)
    {
        long start = System.nanoTime();
        try
        {
            loggers.forEach(logger -> logger.log(logEntry));
        }
        finally
        {
            long end = System.nanoTime();
            auditMetrics.logAuditRequest(end - start, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public boolean shouldLogForStatus(Status status)
    {
        return logTimingStrategy.shouldLogForStatus(status);
    }

    @Override
    public boolean shouldLogFailedBatchSummary()
    {
        return logTimingStrategy.shouldLogFailedBatchSummary();
    }

    @Override
    public void setLogTimingStrategy(LogTimingStrategy logTimingStrategy)
    {
        this.logTimingStrategy = logTimingStrategy;
    }

    @Override
    public void addLogger(AuditLogger logger)
    {
        loggers.add(logger);
    }

    @Override
    public void removeLogger(AuditLogger logger)
    {
        loggers.remove(logger);
    }
}
