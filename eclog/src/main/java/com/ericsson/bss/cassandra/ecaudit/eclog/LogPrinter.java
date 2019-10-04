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
import java.net.InetAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.ericsson.bss.cassandra.ecaudit.common.formatter.LogMessageFormatter;
import com.ericsson.bss.cassandra.ecaudit.common.record.StoredAuditRecord;
import com.ericsson.bss.cassandra.ecaudit.eclog.config.EcLogYamlConfig;
import com.ericsson.bss.cassandra.ecaudit.eclog.config.EcLogYamlConfigLoader;

class LogPrinter
{
    private static final long DEFAULT_POLL_INTERVAL_MS = 1000;

    private final ToolOptions toolOptions;
    private final PrintStream out;
    private final long pollIntervalMs;
    private final LogMessageFormatter<StoredAuditRecord> messageFormatter;

    LogPrinter(ToolOptions toolOptions)
    {
        this(toolOptions, System.out, DEFAULT_POLL_INTERVAL_MS, EcLogYamlConfigLoader.load(toolOptions));
    }

    // Visible for testing
    LogPrinter(ToolOptions toolOptions, PrintStream out, long pollIntervalMs, EcLogYamlConfig config)
    {
        this.toolOptions = toolOptions;
        this.out = out;
        this.pollIntervalMs = pollIntervalMs;
        messageFormatter = LogMessageFormatter.<StoredAuditRecord>builder()
                           .format(config.getLogFormat())
                           .anchor("%s")
                           .escape("%", "%%")
                           .availableFields(getAvailableFieldFunctionMap(config))
                           .build();
    }

    static Map<String, Function<StoredAuditRecord, Object>> getAvailableFieldFunctionMap(EcLogYamlConfig config)
    {
        Map<String, Function<StoredAuditRecord, Object>> availableFields = new HashMap<>();
        availableFields.put("CLIENT_IP", entry -> entry.getClientAddress().map(InetAddress::getHostAddress).orElse(null));
        availableFields.put("CLIENT_PORT", entry -> entry.getClientPort().orElse(null));
        availableFields.put("COORDINATOR_IP", entry -> entry.getCoordinatorAddress().map(InetAddress::getHostAddress).orElse(null));
        availableFields.put("USER", entry -> entry.getUser().orElse(null));
        availableFields.put("BATCH_ID", entry -> entry.getBatchId().orElse(null));
        availableFields.put("STATUS", entry -> entry.getStatus().orElse(null));
        availableFields.put("OPERATION", entry -> entry.getOperation().orElse(null));
        availableFields.put("OPERATION_NAKED", entry -> entry.getNakedOperation().orElse(null));
        availableFields.put("TIMESTAMP", entry -> entry.getTimestamp().map(formatTimestamp(config)).orElse(null));
        return Collections.unmodifiableMap(availableFields);
    }

    private static Function<Long, String> formatTimestamp(EcLogYamlConfig config)
    {
        return timestamp -> config.getTimeFormatter()
                                  .map(formatter -> formatter.format(Instant.ofEpochMilli(timestamp)))
                                  .orElse(String.valueOf(timestamp));
    }

    void print(QueueReader queueReader)
    {
        long printedRecords = 0;
        while (true)
        {
            while (isEligibleForPrint(queueReader, printedRecords))
            {
                StoredAuditRecord auditEntry = queueReader.nextRecord();
                String logText = String.format(messageFormatter.getLogTemplate(), messageFormatter.getArgumentsForEntry(auditEntry));
                out.println(logText);

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
