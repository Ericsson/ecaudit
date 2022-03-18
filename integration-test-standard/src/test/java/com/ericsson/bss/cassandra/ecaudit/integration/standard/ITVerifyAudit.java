/*
 * Copyright 2018 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.integration.standard;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.AuthenticationException;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;
import net.jcip.annotations.NotThreadSafe;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * This test class provides a functional integration test with Cassandra itself.
 *
 * This is achieved by starting an embedded Cassandra server where the audit plug-in is used. Then each test case send
 * different requests and capture and verify that expected audit entries are produced.
 *
 * This class also works as a safe guard to changes on the public API of the plug-in. The plug-in has three different
 * interfaces that together make out its public API. It is Cassandra itself, the configuration, and the audit messages
 * sent to the supported log back ends. When a change is necessary here it indicates that the major or minor version
 * should be bumped as well. This class is mostly focused to verify that a correct audit logs are created based on a
 * specific configuration.
 */
@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ITVerifyAudit
{
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static CassandraDaemonForAuditTest cdt;
    private static Cluster cluster;
    private static Session session;

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        cdt = CassandraDaemonForAuditTest.getInstance();

        cluster = cdt.createCluster();
        session = cluster.connect();

        session.execute(new SimpleStatement(
        "ALTER ROLE cassandra WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/system_schema' }"));

        session.execute(new SimpleStatement(
        "CREATE KEYSPACE ecks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false"));
        session.execute(new SimpleStatement("CREATE TABLE ecks.ectbl (partk int PRIMARY KEY, clustk text, value text)"));
        session.execute(new SimpleStatement(
        "CREATE TABLE ecks.ectypetbl (partk int PRIMARY KEY, v0 text, v1 ascii, v2 bigint, v3 blob, v4 boolean, "
        + "v5 date, v6 decimal, v7 double, v8 float, v9 inet, v10 int, v11 smallint, v12 time, v13 timestamp, "
        + "v14 uuid, v15 varchar, v16 varint)"));

        session.execute(new SimpleStatement("CREATE ROLE ecuser WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement("GRANT CREATE ON ALL ROLES TO ecuser"));

        session.execute(new SimpleStatement(
        "CREATE ROLE sam WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));
        session.execute(new SimpleStatement(
        "ALTER ROLE sam WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/system'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE sam WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/system_schema'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE sam WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/ectbl'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE sam WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/nonexistingks'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE sam WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/nonexistingtbl'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE sam WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections'}"));
        session.execute(new SimpleStatement(
        "GRANT MODIFY ON ecks.ectbl TO sam"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON ecks.ectbl TO sam"));

        session.execute(new SimpleStatement(
        "CREATE ROLE foo WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));
        session.execute(new SimpleStatement(
        "ALTER ROLE foo WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE foo WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE foo WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections'}"));

        session.execute(new SimpleStatement(
        "CREATE ROLE bar WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));
        session.execute(new SimpleStatement(
        "CREATE ROLE mute WITH LOGIN = false"));
        session.execute(new SimpleStatement(
        "ALTER ROLE mute WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE mute WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles'}"));
        session.execute(new SimpleStatement(
        "ALTER ROLE mute WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections'}"));
        session.execute(new SimpleStatement(
        "GRANT mute TO bar"));

        session.execute(new SimpleStatement(
        "CREATE ROLE yser2 WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));

        session.execute(new SimpleStatement(
        "CREATE KEYSPACE ecks2 WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false"));

        session.execute(new SimpleStatement(
        "CREATE KEYSPACE ecks3 WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false"));
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
        session.execute(new SimpleStatement("DROP KEYSPACE IF EXISTS ecks"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS ecuser"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS foo"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS bar"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS mute"));

        session.close();
        cluster.close();
    }

    @Test
    public void testAuthenticateUserSuccessIsLogged()
    {
        try (Cluster privateCluster = cdt.createCluster("ecuser", "secret");
                Session privateSession = privateCluster.connect())
        {
            assertThat(privateSession.isClosed()).isFalse();
        }

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeast(1)).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();
        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                        .contains(
                                "client:'127.0.0.1'|user:'ecuser'|status:'ATTEMPT'|operation:'Authentication attempt'");
    }

    @Test
    public void testAuthenticateWhitelistedUserSuccessIsNotLogged()
    {
        List<String> unloggedUsers = Arrays.asList("foo", "bar");

        for (String user : unloggedUsers)
        {
            try (Cluster privateCluster = cdt.createCluster(user, "secret");
                    Session privateSession = privateCluster.connect())
            {
                assertThat(privateSession.isClosed()).isFalse();
            }
        }
    }

    @Test(expected = AuthenticationException.class)
    public void testAuthenticateUserRejectIsLogged()
    {
        try (Cluster privateCluster = cdt.createCluster("unknown", "secret");
                Session privateSession = privateCluster.connect())
        {
            assertThat(privateSession.isClosed()).isFalse();
        }
        finally
        {

            ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
            verify(mockAuditAppender, atLeast(2)).doAppend(loggingEventCaptor.capture());
            List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();
            assertThat(loggingEvents
                    .stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList()))
                            .contains(
                                    "client:'127.0.0.1'|user:'unknown'|status:'ATTEMPT'|operation:'Authentication attempt'",
                                    "client:'127.0.0.1'|user:'unknown'|status:'FAILED'|operation:'Authentication failed'");
        }
    }

    @Test
    public void testValidSimpleStatementsAreLogged()
    {
        List<String> statements = Arrays.asList(
                "CREATE ROLE validuser WITH PASSWORD = 'secret' AND LOGIN = true",
                "CREATE KEYSPACE validks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false",
                "CREATE TYPE validks.validtype (birthday timestamp, nationality text)",
                "CREATE TABLE validks.validtbl (partk int PRIMARY KEY, clustk text, value text, type frozen <validtype>)",
                "CREATE INDEX valididx ON validks.validtbl (value)",
                "ALTER ROLE validuser WITH PASSWORD = 'secret' AND LOGIN = false",
                "GRANT SELECT ON KEYSPACE validks TO validuser",
                "INSERT INTO validks.validtbl (partk, clustk, value, type) VALUES (1, 'one', 'valid', { birthday : '1976-02-25', nationality : 'Swedish' })",
                "SELECT * FROM validks.validtbl",
                "DELETE FROM validks.validtbl WHERE partk = 2",
                "LIST ROLES OF validuser",
                "LIST ALL PERMISSIONS",
                "LIST ALL PERMISSIONS OF validuser",
                "LIST SELECT OF validuser",
                "LIST SELECT ON KEYSPACE validks",
                "LIST SELECT ON KEYSPACE validks OF validuser",
                "REVOKE SELECT ON KEYSPACE validks FROM validuser",
                "TRUNCATE TABLE validks.validtbl",
                "DROP INDEX validks.valididx",
                "DROP KEYSPACE IF EXISTS validks",
                "DROP ROLE IF EXISTS validuser");

        for (String statement : statements)
        {
            session.execute(new SimpleStatement(statement));
        }

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeast(statements.size())).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                        .containsAll(expectedAttempts(statements));
    }

    @Test
    public void testValidPreparedStatementsAreLogged()
    {
        PreparedStatement preparedInsertStatement = session
                .prepare("INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)");
        PreparedStatement preparedSelectStatement = session
                .prepare("SELECT * FROM ecks.ectbl WHERE partk = ?");
        PreparedStatement preparedDeleteStatement = session.prepare("DELETE FROM ecks.ectbl WHERE partk = ?");

        List<String> expectedStatements = Arrays.asList(
                "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[1, '1', 'valid']",
                "SELECT * FROM ecks.ectbl WHERE partk = ?[1]",
                "DELETE FROM ecks.ectbl WHERE partk = ?[1]",
                "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[2, '2', 'valid']",
                "SELECT * FROM ecks.ectbl WHERE partk = ?[2]",
                "DELETE FROM ecks.ectbl WHERE partk = ?[2]");

        for (int i = 1; i <= 2; i++)
        {
            session.execute(preparedInsertStatement.bind(i, Integer.toString(i), "valid"));
            session.execute(preparedSelectStatement.bind(i));
            session.execute(preparedDeleteStatement.bind(i));
        }

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeast(expectedStatements.size())).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                        .containsAll(expectedAttempts(expectedStatements));
    }

    @Test
    public void testPreparedStatementsWithoutValuesAreLogged()
    {
        PreparedStatement preparedInsertStatement = session.prepare("SELECT * FROM ecks.ectbl");

        List<String> expectedStatements = Arrays.asList(
        "SELECT * FROM ecks.ectbl[]",
        "SELECT * FROM ecks.ectbl[]");

        for (int i = 1; i <= 2; i++)
        {
            session.execute(preparedInsertStatement.bind());
        }

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeast(expectedStatements.size())).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                   .stream()
                   .map(ILoggingEvent::getFormattedMessage)
                   .collect(Collectors.toList()))
        .containsAll(expectedAttempts(expectedStatements));
    }

    @Test
    public void testValidBatchStatementsAreLogged()
    {
        PreparedStatement preparedInsertStatement1 = session
                .prepare("INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)");
        PreparedStatement preparedInsertStatement2 = session
                .prepare("INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, 'valid')");

        List<String> expectedStatements = Arrays.asList(
                "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[1, '1', 'valid']",
                "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[2, '2', 'valid']",
                "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, 'valid')[3, '3']",
                "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (4, '4', 'valid')",
                "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, 'valid')[5, '5']");

        BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
        batch.add(preparedInsertStatement1.bind(1, "1", "valid"));
        batch.add(preparedInsertStatement1.bind(2, "2", "valid"));
        batch.add(preparedInsertStatement2.bind(3, "3"));
        batch.add(new SimpleStatement("INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (4, '4', 'valid')"));
        batch.add(preparedInsertStatement2.bind(5, "5"));
        session.execute(batch);

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeast(expectedStatements.size())).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                        .flatExtracting(e -> Arrays.asList(e.split(UUID_REGEX)))
                        .containsAll(expectedBatchAttemptSegments(expectedStatements));
    }

    @Test
    public void testValidNonPreparedBatchStatementsAreLogged()
    {
        List<String> allStatements = Arrays.asList(
        "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (1, '1', 'valid')",
        "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (2, '2', 'valid')",
        "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (3, '3', 'valid')",
        "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (4, '4', 'valid')",
        "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (5, '5', 'valid')");

        StringBuilder batchStatementBuilder = new StringBuilder("BEGIN UNLOGGED BATCH ");
        for (String statement : allStatements)
        {
            batchStatementBuilder.append(statement).append("; ");
        }
        batchStatementBuilder.append("APPLY BATCH;");

        List<String> expectedStatements = Collections.singletonList(batchStatementBuilder.toString());

        session.execute(batchStatementBuilder.toString());

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeastOnce()).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                   .stream()
                   .map(ILoggingEvent::getFormattedMessage)
                   .collect(Collectors.toList()))
                        .containsAll(expectedAttempts(expectedStatements));
    }

    @Test
    public void testValidPreparedBatchStatementsAreLogged()
    {
        String statement = "BEGIN UNLOGGED BATCH USING TIMESTAMP ? " +
                           "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?); " +
                           "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, 'valid'); " +
                           "APPLY BATCH;";

        List<String> expectedStatements = Collections.singletonList(statement + "[1234, 1, '1', 'valid', 3, '3']");

        PreparedStatement preparedBatchStatement = session.prepare(statement);
        session.execute(preparedBatchStatement.bind(1234L, 1, "1", "valid", 3, "3"));

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeastOnce()).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                   .stream()
                   .map(ILoggingEvent::getFormattedMessage)
                   .collect(Collectors.toList()))
                        .containsAll(expectedAttempts(expectedStatements));
    }

    @Test
    public void testValidPreparedStatementTypesAreLogged() throws Exception
    {
        PreparedStatement preparedStatement = session
                .prepare("INSERT INTO ecks.ectypetbl "
                        + "(partk, v0, v1, v2, v4, v5, v9, v13, v15)"
                        + " VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?)");

        String expectedStatement = "INSERT INTO ecks.ectypetbl "
                + "(partk, v0, v1, v2, v4, v5, v9, v13, v15)"
                + " VALUES "
                + "(?, ?, ?, ?, ?, ?, ?, ?, ?)[1, 'text', 'ascii', 123123123123123123, true, 1976-02-25, 8.8.8.8, 2004-05-29T14:29:00.000Z, 'varchar']";
        // TODO: Are these bugs in Cassandra?
        // Was expecting "v5 date" to get quotes
        // Was expecting "v9 inet" to get quotes
        // Was expecting "v13 timestamp" to get quotes

        session.execute(preparedStatement.bind(1, "text", "ascii", 123123123123123123L,
                                               Boolean.TRUE, LocalDate.fromYearMonthDay(1976, 2, 25), InetAddress.getByName("8.8.8.8"),
                                               Date.from(Instant.parse("2004-05-29T14:29:00.000Z")), "varchar"));

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeast(1)).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                        .containsAll(expectedAttempts(Arrays.asList(expectedStatement)));
    }

    @Test
    public void testValidSimpleStatementTypesAreLogged()
    {
        String statement = "INSERT INTO ecks.ectypetbl "
                + "(partk, v0, v1, v2, v4, v5, v9, v13, v15)"
                + " VALUES "
                + "(1, 'text', 'ascii', 123123123123123123, true, '1976-02-25', '8.8.8.8', '2004-05-29T14:29:00.000Z', 'varchar')";

        session.execute(statement);

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeast(1)).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                        .containsAll(expectedAttempts(Arrays.asList(statement)));
    }

    @Test
    public void testWhitelistedUserValidStatementsAreNotLogged()
    {
        List<String> statements = Arrays.asList(
                "CREATE ROLE validuser WITH PASSWORD = 'secret' AND LOGIN = true",
                "CREATE KEYSPACE validks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false",
                "CREATE TABLE validks.validtbl (partk int PRIMARY KEY, clustk text, value text)",
                "INSERT INTO validks.validtbl (partk, clustk, value) VALUES (1, 'one', 'valid')",
                "SELECT * FROM validks.validtbl",
                "DELETE FROM validks.validtbl WHERE partk = 2",
                "DROP KEYSPACE IF EXISTS validks",
                "DROP ROLE IF EXISTS validuser");

        List<String> unloggedUsers = Arrays.asList("foo", "bar");

        for (String user : unloggedUsers)
        {
            try (Cluster privateCluster = cdt.createCluster(user, "secret");
                    Session privateSession = privateCluster.connect())
            {
                for (String statement : statements)
                {
                    privateSession.execute(new SimpleStatement(statement));
                }
            }
        }
    }

    @Test
    public void testWhitelistedUserValidStatementsWithUseAreNotLogged()
    {
        // Driver or Cassandra will add double-quotes to ks on one of the connections if statemens doesn't have it here.
        // TODO: Research if this is "bug" in Cassandra, driver or ecAudit?
        List<String> statements = Arrays.asList(
                "INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (1, 'one', 'valid')",
                "SELECT * FROM ecks.ectbl",
                "USE \"ecks\"",
                "INSERT INTO ectbl (partk, clustk, value) VALUES (1, 'one', 'valid')",
                "SELECT * FROM ectbl");

        String user = "sam";
        try (Cluster privateCluster = cdt.createCluster(user, "secret");
                Session privateSession = privateCluster.connect())
        {
            for (String statement : statements)
            {
                privateSession.execute(new SimpleStatement(statement));
            }
        }

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        // Will typically see 2 USE statements, assuming this is one for ordinary connection and one for control connection
        // TODO: Research if assumption above is correct
        verify(mockAuditAppender, atLeast(1)).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                .containsOnlyElementsOf(expectedAttemptsAsUser(Arrays.asList("USE \"ecks\""), user));
    }

    /**
     * Each USE statement will typically result in two log entries. Further, the second USE log entry
     * will only appear just before the next statement is issued from the client. Though this is a bit
     * unexpected it still seem as if the order is preserved which is verified by this test case.
     *
     * TODO: Research why we get this behavior.
     */
    @Test
    public void testMultipleUseStatementsPreserveOrder()
    {
        String user = "sam";
        try (Cluster privateCluster = cdt.createCluster(user, "secret");
             Session privateSession = privateCluster.connect())
        {
            executeOneUseWithFollowingSelect(user, privateSession, "USE \"ecks\"");
            executeOneUseWithFollowingSelect(user, privateSession, "USE \"ecks2\"");
            executeOneUseWithFollowingSelect(user, privateSession, "USE \"ecks3\"");
        }
    }

    private void executeOneUseWithFollowingSelect(String user, Session privateSession, String useStatement) {
        ArgumentCaptor<ILoggingEvent> loggingEventCaptor1 = ArgumentCaptor.forClass(ILoggingEvent.class);
        privateSession.execute(new SimpleStatement(useStatement));
        privateSession.execute(new SimpleStatement("SELECT * FROM ecks.ectypetbl"));
        verify(mockAuditAppender, atLeast(2)).doAppend(loggingEventCaptor1.capture());
        List<ILoggingEvent> loggingEvents1 = loggingEventCaptor1.getAllValues();
        assertThat(loggingEvents1
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                .containsOnlyElementsOf(expectedAttemptsAsUser(Arrays.asList(useStatement, "SELECT * FROM ecks.ectypetbl"), user));
        reset(mockAuditAppender);
    }

    @Test
    public void testFailedStatementsAreLogged()
    {
        List<String> statements = Arrays.asList(
                "CREATE ROLE ecuser WITH PASSWORD = 'secret' AND LOGIN = true",
                "CREATE KEYSPACE ecks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false",
                "CREATE TABLE ecks.ectbl (partk int PRIMARY KEY, clustk text, value text)",
                "INSERT INTO invalidks.invalidtbl (partk, clustk, value) VALUES (1, 'one', 'valid')",
                "SELECT * FROM invalidks.invalidtbl",
                "SELECT * FROM ecks.invalidtbl",
                "DELETE FROM invalidks.invalidtbl WHERE partk = 2",
                "DROP KEYSPACE invalidks",
                "DROP ROLE invaliduser",
                "CREATE ROLE invaliduser \nWITH PASSWORD = 'secret' _unknown_");

        for (String statement : statements)
        {
            assertThatExceptionOfType(DriverException.class).isThrownBy(() -> session.execute(new SimpleStatement(statement)));
        }

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, atLeast(statements.size() * 2)).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                        .containsAll(expectedAttemptsAndFails(statements));
    }

    @Test
    public void testFailedWhitelistedStatementsAreNotLogged()
    {
        List<String> statements = Arrays.asList(
                "SELECT * FROM nonexistingks.nonexistingtbl",
                "SELECT * FROM ecks.nonexistingtbl",
                "INSERT INTO nonexistingks.nonexistingtbl (partk, clustk, value) VALUES (1, 'one', 'valid')",
                "INSERT INTO ecks.nonexistingtbl (partk, clustk, value) VALUES (1, 'one', 'valid')");

        try (Cluster privateCluster = cdt.createCluster("sam", "secret");
             Session privateSession = privateCluster.connect())
        {
            for (String statement : statements)
            {
                assertThatExceptionOfType(InvalidQueryException.class).isThrownBy(() -> privateSession.execute(new SimpleStatement(statement)));
            }
        }
    }

    @Test
    public void testFailedWhitelistedBatchStatementIsNotLogged()
    {
            List<String> statements = Arrays.asList(
                "INSERT INTO nonexistingks.nonexistingtbl (partk, clustk, value) VALUES (1, 'one', 'valid')",
                "INSERT INTO validks.nonexistingtbl (partk, clustk, value) VALUES (1, 'one', 'valid')");

        try (Cluster privateCluster = cdt.createCluster("sam", "secret");
             Session privateSession = privateCluster.connect())
        {
            for (String statement : statements)
            {
                BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
                batch.add(new SimpleStatement("INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (4, '4', 'valid')"));
                batch.add(new SimpleStatement(statement));
                assertThatExceptionOfType(InvalidQueryException.class).isThrownBy(() -> privateSession.execute(batch));
            }
        }
    }

    @Test
    public void testYamlWhitelistedUserShowConnectionAttemptsButValidStatementsAreNotLogged()
    {
        List<String> statements = Arrays.asList(
                "CREATE ROLE validuser WITH PASSWORD = 'secret' AND LOGIN = true",
                "CREATE KEYSPACE validks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false",
                "CREATE TABLE validks.validtbl (partk int PRIMARY KEY, clustk text, value text)",
                "INSERT INTO validks.validtbl (partk, clustk, value) VALUES (1, 'one', 'valid')",
                "SELECT * FROM validks.validtbl",
                "DELETE FROM validks.validtbl WHERE partk = 2",
                "DROP KEYSPACE IF EXISTS validks",
                "DROP ROLE IF EXISTS validuser");

        List<String> unloggedUsers = Arrays.asList("yser2");

        for (String user : unloggedUsers)
        {
            try (Cluster privateCluster = cdt.createCluster(user, "secret");
                    Session privateSession = privateCluster.connect())
            {
                for (String statement : statements)
                {
                    privateSession.execute(new SimpleStatement(statement));
                }
            }
        }

        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, times(2)).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList()))
                        .contains(
                                "client:'127.0.0.1'|user:'yser2'|status:'ATTEMPT'|operation:'Authentication attempt'");
    }

    private List<String> expectedAttempts(List<String> statements)
    {
        return statements
                .stream()
                .map(s -> s.replaceAll("secret", "*****"))
                .map(s -> String.format("client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'%s'", s))
                .collect(Collectors.toList());
    }

    private List<String> expectedAttemptsAsUser(List<String> statements, String user)
    {
        return statements
                .stream()
                .map(s -> s.replaceAll("secret", "*****"))
                .map(s -> String.format("client:'127.0.0.1'|user:'%s'|status:'ATTEMPT'|operation:'%s'", user, s))
                .collect(Collectors.toList());
    }

    private List<String> expectedBatchAttemptSegments(List<String> statements)
    {
        List<String> result = new ArrayList<>();
        for (String statement : statements)
        {
            result.add("client:'127.0.0.1'|user:'cassandra'|batchId:'");
            result.add(String.format("'|status:'ATTEMPT'|operation:'%s'", statement));
        }
        return result;
    }

    private List<String> expectedAttemptsAndFails(List<String> statements)
    {
        List<String> obfuscatedStatements = statements
                .stream()
                .map(s -> s.replaceAll("secret", "*****"))
                .collect(Collectors.toList());

        List<String> expectedLogPairs = new ArrayList<>();
        for (String statement : obfuscatedStatements)
        {
            expectedLogPairs.add(
                    String.format("client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'%s'", statement));
            expectedLogPairs.add(
                    String.format("client:'127.0.0.1'|user:'cassandra'|status:'FAILED'|operation:'%s'", statement));
        }

        return expectedLogPairs;
    }
}
