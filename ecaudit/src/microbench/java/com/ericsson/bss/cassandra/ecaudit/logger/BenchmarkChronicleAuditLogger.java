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

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.entry.LazyUUID;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Simple benchmark for Chronicle logger.
 *
 * Run this directly in IntelliJ (if you have a working JMH plugin).
 *
 * Or, run in from the command line (with more accurate results)
 * - mvn package -DskipTests
 * - mvn dependency:unpack-dependencies
 * - java -cp target/classes:target/test-classes:target/dependency com.ericsson.bss.cassandra.ecaudit.logger.BenchmarkChronicleAuditLogger
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Benchmark)
public class BenchmarkChronicleAuditLogger
{
    private final ChronicleAuditLogger logger;
    private AuditEntry auditEntry;

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                      .include(BenchmarkChronicleAuditLogger.class.getSimpleName())
                      .forks(1)
                      .build();

        new Runner(opt).run();
    }

    public BenchmarkChronicleAuditLogger()
    {
        File tempDir = Files.createTempDir();
        tempDir.deleteOnExit();

        Map<String, String> config = ImmutableMap.of("log_dir", tempDir.getPath());

        logger = new ChronicleAuditLogger(config);
    }

    @Setup(Level.Iteration)
    public void setup() throws Exception
    {
        auditEntry = AuditEntry.newBuilder()
                               .timestamp(System.currentTimeMillis())
                               .client(new InetSocketAddress(InetAddress.getLocalHost(), 678))
                               .coordinator(InetAddress.getLocalHost())
                               .user("cassandra")
                               .batch(LazyUUID.fromUuid(UUID.randomUUID()))
                               .status(Status.ATTEMPT)
                               .operation(new SimpleAuditOperation("SELECT * from dummy.table"))
                               .build();
    }

    @Benchmark
    public void benchmarkGetLogString()
    {
        logger.log(auditEntry);
    }
}
