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
package com.ericsson.bss.cassandra.ecaudit.integration.custom;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.LogTimingStrategy;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class ITVerifyPostLogging
{
    private static final String CUSTOM_LOGGER_NAME = "ECAUDIT_CUSTOM";
    private static CqlSession session;
    private static AuditLogger customLogger;

    @Captor
    private ArgumentCaptor<ILoggingEvent> loggingEventCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;
    private static CassandraDaemonForAuditTest cdt;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        cdt = CassandraDaemonForAuditTest.getInstance();
        session = cdt.createSession();

        // Configure logger with custom/simple format
        Map<String, String> configParameters = Collections.singletonMap("log_format", "Status=${STATUS}|User=${USER}|{?Batch-ID=${BATCH_ID}|?}Operation=${OPERATION}");
        customLogger = new Slf4jAuditLogger(configParameters, CUSTOM_LOGGER_NAME);
        // Add custom logger
        AuditAdapter.getInstance().getAuditor().addLogger(customLogger);
        // Set post logging strategy
        AuditAdapter.getInstance().getAuditor().setLogTimingStrategy(LogTimingStrategy.POST_LOGGING_STRATEGY);
    }

    @Before
    public void before()
    {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(CUSTOM_LOGGER_NAME).addAppender(mockAuditAppender);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockAuditAppender);
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(CUSTOM_LOGGER_NAME).detachAppender(mockAuditAppender);
    }

    @AfterClass
    public static void afterClass()
    {
        AuditAdapter.getInstance().getAuditor().removeLogger(customLogger);
        AuditAdapter.getInstance().getAuditor().setLogTimingStrategy(LogTimingStrategy.PRE_LOGGING_STRATEGY);
        session.close();
    }

    @Test
    public void testSuccessfulUserAuthentication()
    {
        reset(mockAuditAppender);

        try (CqlSession privateSession = cdt.createSession("cassandra", "cassandra"))
        {
            assertThat(privateSession.isClosed()).isFalse();
        }

        List<String> logEntries = getLogEntries();
        assertThat(logEntries).contains("Status=SUCCEEDED|User=cassandra|Operation=Authentication succeeded");
        logEntries.forEach(s -> assertThat(s).as("No attempts should be logged").doesNotContain("Status=ATTEMPT"));
    }

    @Test(expected = AllNodesFailedException.class)
    public void testFailedUserAuthentication()
    {
        reset(mockAuditAppender);

        try (CqlSession privateSession = cdt.createSession("unknown", "secret"))
        {
            assertThat(privateSession.isClosed()).isFalse();
        }
        finally
        {
            List<String> logEntries = getLogEntries();
            assertThat(logEntries).contains("Status=FAILED|User=unknown|Operation=Authentication failed");
            logEntries.forEach(s -> assertThat(s).as("No attempts should be logged").doesNotContain("Status=ATTEMPT"));
        }
    }

    @Test
    public void testSuccessfulStatements()
    {
        // Given
        givenTable("keyspace1", "table1");
        reset(mockAuditAppender);

        BatchStatement batchStatement = BatchStatement.builder(DefaultBatchType.UNLOGGED)
                .addStatement(SimpleStatement.newInstance("INSERT INTO keyspace1.table1 (key, value) VALUES (42, 'Kalle')"))
                .addStatement(SimpleStatement.newInstance("UPDATE keyspace1.table1 SET value = 'Anka' WHERE key = 42"))
                .build();

        String batch = "BEGIN UNLOGGED BATCH USING TIMESTAMP ? " +
                       "INSERT INTO keyspace1.table1 (key, value) VALUES (?, ?); " +
                       "UPDATE keyspace1.table1 SET value = 'New' WHERE key = ?; " +
                       "APPLY BATCH;";
        String nonPreparedBatch = "BEGIN UNLOGGED BATCH " +
                                  "INSERT INTO keyspace1.table1 (key, value) VALUES (44, 'Kalle'); " +
                                  "UPDATE keyspace1.table1 SET value = 'Anka' WHERE key = 44;" +
                                  "APPLY BATCH;";

        PreparedStatement preparedBatch = session.prepare(batch);

        // When
        session.execute("SELECT * from keyspace1.table1");
        session.execute(batchStatement);
        PreparedStatement preparedStatement = session.prepare("SELECT * FROM keyspace1.table1 WHERE key = ?");
        session.execute(preparedStatement.bind(42));
        session.execute(preparedBatch.bind(1234L, 43, "Pelle", 43));
        session.execute(nonPreparedBatch);
        // Then
        List<String> entries = getLogEntries().stream()
                                              .map(this::replaceBatchIdWithConstant)
                                              .collect(Collectors.toList());
        assertThat(entries).containsOnly(
            "Status=SUCCEEDED|User=cassandra|Operation=SELECT * from keyspace1.table1",
            "Status=SUCCEEDED|User=cassandra|Batch-ID=<uuid>|Operation=INSERT INTO keyspace1.table1 (key, value) VALUES (42, 'Kalle')",
            "Status=SUCCEEDED|User=cassandra|Batch-ID=<uuid>|Operation=UPDATE keyspace1.table1 SET value = 'Anka' WHERE key = 42",
            "Status=SUCCEEDED|User=cassandra|Operation=SELECT * FROM keyspace1.table1 WHERE key = ?[42]",
            "Status=SUCCEEDED|User=cassandra|Operation=" + batch + "[1234, 43, 'Pelle', 43]",
            "Status=SUCCEEDED|User=cassandra|Operation=" + nonPreparedBatch
        );
    }

    private String replaceBatchIdWithConstant(String s)
    {
        return s.replaceAll("Batch-ID=[0-9A-Fa-f-]+", "Batch-ID=<uuid>");
    }

    @Test
    public void testFailedSimpleStatements()
    {
        // Given
        givenTable("keyspace1", "table1");
        reset(mockAuditAppender);

        // When
        executeAndIgnoreException(() -> session.execute("SELECT * from NON.EXISTING_TABLE1"));
        executeAndIgnoreException(() -> session.execute("INSERT INTO keyspace1.table1 (key, value) VALUES (42)"));

        // Then
        List<String> entries = getLogEntries().stream()
                                              .map(this::replaceBatchIdWithConstant)
                                              .collect(Collectors.toList());
        assertThat(entries).containsOnly(
        "Status=FAILED|User=cassandra|Operation=SELECT * from NON.EXISTING_TABLE1",
        "Status=FAILED|User=cassandra|Operation=INSERT INTO keyspace1.table1 (key, value) VALUES (42)"
        );
    }

    @Test
    public void testFailedBatchStatements()
    {
        // Given
        givenTable("keyspace1", "table1");
        reset(mockAuditAppender);

        // When
        PreparedStatement preparedInsert = session.prepare("INSERT INTO keyspace1.table1 (key, value) VALUES (?, ?)");
        BatchStatement batchInserts = BatchStatement.builder(DefaultBatchType.UNLOGGED)
                .addStatement(preparedInsert.bind(42, "Kalle"))
                .addStatement(preparedInsert.bind()) // No values -> will fail!
                .build();
        executeAndIgnoreException(() -> session.execute(batchInserts));

        // Then
        List<String> entries = getLogEntries().stream()
                                              .map(this::replaceBatchIdWithConstant)
                                              .collect(Collectors.toList());
        assertThat(entries).containsOnly(
        "Status=FAILED|User=cassandra|Batch-ID=<uuid>|Operation=INSERT INTO keyspace1.table1 (key, value) VALUES (?, ?)[42, 'Kalle']",
        "Status=FAILED|User=cassandra|Batch-ID=<uuid>|Operation=INSERT INTO keyspace1.table1 (key, value) VALUES (?, ?)[null, '']"
        );
    }

    @Test
    public void testFailedNonPreparedBatchStatements()
    {
        // Given
        givenTable("keyspace1", "table1");
        reset(mockAuditAppender);

        // When
        String batch = "BEGIN UNLOGGED BATCH " +
                       "INSERT INTO keyspace1.table1 (key, value) VALUES (42); " +
                       "APPLY BATCH;";

        executeAndIgnoreException(() -> session.execute(batch)); // To few values -> will fail

        // Then
        List<String> entries = getLogEntries().stream()
                                              .map(this::replaceBatchIdWithConstant)
                                              .collect(Collectors.toList());
        assertThat(entries).containsOnly(
        "Status=FAILED|User=cassandra|Operation=" + batch
        );
    }

    @Test
    public void testFailedPreparedBatchStatements()
    {
        // Given
        givenTable("keyspace1", "table1");
        reset(mockAuditAppender);

        // When
        String batch = "BEGIN UNLOGGED BATCH USING TIMESTAMP ? " +
                       "INSERT INTO keyspace1.table1 (key, value) VALUES (?, ?); " +
                       "UPDATE keyspace1.table1 SET value = 'New' WHERE key=?; " +
                       "APPLY BATCH;";
        PreparedStatement preparedBatch = session.prepare(batch);

        executeAndIgnoreException(() -> session.execute(preparedBatch.bind(1234L, 43))); // To few values -> will fail

        // Then
        List<String> entries = getLogEntries().stream()
                                              .map(this::replaceBatchIdWithConstant)
                                              .collect(Collectors.toList());
        assertThat(entries).containsOnly(
        "Status=FAILED|User=cassandra|Operation=" + batch + "[1234, 43, '', null]"
        );
    }

    @Test
    public void testFailedPreparedStatement()
    {
        // Given
        givenTable("keyspace1", "table1");
        reset(mockAuditAppender);

        // When
        PreparedStatement preparedStatement = session.prepare("SELECT * FROM keyspace1.table1 WHERE key = ?");
        executeAndIgnoreException(() -> session.execute(preparedStatement.bind())); // No values -> will fail!

        // Then
        List<String> entries = getLogEntries().stream()
                                              .map(this::replaceBatchIdWithConstant)
                                              .collect(Collectors.toList());
        assertThat(entries).containsOnly(
        "Status=FAILED|User=cassandra|Operation=SELECT * FROM keyspace1.table1 WHERE key = ?[null]"
        );
    }

    private void givenTable(String keyspace, String table)
    {
        session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false");
        session.execute("CREATE TABLE IF NOT EXISTS " + keyspace + "." + table + " (key int PRIMARY KEY, value text)");
    }

    private void executeAndIgnoreException(Runnable runnable)
    {
        try
        {
            runnable.run();
            fail("Expected exception to be thrown");
        }
        catch (Exception e)
        {
            // Do nothing
        }
    }

    private List<String> getLogEntries()
    {
        verify(mockAuditAppender, atLeast(1)).doAppend(loggingEventCaptor.capture());
        return loggingEventCaptor.getAllValues()
                                 .stream()
                                 .map(ILoggingEvent::getFormattedMessage)
                                 .collect(Collectors.toList());
    }
}
