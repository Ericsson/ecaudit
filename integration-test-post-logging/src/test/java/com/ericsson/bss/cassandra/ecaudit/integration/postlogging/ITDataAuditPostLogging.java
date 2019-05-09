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
package com.ericsson.bss.cassandra.ecaudit.integration.postlogging;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.AuthenticationException;
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
public class ITDataAuditPostLogging
{
    private static Cluster cluster;
    private static Session session;

    @Captor
    private ArgumentCaptor<ILoggingEvent> loggingEventCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;
    private static CassandraDaemonForAuditTest cdt;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        cdt = CassandraDaemonForAuditTest.getInstance();
        cluster = cdt.createCluster();
        session = cluster.connect();
    }

    @Before
    public void before()
    {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(Slf4jAuditLogger.AUDIT_LOGGER_NAME).addAppender(mockAuditAppender);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockAuditAppender);
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(Slf4jAuditLogger.AUDIT_LOGGER_NAME).detachAppender(mockAuditAppender);
    }

    @AfterClass
    public static void afterClass()
    {
        session.close();
        cluster.close();
    }

    @Test
    public void testSuccessfulUserAuthentication()
    {
        reset(mockAuditAppender);

        try (Cluster privateCluster = cdt.createCluster("cassandra", "cassandra");
             Session privateSession = privateCluster.connect())
        {
            assertThat(privateSession.isClosed()).isFalse();
        }

        List<String> logEntries = getLogEntries();
        assertThat(logEntries).contains("Status=SUCCEEDED|User=cassandra|Operation=Authentication succeeded");
        logEntries.forEach(s -> assertThat(s).as("No attempts should be logged").doesNotContain("Status=ATTEMPT"));
    }

    @Test(expected = AuthenticationException.class)
    public void testFailedUserAuthentication()
    {
        reset(mockAuditAppender);

        try (Cluster privateCluster = cdt.createCluster("unknown", "secret");
             Session privateSession = privateCluster.connect())
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
        givenTable("school", "students");
        reset(mockAuditAppender);

        BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
        batch.add(new SimpleStatement("INSERT INTO school.students (key, value) VALUES (42, 'Kalle')"));
        batch.add(new SimpleStatement("UPDATE school.students SET value = 'Anka' WHERE key = 42"));
        // When
        session.execute(new SimpleStatement("SELECT * from school.students"));
        session.execute(batch);
        PreparedStatement preparedStatement = session.prepare("SELECT * FROM school.students WHERE key = ?");
        session.execute(preparedStatement.bind(42));
        // Then
        List<String> entries = getLogEntries().stream()
                                              .map(this::replaceBatchIdWithConstant)
                                              .collect(Collectors.toList());
        assertThat(entries).containsOnly(
            "Status=SUCCEEDED|User=cassandra|Operation=SELECT * from school.students",
            "Status=SUCCEEDED|User=cassandra|Batch-ID=<uuid>|Operation=INSERT INTO school.students (key, value) VALUES (42, 'Kalle')",
            "Status=SUCCEEDED|User=cassandra|Batch-ID=<uuid>|Operation=UPDATE school.students SET value = 'Anka' WHERE key = 42",
            "Status=SUCCEEDED|User=cassandra|Operation=SELECT * FROM school.students WHERE key = ?[42]"
        );
    }

    private String replaceBatchIdWithConstant(String s)
    {
        return s.replaceAll("Batch-ID=[0-9A-Fa-f-]+", "Batch-ID=<uuid>");
    }

    @Test
    public void testFailedStatements()
    {
        // Given
        givenTable("school", "students");
        reset(mockAuditAppender);

        // When
        executeAndIgnoreException(() -> session.execute(new SimpleStatement("SELECT * from NON.EXISTING_TABLE1")));

        BatchStatement batchInserts = new BatchStatement(BatchStatement.Type.UNLOGGED);
        PreparedStatement preparedInsert = session.prepare("INSERT INTO school.students (key, value) VALUES (?, ?)");
        batchInserts.add(preparedInsert.bind(42, "Kalle"));
        batchInserts.add(preparedInsert.bind()); // No values -> will fail!
        executeAndIgnoreException(() -> session.execute(batchInserts));

        PreparedStatement preparedStatement = session.prepare("SELECT * FROM school.students WHERE key = ?");
        executeAndIgnoreException(() -> session.execute(preparedStatement.bind())); // No values -> will fail!
        // Then
        List<String> entries = getLogEntries().stream()
                                              .map(this::replaceBatchIdWithConstant)
                                              .collect(Collectors.toList());
        assertThat(entries).containsOnly(
            "Status=FAILED|User=cassandra|Operation=SELECT * from NON.EXISTING_TABLE1",
            "Status=FAILED|User=cassandra|Batch-ID=<uuid>|Operation=INSERT INTO school.students (key, value) VALUES (?, ?)[42, 'Kalle']",
            "Status=FAILED|User=cassandra|Batch-ID=<uuid>|Operation=INSERT INTO school.students (key, value) VALUES (?, ?)[null, '']",
            "Status=FAILED|User=cassandra|Operation=SELECT * FROM school.students WHERE key = ?[null]"
        );

    }

    private void givenTable(String keyspace, String table)
    {
        session.execute(new SimpleStatement(
        "CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false"));
        session.execute(new SimpleStatement(
        "CREATE TABLE IF NOT EXISTS " + keyspace + "." + table + " (key int PRIMARY KEY, value text)"));
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
