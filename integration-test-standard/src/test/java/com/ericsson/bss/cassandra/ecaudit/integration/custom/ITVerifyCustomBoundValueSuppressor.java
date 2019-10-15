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
import java.util.Arrays;
import java.util.Collections;

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
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.TupleType;
import com.datastax.driver.core.UserType;
import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.BoundValueSuppressor;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.SuppressBlobs;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.SuppressClusteringAndRegular;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.SuppressEverything;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.SuppressNothing;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.SuppressRegular;
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

/**
 * This test class provides a functional integration test for custom bound value suppressor log format with Cassandra itself.
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
public class ITVerifyCustomBoundValueSuppressor
{
    private static final String CUSTOM_LOGGER_NAME = "ECAUDIT_CUSTOM";
    private static final String KEYSPACE = "CREATE KEYSPACE ks1 WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false";
    private static final String UDT = "CREATE TYPE ks1.mytype (mykey text, myval blob)";
    private static final String TABLE = "CREATE TABLE ks1.t1 (key1 text, key2 int, key3 text, val1 blob, val2 list<blob>, val3 map<int, frozen<list<blob>>>, val4 int, val5 tuple<text, blob>, val6 frozen<ks1.mytype>, PRIMARY KEY((key1, key2), key3))";
    private static final String INSERT = "INSERT INTO ks1.t1 (key1, key2, key3, val1, val2, val3, val4, val5, val6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static Cluster cluster;
    private static Session session;
    private static AuditLogger customLogger;

    private static BoundValueSuppressor defaultSuppressor;

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
        session.execute(new SimpleStatement(UDT));
        session.execute(new SimpleStatement(TABLE));

        // Configure logger with custom format with only operation
        customLogger = new Slf4jAuditLogger(Collections.singletonMap("log_format", "operation=${OPERATION}"), CUSTOM_LOGGER_NAME);
        AuditAdapter.getInstance().getAuditor().addLogger(customLogger);

        defaultSuppressor = getBoundValueSuppressor();
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
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(CUSTOM_LOGGER_NAME).detachAppender(mockAuditAppender);
    }

    @AfterClass
    public static void afterClass()
    {
        AuditAdapter.getInstance().getAuditor().removeLogger(customLogger);
        session.close();
        cluster.close();
        setBoundValueSuppressor(defaultSuppressor);
    }

    @Test
    public void testSuppressNothing()
    {
        // Given
        setBoundValueSuppressor(new SuppressNothing());
        // When
        executePreparedStatement();
        // Then
        assertThat(getLogEntry()).isEqualTo("operation=INSERT INTO ks1.t1 (key1, key2, key3, val1, val2, val3, val4, val5, val6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                                            "['PartKey1', 42, 'ClusterKey', 0x00000001000000020000000300000004, [0x00000001, 0x0000000100000002], {99: [0x00000001, 0x00000001]}, 43, ('Hello', 0x00000001), {mykey: 'Kalle', myval: 0x00000001000000020000000300000004}]");
    }

    @Test
    public void testSuppressBlobs()
    {
        // Given
        setBoundValueSuppressor(new SuppressBlobs());
        // When
        executePreparedStatement();
        // Then
        assertThat(getLogEntry()).isEqualTo("operation=INSERT INTO ks1.t1 (key1, key2, key3, val1, val2, val3, val4, val5, val6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                                            "['PartKey1', 42, 'ClusterKey', <blob>, <list<blob>>, <map<int, list<blob>>>, 43, <tuple<text, blob>>, <mytype>]");
    }

    @Test
    public void testSuppressRegular()
    {
        // Given
        setBoundValueSuppressor(new SuppressRegular());
        // When
        executePreparedStatement();
        // Then
        assertThat(getLogEntry()).isEqualTo("operation=INSERT INTO ks1.t1 (key1, key2, key3, val1, val2, val3, val4, val5, val6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                                            "['PartKey1', 42, 'ClusterKey', <blob>, <list<blob>>, <map<int, list<blob>>>, <int>, <tuple<text, blob>>, <mytype>]");
    }

    @Test
    public void testSuppressClusteringAndRegular()
    {
        // Given
        setBoundValueSuppressor(new SuppressClusteringAndRegular());
        // When
        executePreparedStatement();
        // Then
        assertThat(getLogEntry()).isEqualTo("operation=INSERT INTO ks1.t1 (key1, key2, key3, val1, val2, val3, val4, val5, val6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                                            "['PartKey1', 42, <text>, <blob>, <list<blob>>, <map<int, list<blob>>>, <int>, <tuple<text, blob>>, <mytype>]");
    }

    @Test
    public void testSuppressEverything()
    {
        // Given
        setBoundValueSuppressor(new SuppressEverything());
        // When
        executePreparedStatement();
        // Then
        assertThat(getLogEntry()).isEqualTo("operation=INSERT INTO ks1.t1 (key1, key2, key3, val1, val2, val3, val4, val5, val6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                                            "[<text>, <int>, <text>, <blob>, <list<blob>>, <map<int, list<blob>>>, <int>, <tuple<text, blob>>, <mytype>]");
    }

    private String getLogEntry()
    {
        verify(mockAuditAppender, atLeastOnce()).doAppend(loggingEventCaptor.capture());
        return loggingEventCaptor.getAllValues()
                                 .stream()
                                 .map(ILoggingEvent::getFormattedMessage)
                                 .filter(s -> s.contains(INSERT))
                                 .findFirst().get();
    }

    private void executePreparedStatement()
    {
        TupleType tupleType = TupleType.of(ProtocolVersion.NEWEST_SUPPORTED, CodecRegistry.DEFAULT_INSTANCE, DataType.text(), DataType.blob());
        UserType udt = session.getCluster().getMetadata().getKeyspace("ks1").getUserType("mytype");

        PreparedStatement preparedInsert = session.prepare(INSERT);
        session.execute(preparedInsert.bind("PartKey1",
                                            42,
                                            "ClusterKey",
                                            createBlob(16),
                                            Arrays.asList(createBlob(4), createBlob(8)),
                                            Collections.singletonMap(99, Arrays.asList(createBlob(4), createBlob(4))),
                                            43,
                                            tupleType.newValue("Hello", createBlob(4)),
                                            udt.newValue().setString("mykey", "Kalle").setBytes("myval", (ByteBuffer)createBlob(16))
                                            ));
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

    private static void setBoundValueSuppressor(BoundValueSuppressor suppressor)
    {
        AuditAdapter.getInstance().setBoundValueSuppressor(suppressor);
    }

    private static BoundValueSuppressor getBoundValueSuppressor()
    {
        return AuditAdapter.getInstance().getBoundValueSuppressor();
    }
}
