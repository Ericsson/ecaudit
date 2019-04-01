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
package com.ericsson.bss.cassandra.ecaudit.eclog;

import java.io.PrintStream;

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;

class LogPrinter
{
    private static final long DEFAULT_POLL_INTERVAL_MS = 1000;

    private final ToolOptions toolOptions;
    private final PrintStream out;
    private final long pollIntervalMs;

    LogPrinter(ToolOptions toolOptions)
    {
        this(toolOptions, System.out, DEFAULT_POLL_INTERVAL_MS);
    }

    // Visible for testing
    LogPrinter(ToolOptions toolOptions, PrintStream out, long pollIntervalMs)
    {
        this.toolOptions = toolOptions;
        this.out = out;
        this.pollIntervalMs = pollIntervalMs;
    }

    void print(QueueReader queueReader)
    {
        long printedRecords = 0;
        while (true)
        {
            while (isEligibleForPrint(queueReader, printedRecords))
            {
                AuditRecord auditEntry = queueReader.nextRecord();

                StringBuilder builder = new StringBuilder();
                builder.append(auditEntry.getTimestamp()).append('|');
                builder.append(auditEntry.getClientAddress().getHostAddress()).append('|');
                builder.append(auditEntry.getUser()).append('|');
                builder.append(auditEntry.getStatus()).append('|');
                auditEntry.getBatchId()
                          .ifPresent(bid -> builder.append(bid).append('|'));

                builder.append(auditEntry.getOperation().getOperationString());
                out.println(builder.toString());

                printedRecords++;
            }

            if (isLimitReached(printedRecords))
            {
                return;
            }

            if (!toolOptions.follow())
            {
                return;
            }

            try
            {
                Thread.sleep(pollIntervalMs);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean isEligibleForPrint(QueueReader queueReader, long printedRecords)
    {
        return queueReader.hasRecordAvailable() && !isLimitReached(printedRecords);
    }

    private boolean isLimitReached(long printedRecords)
    {
        return toolOptions.limit().filter(limit -> printedRecords >= limit).isPresent();
    }
}
