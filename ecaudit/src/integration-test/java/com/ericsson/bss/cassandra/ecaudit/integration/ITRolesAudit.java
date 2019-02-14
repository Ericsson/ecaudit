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
public class ITRolesAudit
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
            "CREATE ROLE superrole WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_alter' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_drop' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_authorize' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_alter' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_drop' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE superrole WITH OPTIONS = { 'grant_audit_whitelist_for_modify' : 'data'}"));
        }

        superCluster = cdt.createCluster("superrole", "secret");
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
            superSession.execute(new SimpleStatement("DROP ROLE IF EXISTS rolerole" + i));
        }
        superSession.close();
        superCluster.close();

        try (Cluster cassandraCluster = cdt.createCluster();
             Session cassandraSession = cassandraCluster.connect())
        {
            cassandraSession.execute(new SimpleStatement("DROP ROLE IF EXISTS superrole"));
        }
    }

    @Test
    public void createRoleIsLogged()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole = getUniqueUsername();

        testSession.execute(new SimpleStatement(
        "CREATE ROLE " + testRole + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));

        thenAuditLogContainEntryForUser("CREATE ROLE " + testRole + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true", username);
    }

    @Test
    public void createRoleIsWhitelisted()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole = getUniqueUsername();
        whenRoleIsWhitelistedForOperationOnResource(username, "create", "roles");

        testSession.execute(new SimpleStatement(
        "CREATE ROLE " + testRole + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));

        thenAuditLogContainNothingForUser();
    }

    @Test
    public void alterRoleIsLogged()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole = givenUniqueSuperuserWithMinimalWhitelist();

        testSession.execute(new SimpleStatement(
        "ALTER ROLE " + testRole + " WITH LOGIN = false"));

        thenAuditLogContainEntryForUser("ALTER ROLE " + testRole + " WITH LOGIN = false", username);
    }

    @Test
    public void alterRoleIsWhitelisted()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole = givenUniqueSuperuserWithMinimalWhitelist();
        whenRoleIsWhitelistedForOperationOnResource(username, "alter", "roles/" + testRole);

        testSession.execute(new SimpleStatement(
        "ALTER ROLE " + testRole + " WITH LOGIN = false"));

        thenAuditLogContainNothingForUser();
    }

    @Test
    public void dropRoleIsLogged()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole = givenUniqueSuperuserWithMinimalWhitelist();

        testSession.execute(new SimpleStatement(
        "DROP ROLE " + testRole));

        thenAuditLogContainEntryForUser("DROP ROLE " + testRole, username);
    }

    @Test
    public void dropRoleIsWhitelisted()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole = givenUniqueSuperuserWithMinimalWhitelist();
        whenRoleIsWhitelistedForOperationOnResource(username, "drop", "roles/" + testRole);

        testSession.execute(new SimpleStatement(
        "DROP ROLE " + testRole));

        thenAuditLogContainNothingForUser();
    }

    @Test
    public void grantRoleIsLogged()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole1 = givenUniqueSuperuserWithMinimalWhitelist();
        String testRole2 = givenUniqueSuperuserWithMinimalWhitelist();

        testSession.execute(new SimpleStatement(
        "GRANT " + testRole1 + " TO " + testRole2));

        thenAuditLogContainEntryForUser("GRANT " + testRole1 + " TO " + testRole2, username);
    }

    @Test
    public void grantRoleIsWhitelisted()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole1 = givenUniqueSuperuserWithMinimalWhitelist();
        String testRole2 = givenUniqueSuperuserWithMinimalWhitelist();
        whenRoleIsWhitelistedForOperationOnResource(username, "authorize", "roles/" + testRole1);

        testSession.execute(new SimpleStatement(
        "GRANT " + testRole1 + " TO " + testRole2));

        thenAuditLogContainNothingForUser();
    }

    @Test
    public void revokeRoleIsLogged()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole1 = givenUniqueSuperuserWithMinimalWhitelist();
        String testRole2 = givenUniqueSuperuserWithMinimalWhitelist();
        givenRoleGrantedTo(testRole1, testRole2);

        testSession.execute(new SimpleStatement(
        "REVOKE " + testRole1 + " FROM " + testRole2));

        thenAuditLogContainEntryForUser("REVOKE " + testRole1 + " FROM " + testRole2, username);
    }

    @Test
    public void revokeRoleIsWhitelisted()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole1 = givenUniqueSuperuserWithMinimalWhitelist();
        String testRole2 = givenUniqueSuperuserWithMinimalWhitelist();
        givenRoleGrantedTo(testRole1, testRole2);
        whenRoleIsWhitelistedForOperationOnResource(username, "authorize", "roles/" + testRole1);

        try (Cluster privateCluster = cdt.createCluster(username, "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "REVOKE " + testRole1 + " FROM " + testRole2));
        }

        thenAuditLogContainNothingForUser();
    }

    @Test
    public void listRolesIsLogged()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole = givenUniqueSuperuserWithMinimalWhitelist();

        testSession.execute(new SimpleStatement(
        "LIST ROLES OF " + testRole));

        thenAuditLogContainEntryForUser("LIST ROLES OF " + testRole, username);
    }

    @Test
    public void listRolesIsWhitelisted()
    {
        String username = givenSuperuserWithMinimalWhitelist();
        String testRole = givenUniqueSuperuserWithMinimalWhitelist();
        whenRoleIsWhitelistedForOperationOnResource(username, "describe", "roles");

        try (Cluster privateCluster = cdt.createCluster(username, "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "LIST ROLES OF " + testRole));
        }

        thenAuditLogContainNothingForUser();
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
        return "rolerole" + usernameNumber.getAndIncrement();
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

    private void givenRoleGrantedTo(String role1, String grantee)
    {
        superSession.execute(new SimpleStatement(
        "GRANT " + role1 + " TO " + grantee));
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
