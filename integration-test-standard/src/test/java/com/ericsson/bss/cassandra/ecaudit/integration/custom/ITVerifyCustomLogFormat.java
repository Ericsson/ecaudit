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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;
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
 * {@code ${TIMESTAMP}-> client=${CLIENT_IP}, user=${USER}, status=${STATUS}, {?batch-id=${BATCH_ID}, ?}operation='${OPERATION}'}
 * <br>
 * The time format configured by this test looks like this:
 * {@code "yyyy-MM-dd HH:mm:ss.SSS"}
 * <br>
 * Containing the mandatory parameters TIMESTAMP/CLIENT_IP/COORDINATOR_IP/USER/STATUS/OPERATION and the optional parameters CLIENT_PORT/BATCH_ID.
 */
@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ITVerifyCustomLogFormat
{
    private static final String CUSTOM_LOGGER_NAME = "ECAUDIT_CUSTOM";

    private static final String TIMESTAMP_REGEX = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}";  // correnspons to "yyyy-MM-dd HH:mm:ss.SSS" timestamp
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static Cluster cluster;
    private static Session session;
    private static AuditLogger customLogger;

    @Captor
    private ArgumentCaptor<ILoggingEvent> loggingEventCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        CassandraDaemonForAuditTest cdt = CassandraDaemonForAuditTest.getInstance();
        cluster = cdt.createCluster();
        session = cluster.connect();

        // Configure logger with custom format
        Map<String, String> configParameters = new HashMap<>();
        String customLogFormat = "${TIMESTAMP}-> client=${CLIENT_IP}{?:${CLIENT_PORT}?}, coordinator=${COORDINATOR_IP}, user=${USER}, status=${STATUS}, operation='${OPERATION_NAKED}'{?, batch-id=${BATCH_ID}?}";
        configParameters.put("log_format", customLogFormat);
        configParameters.put("time_format", "yyyy-MM-dd HH:mm:ss.SSS");
        configParameters.put("time_zone", "UTC");
        customLogger = new Slf4jAuditLogger(configParameters, CUSTOM_LOGGER_NAME);
        // Add custom logger
        AuditAdapter.getInstance().getAuditor().addLogger(customLogger);
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
        session.close();
        cluster.close();
    }

    @Test
    public void testCustomLogFormat()
    {
        // Given
        String createKeyspace = "CREATE KEYSPACE school WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false";
        String createTable = "CREATE TABLE school.students (name text PRIMARY KEY, grade text)";
        String insert = "INSERT INTO school.students (name, grade) VALUES (?, ?)";
        String update = "UPDATE school.students SET grade = 'A' WHERE name = 'Kalle'";
        String batch = "BEGIN UNLOGGED BATCH USING TIMESTAMP ? " +
                       "INSERT INTO school.students (name, grade) VALUES (?, ?); " +
                       "UPDATE school.students SET grade = 'B' WHERE name = ?; " +
                       "APPLY BATCH;";
        // When
        session.execute(new SimpleStatement(createKeyspace));
        session.execute(new SimpleStatement(createTable));
        Instant now = Instant.now();

        PreparedStatement preparedInsert = session.prepare(insert);
        BatchStatement batchStatement = new BatchStatement(BatchStatement.Type.UNLOGGED);
        batchStatement.add(preparedInsert.bind("Kalle", "B"));
        batchStatement.add(new SimpleStatement(update));
        session.execute(batchStatement);

        PreparedStatement preparedBatch = session.prepare(batch);
        session.execute(preparedBatch.bind(1234L, "Pelle", "A", "Kalle"));

        // Then
        verify(mockAuditAppender, atLeast(1)).doAppend(loggingEventCaptor.capture());
        List<String> logEntries = loggingEventCaptor.getAllValues().stream()
                                                    .map(ILoggingEvent::getFormattedMessage)
                                                    .collect(Collectors.toList());

        assertListContainsPattern(logEntries, TIMESTAMP_REGEX + "-> client=127.0.0.1:[0-9]*, coordinator=127.0.0.1, user=cassandra, status=ATTEMPT, operation='" + Pattern.quote(createKeyspace));
        assertListContainsPattern(logEntries, TIMESTAMP_REGEX + "-> client=127.0.0.1:[0-9]*, coordinator=127.0.0.1, user=cassandra, status=ATTEMPT, operation='" + Pattern.quote(createTable));
        assertListContainsPattern(logEntries, TIMESTAMP_REGEX + "-> client=127.0.0.1:[0-9]*, coordinator=127.0.0.1, user=cassandra, status=ATTEMPT, operation='" + Pattern.quote(insert) + "', batch-id=" + UUID_REGEX);
        assertListContainsPattern(logEntries, TIMESTAMP_REGEX + "-> client=127.0.0.1:[0-9]*, coordinator=127.0.0.1, user=cassandra, status=ATTEMPT, operation='" + Pattern.quote(update) + "', batch-id=" + UUID_REGEX);
        assertListContainsPattern(logEntries, TIMESTAMP_REGEX + "-> client=127.0.0.1:[0-9]*, coordinator=127.0.0.1, user=cassandra, status=ATTEMPT, operation='" + Pattern.quote(batch));

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);// With second resolution...
        String expectedTimestampString = dateTimeFormatter.format(now);
        assertListContainsPattern(logEntries, expectedTimestampString);
    }

    private void assertListContainsPattern(List<String> list, String pattern)
    {
        Pattern p = Pattern.compile(pattern);
        assertThat(list).as("Pattern: %s", p)
                        .anyMatch(entry -> p.matcher(entry).find());
    }
}
