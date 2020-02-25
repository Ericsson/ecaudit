/*
 * Copyright 2020 Telefonaktiebolaget LM Ericsson
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CassandraAuditTester
{
    private static final AtomicInteger superUsernameNumber = new AtomicInteger();
    private static final AtomicInteger usernameNumber = new AtomicInteger();
    private List<Integer> usernameNumbers = new ArrayList<>();

    private CassandraDaemonForAuditTest cdt;
    private String superName;
    private Cluster superCluster;
    private Session superSession;

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;

    CassandraAuditTester()
    {
        superName = "superuser" + superUsernameNumber.incrementAndGet();
        try
        {
            cdt = CassandraDaemonForAuditTest.getInstance();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        try (Cluster cassandraCluster = cdt.createCluster();
             Session cassandraSession = cassandraCluster.connect())
        {
            cassandraSession.execute("CREATE ROLE " + superName + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true");
            cassandraSession.execute("ALTER ROLE " + superName + " WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles'}");
            cassandraSession.execute("ALTER ROLE " + superName + " WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data'}");
        }

        superCluster = cdt.createCluster(superName, "secret");
        superSession = superCluster.connect();
    }

    void before()
    {
        MockitoAnnotations.initMocks(this);
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(Slf4jAuditLogger.AUDIT_LOGGER_NAME).addAppender(mockAuditAppender);
    }

    void after()
    {
        verifyNoMoreInteractions(mockAuditAppender);
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(Slf4jAuditLogger.AUDIT_LOGGER_NAME).detachAppender(mockAuditAppender);
    }

    void afterClass()
    {
        for (int usernameNumber : usernameNumbers)
        {
            superSession.execute("DROP ROLE IF EXISTS role" + usernameNumber);
        }
        superSession.close();
        superCluster.close();

        try (Cluster cassandraCluster = cdt.createCluster();
             Session cassandraSession = cassandraCluster.connect())
        {
            cassandraSession.execute("DROP ROLE IF EXISTS " + superName);
        }
    }

    String createUniqueUser()
    {
        return createUniqueUser(false);
    }

    String createUniqueSuperUser()
    {
        return createUniqueUser(true);
    }

    private String createUniqueUser(boolean superuser)
    {
        String username = getUniqueUsername();
        superSession.execute("CREATE ROLE " + username + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = " + superuser);
        return username;
    }

    String getUniqueUsername()
    {
        int uniqueNumber = usernameNumber.getAndIncrement();
        usernameNumbers.add(uniqueNumber);
        return "role" + uniqueNumber;
    }

    void resetTestUserWithMinimalWhitelist(String username)
    {
        superSession.execute("DELETE FROM system_auth.role_audit_whitelists_v2 WHERE role = '" + username + "'");
        superSession.execute("ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_execute'  : 'connections' }");
        superSession.execute("ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_select'  : 'data/system' }");
        superSession.execute("ALTER ROLE " + username + " WITH OPTIONS = { 'grant_audit_whitelist_for_select'  : 'data/system_schema' }");
    }

    void executeStatementAsSuperuserWithoutAudit(String statement)
    {
        superSession.execute(statement);
    }

    void createKeyspace(String keyspace)
    {
        superSession.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false");
    }

    void whitelistRoleForOperationOnResource(String username, String operation, String resource)
    {
        superSession.execute("ALTER ROLE " + username + " WITH OPTIONS = {'grant_audit_whitelist_for_" + operation + "' : '" + resource + "'}");
    }

    void expectNoAuditLog()
    {
        verify(mockAuditAppender, never()).doAppend(any(ILoggingEvent.class));
    }

    void expectAuditLogContainEntryForUser(String auditOperation, String username)
    {
        ArgumentCaptor<ILoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAuditAppender, times(1)).doAppend(loggingEventCaptor.capture());
        List<ILoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertThat(loggingEvents.get(0).getFormattedMessage()).isEqualTo(expectedAuditEntry(auditOperation, username, "ATTEMPT"));
    }

    private String expectedAuditEntry(String auditOperation, String username, String status)
    {
        String obfuscatedOperation = auditOperation.replaceAll("secret", "*****");
        return String.format("client:'127.0.0.1'|user:'%s'|status:'%s'|operation:'%s'", username, status, obfuscatedOperation);
    }

    Cluster createCluster(String username, String secret)
    {
        return cdt.createCluster(username, secret);
    }
}
