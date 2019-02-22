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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import net.jcip.annotations.NotThreadSafe;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * This test class provides a functional integration test for custom log format with Cassandra itself.
 * <p>
 * The format configuration is read when the plugin is started and has to be setup before the embedded Cassandra
 * is started. Therefor this test class cannot be run in the same process as the other integration tests (having
 * legacy log formatting).
 * <p>
 * The parameterized audit log format configured by this test looks like this:
 * {@code client=${CLIENT}, user=${USER}, status=${STATUS}, {?batch-id=${BATCH_ID}, ?}operation='${OPERATION}'}
 * <br>
 * Containing the mandatory parameters CLIENT/USER/STATUS/OPERATION and the optional parameter BATCH_ID.
 */
@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ITVerifyCustomLogFormat
{
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static Cluster cluster;
    private static Session session;

    @Captor
    ArgumentCaptor<ILoggingEvent> loggingEventCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        CassandraDaemonForAuditTest cdt = CassandraDaemonForAuditTest.getInstance();
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
    public void testCustomLogFormat()
    {
        // Given
        String createKeyspace = "CREATE KEYSPACE school WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false";
        String createTable = "CREATE TABLE school.students (name text PRIMARY KEY, grade text)";
        String insert = "INSERT INTO school.students (name, grade) VALUES ('Kalle', 'B')";
        String update = "UPDATE school.students SET grade = 'A' WHERE name = 'Kalle'";

        // When
        session.execute(new SimpleStatement(createKeyspace));
        session.execute(new SimpleStatement(createTable));

        BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
        batch.add(new SimpleStatement(insert));
        batch.add(new SimpleStatement(update));
        session.execute(batch);

        // Then
        verify(mockAuditAppender, atLeast(1)).doAppend(loggingEventCaptor.capture());
        List<String> logEntries = loggingEventCaptor.getAllValues().stream()
                                                    .map(ILoggingEvent::getFormattedMessage)
                                                    .flatMap(s -> Stream.of(s.split(UUID_REGEX)))
                                                    .collect(Collectors.toList());
        assertThat(logEntries)
        .contains(String.format("client=127.0.0.1, user=cassandra, status=ATTEMPT, operation='%s'", createKeyspace))
        .contains(String.format("client=127.0.0.1, user=cassandra, status=ATTEMPT, operation='%s'", createTable))
        .contains("client=127.0.0.1, user=cassandra, status=ATTEMPT, batch-id=", /*Batch-ID*/ String.format(", operation='%s'", insert))
        .contains("client=127.0.0.1, user=cassandra, status=ATTEMPT, batch-id=", /*Batch-ID*/ String.format(", operation='%s'", update));
    }
}
