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
package com.ericsson.bss.cassandra.ecaudit.integration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class ITFunctionsAudit
{
    private static CassandraDaemonForAuditTest cdt;
    private static Cluster superCluster;
    private static Session superSession;

    private static String testUsername;
    private static Cluster testCluster;
    private static Session testSession;

    private static AtomicInteger usernameNumber = new AtomicInteger();

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        cdt = CassandraDaemonForAuditTest.getInstance();

        try (Cluster cassandraCluster = cdt.createCluster();
             Session cassandraSession = cassandraCluster.connect())
        {
            cassandraSession.execute(new SimpleStatement(
            "CREATE ROLE superfunc WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_execute' : 'connections'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_alter' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_drop' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_authorize' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_alter' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_drop' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_modify' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superfunc WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'functions'}"));
        }

        superCluster = cdt.createCluster("superfunc", "secret");
        superSession = superCluster.connect();

        testUsername = givenUniqueSuperuserWithMinimalWhitelist();
        testCluster = cdt.createCluster(testUsername, "secret");
        testSession = testCluster.connect();
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
        resetTestUserWithMinimalWhitelist(testUsername);
    }

    @AfterClass
    public static void afterClass()
    {
        testSession.close();
        testCluster.close();

        for (int i = 0; i < usernameNumber.get(); i++)
        {
            superSession.execute(new SimpleStatement("DROP ROLE IF EXISTS funcrole" + i));
        }
        superSession.close();
        superCluster.close();

        try (Cluster cassandraCluster = cdt.createCluster();
             Session cassandraSession = cassandraCluster.connect())
        {
            cassandraSession.execute(new SimpleStatement("DROP ROLE IF EXISTS superfunc"));
        }
    }

    @Test
    public void createFunctionIsLogged()
    {
        givenKeyspace("funcks");
        String username = givenSuperuserWithMinimalWhitelist();

        testSession.execute(new SimpleStatement(
        "CREATE FUNCTION IF NOT EXISTS funcks.flog1 (input double) " +
        "CALLED ON NULL INPUT " +
        "RETURNS double LANGUAGE java AS " +
        "'return Double.valueOf(Math.log(input.doubleValue()));'"));

        thenAuditLogContainEntryForUser("CREATE FUNCTION IF NOT EXISTS funcks.flog1 (input double) " +
                                        "CALLED ON NULL INPUT " +
                                        "RETURNS double LANGUAGE java AS " +
                                        "'return Double.valueOf(Math.log(input.doubleValue()));'", username);
    }

    @Test
    public void createFunctionIsWhitelisted()
    {
        givenKeyspace("funcks");
        String username = givenSuperuserWithMinimalWhitelist();
        whenRoleIsWhitelistedForOperationOnResource(username, "create", "functions/funcks");

        testSession.execute(new SimpleStatement(
        "CREATE FUNCTION IF NOT EXISTS funcks.flog2 (input double) " +
        "CALLED ON NULL INPUT " +
        "RETURNS double LANGUAGE java AS " +
        "'return Double.valueOf(Math.log(input.doubleValue()));'"));

        thenAuditLogContainNothingForUser();
    }

    @Test
    public void dropFunctionIsLogged()
    {
        givenFunction("funcks", "flog3");
        String username = givenSuperuserWithMinimalWhitelist();

        testSession.execute(new SimpleStatement("DROP FUNCTION IF EXISTS funcks.flog3(double)"));

        thenAuditLogContainEntryForUser("DROP FUNCTION IF EXISTS funcks.flog3(double)", username);
    }

    @Test
    public void dropFunctionIsWhitelisted()
    {
        givenFunction("funcks", "flog4");
        String username = givenSuperuserWithMinimalWhitelist();
        whenRoleIsWhitelistedForOperationOnResource(username, "drop", "functions/funcks/flog4|DoubleType");

        testSession.execute(new SimpleStatement("DROP FUNCTION IF EXISTS funcks.flog4(double)"));

        thenAuditLogContainNothingForUser();
    }

    @Test
    public void createAggregateIsLogged()
    {
        givenStateFunction("aggks", "avgState1");
        givenFinalStateFunction("aggks", "avgFinal1");
        String username = givenSuperuserWithMinimalWhitelist();

        testSession.execute(new SimpleStatement(
        "CREATE AGGREGATE IF NOT EXISTS aggks.aaverage1 (int) " +
        "SFUNC avgState1 " +
        "STYPE tuple<int,bigint> " +
        "FINALFUNC avgFinal1 " +
        "INITCOND (0,0)"));

        thenAuditLogContainEntryForUser("CREATE AGGREGATE IF NOT EXISTS aggks.aaverage1 (int) " +
                                        "SFUNC avgState1 " +
                                        "STYPE tuple<int,bigint> " +
                                        "FINALFUNC avgFinal1 " +
                                        "INITCOND (0,0)", username);
    }

    @Test
    public void createAggregateIsWhitelisted()
    {
        givenStateFunction("aggks", "avgState2");
        givenFinalStateFunction("aggks", "avgFinal2");
        String username = givenSuperuserWithMinimalWhitelist();
        whenRoleIsWhitelistedForOperationOnResource(username, "create", "functions/aggks");

        testSession.execute(new SimpleStatement(
        "CREATE AGGREGATE IF NOT EXISTS aggks.aaverage2 (int) " +
        "SFUNC avgState2 " +
        "STYPE tuple<int,bigint> " +
        "FINALFUNC avgFinal2 " +
        "INITCOND (0,0)"));

        thenAuditLogContainNothingForUser();
    }

    @Test
    public void dropAggregateIsLogged()
    {
        givenAggregate("aggks", "aaverage3");
        String username = givenSuperuserWithMinimalWhitelist();

        testSession.execute(new SimpleStatement("DROP AGGREGATE IF EXISTS aggks.aaverage3(int)"));

        thenAuditLogContainEntryForUser("DROP AGGREGATE IF EXISTS aggks.aaverage3(int)", username);
    }

    @Test
    public void dropAggregateIsWhitelisted()
    {
        givenAggregate("aggks", "aaverage4");
        String username = givenSuperuserWithMinimalWhitelist();
        whenRoleIsWhitelistedForOperationOnResource(username, "drop", "functions/aggks/aaverage4|Int32Type");

        testSession.execute(new SimpleStatement("DROP AGGREGATE IF EXISTS aggks.aaverage4(int)"));

        thenAuditLogContainNothingForUser();
    }

    private void givenKeyspace(String keyspace)
    {
        superSession.execute(new SimpleStatement(
        "CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false"));
    }

    private void givenFunction(String keyspace, String func)
    {
        givenKeyspace(keyspace);
        superSession.execute(new SimpleStatement(
        "CREATE FUNCTION IF NOT EXISTS " + keyspace + "." + func + " (input double) " +
        "CALLED ON NULL INPUT " +
        "RETURNS double LANGUAGE java AS " +
        "'" +
        "return Double.valueOf(Math.log(input.doubleValue()));" +
        "'"));
    }

    private void givenStateFunction(String keyspace, String func)
    {
        givenKeyspace(keyspace);
        superSession.execute(new SimpleStatement(
        "CREATE FUNCTION IF NOT EXISTS " + keyspace + "." + func + " (state tuple<int, bigint>, val int) " +
        "CALLED ON NULL INPUT " +
        "RETURNS tuple<int, bigint> LANGUAGE java AS " +
        "'" +
        "if (val != null)" +
        "{" +
        " state.setInt(0, state.getInt(0) + 1);" +
        " state.setLong(1, state.getLong(1) + val.intValue());" +
        "}" +
        "return state;" +
        "'"));
    }

    private void givenFinalStateFunction(String keyspace, String func)
    {
        givenKeyspace(keyspace);
        superSession.execute(new SimpleStatement(
        "CREATE FUNCTION IF NOT EXISTS " + keyspace + "." + func + " (state tuple<int, bigint>) " +
        "CALLED ON NULL INPUT " +
        "RETURNS double LANGUAGE java AS " +
        "'" +
        "double r = 0;" +
        "if (state.getInt(0) == 0)" +
        "{" +
        " return null;" +
        "}" +
        "r = state.getLong(1);" +
        "r/= state.getInt(0);" +
        "return Double.valueOf(r);" +
        "'"));
    }

    private void givenAggregate(String keyspace, String aggregate)
    {
        givenStateFunction(keyspace, aggregate + "SF");
        givenFinalStateFunction(keyspace, aggregate + "FSF");
        superSession.execute(new SimpleStatement(
        "CREATE AGGREGATE IF NOT EXISTS " + keyspace + "." + aggregate + " (int) " +
        "SFUNC " + aggregate + "SF " +
        "STYPE tuple<int,bigint> " +
        "FINALFUNC " + aggregate + "FSF " +
        "INITCOND (0,0)"));
    }

    private static String givenUniqueSuperuserWithMinimalWhitelist()
    {
        String username = getUniqueUsername();
        superSession.execute(new SimpleStatement(
        "CREATE ROLE " + username + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_execute'  : 'connections' }"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_select'  : 'data/system' }"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_select'  : 'data/system_schema' }"));
        return username;
    }

    private static String getUniqueUsername()
    {
        return "funcrole" + usernameNumber.getAndIncrement();
    }

    private static String givenSuperuserWithMinimalWhitelist()
    {
        return testUsername;
    }

    private void resetTestUserWithMinimalWhitelist(String username)
    {
        superSession.execute(new SimpleStatement(
        "DELETE FROM system_auth.role_audit_whitelists_v2 WHERE role = '" + username + "'"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_execute'  : 'connections' }"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_select'  : 'data/system' }"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_select'  : 'data/system_schema' }"));
    }

    private void whenRoleIsWhitelistedForOperationOnResource(String username, String operation, String resource)
    {
        superSession.execute("ALTER ROLE " + username + " WITH OPTIONS = {'grant_audit_whitelist_for_" + operation + "' : '" + resource + "'}");
    }

    private void thenAuditLogContainNothingForUser()
    {
        verify(mockAuditAppender, times(0)).doAppend(any(ILoggingEvent.class));
    }

    private void thenAuditLogContainEntryForUser(String auditOperation, String username)
    {
        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, times(1)).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents.get(0).getFormattedMessage()).isEqualTo(expectedAuditEntry(auditOperation, username));
    }

    private String expectedAuditEntry(String auditOperation, String username)
    {
        String obfuscatedOperation = auditOperation.replaceAll("secret", "*****");
        return String.format("client:'127.0.0.1'|user:'%s'|status:'ATTEMPT'|operation:'%s'", username, obfuscatedOperation);
    }
}
