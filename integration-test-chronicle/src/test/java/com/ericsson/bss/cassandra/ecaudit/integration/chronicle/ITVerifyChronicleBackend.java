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
package com.ericsson.bss.cassandra.ecaudit.integration.chronicle;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.eclog.QueueReader;
import com.ericsson.bss.cassandra.ecaudit.eclog.ToolOptions;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;
import net.jcip.annotations.NotThreadSafe;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ITVerifyChronicleBackend
{
    private static final String SUITE_SUPER_USER = "superchronicle";
    private static final String SUITE_TEST_USER_PREFIX = "chroniclerole";
    private static AtomicInteger usernameNumber = new AtomicInteger();

    private static CassandraDaemonForAuditTest cdt;
    private static Cluster superCluster;
    private static Session superSession;

    private static String testUsername;
    private static Cluster testCluster;
    private static Session testSession;


    private static QueueReader reader;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        cdt = CassandraDaemonForAuditTest.getInstance();

        try (Cluster cassandraCluster = cdt.createCluster();
             Session cassandraSession = cassandraCluster.connect())
        {
            cassandraSession.execute(new SimpleStatement(
            "CREATE ROLE " + SUITE_SUPER_USER + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_execute'  : 'connections' }"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_alter' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_drop' : 'roles'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_alter' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_drop' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data'}"));
            cassandraSession.execute(new SimpleStatement(
            "ALTER ROLE " + SUITE_SUPER_USER + " WITH OPTIONS = { 'grant_audit_whitelist_for_modify' : 'data'}"));
        }

        superCluster = cdt.createCluster(SUITE_SUPER_USER, "secret");
        superSession = superCluster.connect();

        testUsername = givenUniqueSuperuserWithMinimalWhitelist();
        testCluster = cdt.createCluster(testUsername, "secret");
        testSession = testCluster.connect();

        ToolOptions options = ToolOptions
                              .builder()
                              .withPath(CassandraDaemonForAuditTest.getInstance().getAuditDirectory())
                              .build();
        reader = new QueueReader(options);
    }

    @Before
    public void before()
    {
        // Drain the log
        getRecords();
    }

    @After
    public void after()
    {
        resetTestUserWithMinimalWhitelist(testUsername);
    }

    @AfterClass
    public static void afterClass()
    {
        testSession.close();
        testCluster.close();

        for (int i = 0; i < usernameNumber.get(); i++)
        {
            superSession.execute(new SimpleStatement("DROP ROLE IF EXISTS " + SUITE_TEST_USER_PREFIX + i));
        }
        superSession.close();
        superCluster.close();

        try (Cluster cassandraCluster = cdt.createCluster();
             Session cassandraSession = cassandraCluster.connect())
        {
            cassandraSession.execute(new SimpleStatement("DROP ROLE IF EXISTS " + SUITE_SUPER_USER));
        }
    }

    @Test
    public void simpleUpdateIsLogged()
    {
        givenTable("ks1", "tbl");
        String username = givenSuperuserWithMinimalWhitelist();

        testSession.execute("UPDATE ks1.tbl SET value = 'hepp' WHERE key = 88");

        thenChronicleLogContainEntryForUser("UPDATE ks1.tbl SET value = 'hepp' WHERE key = 88", username);
    }

    @Test
    public void preparedInsertIsLogged()
    {
        givenTable("ks2", "tbl");
        String username = givenSuperuserWithMinimalWhitelist();

        PreparedStatement preparedStatement = testSession.prepare("INSERT INTO ks2.tbl (key, value) VALUES (?, ?)");
        testSession.execute(preparedStatement.bind(5, "hepp"));

        thenChronicleLogContainEntryForUser("INSERT INTO ks2.tbl (key, value) VALUES (?, ?)[5, 'hepp']", username);
    }

    private void thenChronicleLogContainEntryForUser(String operation, String username)
    {
        List<AuditRecord> records = getRecords();
        assertThat(records).hasSize(1);

        AuditRecord record = records.get(0);
        assertThat(record.getOperation().getOperationString()).isEqualTo(operation);
        assertThat(record.getUser()).isEqualTo(username);
        assertThat(record.getStatus()).isEqualTo(Status.ATTEMPT);
        assertThat(record.getClientAddress()).isEqualTo(InetAddress.getLoopbackAddress());
        assertThat(record.getTimestamp()).isLessThanOrEqualTo(System.currentTimeMillis());
        assertThat(record.getTimestamp()).isGreaterThan(System.currentTimeMillis() - 30_000);
    }

    private List<AuditRecord> getRecords()
    {
        List<AuditRecord> records = new ArrayList<>();

        while (reader.hasRecordAvailable())
        {
            AuditRecord record = reader.nextRecord();
            records.add(record);
        }

        return records;
    }

    private void givenKeyspace(String keyspace)
    {
        superSession.execute(new SimpleStatement(
        "CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false"));
    }

    private void givenTable(String keyspace, String table)
    {
        givenKeyspace(keyspace);
        superSession.execute(new SimpleStatement(
        "CREATE TABLE IF NOT EXISTS " + keyspace + "." + table + " (key int PRIMARY KEY, value text)"));
    }

    private static String givenSuperuserWithMinimalWhitelist()
    {
        return testUsername;
    }

    private static String givenUniqueSuperuserWithMinimalWhitelist()
    {
        String username = SUITE_TEST_USER_PREFIX + usernameNumber.getAndIncrement();
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
}
