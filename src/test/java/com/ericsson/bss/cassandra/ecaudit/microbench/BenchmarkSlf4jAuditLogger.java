package com.ericsson.bss.cassandra.ecaudit.microbench;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.Status;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
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

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Benchmark)
public class BenchmarkSlf4jAuditLogger
{
    private AuditEntry auditEntry;

    @Setup(Level.Invocation)
    public void setup() throws Exception
    {
        auditEntry = AuditEntry.newBuilder()
                               .client(InetAddress.getLocalHost())
                               .user("cassandra")
                               .batch(UUID.randomUUID())
                               .status(Status.ATTEMPT)
                               .operation(new SimpleAuditOperation("SELECT * from dummy.table"))
                               .build();
    }

    @Benchmark
    public void benchmarkGetLogString()
    {
        Slf4jAuditLogger.getLogString(auditEntry);
    }
}
