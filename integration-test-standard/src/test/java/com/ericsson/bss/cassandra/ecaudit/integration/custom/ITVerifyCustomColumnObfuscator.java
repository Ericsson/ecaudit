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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Collections;
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
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.entry.obfuscator.ColumnObfuscator;
import com.ericsson.bss.cassandra.ecaudit.entry.obfuscator.HideBlobsObfuscator;
import com.ericsson.bss.cassandra.ecaudit.entry.obfuscator.ShowAllObfuscator;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;
import net.jcip.annotations.NotThreadSafe;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * This test class provides a functional integration test for custom column obfuscator log format with Cassandra itself.
 * <p>
 * The format configuration is read when the plugin is started and has to be setup before the embedded Cassandra
 * is started. Therefor this test class cannot be run in the same process as the other integration tests (having
 * legacy log formatting).
 * <p>
 * Use simple audit log format - with only OPERATION, to make assertions simpler:
 * {@code operation='${OPERATION}'}
 * <br>
 */
@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ITVerifyCustomColumnObfuscator
{
    private static final String CUSTOM_LOGGER_NAME = "ECAUDIT_CUSTOM";
    private static final String KEYSPACE = "CREATE KEYSPACE ks1 WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false";
    private static final String TABLE = "CREATE TABLE ks1.t1 (key1 text, key2 int, val1 text, val2 blob, val4 int, PRIMARY KEY((key1, key2), val1))";

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

        session.execute(new SimpleStatement(KEYSPACE));
        session.execute(new SimpleStatement(TABLE));

        // Configure logger with custom format with only operation
        customLogger = new Slf4jAuditLogger(Collections.singletonMap("log_format", "operation=${OPERATION}"), CUSTOM_LOGGER_NAME);
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
    public void testShowAllObfuscator()
    {
        // Given
        setColumnObfuscator(new ShowAllObfuscator());
        // When
        executePreparedStatement();
        // Then
        assertThat(getSingleLogEntry()).contains("operation=INSERT INTO ks1.t1 (key1, key2, val1, val2, val4) VALUES (?, ?, ?, ?, ?)" +
                                                  "['PartKey1', 42, 'ClusterKey', 0x00000001000000020000000300000004, 43]");
    }

    @Test
    public void testHideBlobsObfuscator()
    {
        // Given
        setColumnObfuscator(new HideBlobsObfuscator());
        // When
        executePreparedStatement();
        // Then
        assertThat(getSingleLogEntry()).contains("operation=INSERT INTO ks1.t1 (key1, key2, val1, val2, val4) VALUES (?, ?, ?, ?, ?)" +
                                                  "['PartKey1', 42, 'ClusterKey', <blob>, 43]");
    }

    private List<String> getSingleLogEntry()
    {
        verify(mockAuditAppender, atLeastOnce()).doAppend(loggingEventCaptor.capture());
        return loggingEventCaptor.getAllValues().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    private void executePreparedStatement()
    {
        String insert = "INSERT INTO ks1.t1 (key1, key2, val1, val2, val4) VALUES (?, ?, ?, ?, ?)";

        PreparedStatement preparedInsert = session.prepare(insert);
        session.execute(preparedInsert.bind("PartKey1",
                                            42,
                                            "ClusterKey",
                                            createBlob(16),
                                            43));
    }

    private Buffer createBlob(int capacityInBytes)
    {
        ByteBuffer buffer = ByteBuffer.allocate(capacityInBytes);
        for (int i = 1; i <= capacityInBytes / 4; i++)
        {
            buffer.putInt(i);
        }
        return buffer.flip();
    }

    private void setColumnObfuscator(ColumnObfuscator obfuscator)
    {
        AuditAdapter.getInstance().setColumnObfuscator(obfuscator);
    }
}
