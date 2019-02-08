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
package com.ericsson.bss.cassandra.ecaudit.integration;

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
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import net.jcip.annotations.NotThreadSafe;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.ericsson.bss.cassandra.ecaudit.integration.ITVerifyAudit.UUID_REGEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * This test class provides a functional integration test with Cassandra itself.
 * <p>
 * This is achieved by starting an embedded Cassandra server where the audit plug-in is used. Then each test case send
 * different requests and capture and verify that expected audit entries are produced.
 * <p>
 * This class also works as a safe guard to changes on the public API of the plug-in. The plug-in has three different
 * interfaces that together make out its public API. It is Cassandra itself, the configuration, and the audit messages
 * sent to the supported log back ends. When a change is necessary here it indicates that the major or minor version
 * should be bumped as well. This class is mostly focused to verify that a correct audit logs are created based on a
 * specific configuration.
 */
@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ITVerifyCustomLogFormat
{
    private static CassandraDaemonForAuditTest cdt;
    private static Cluster cluster;
    private static Session session;

    @Captor
    ArgumentCaptor<ILoggingEvent> loggingEventCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        cdt = new CassandraDaemonForAuditTest("integration_audit_custom_format.yaml");
        cdt.activate();
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
        ResultSet execute = session.execute(batch);

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
