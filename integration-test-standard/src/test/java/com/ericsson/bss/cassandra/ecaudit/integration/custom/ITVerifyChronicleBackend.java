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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.datastax.driver.core.exceptions.AuthenticationException;
import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.common.record.StoredAuditRecord;
import com.ericsson.bss.cassandra.ecaudit.eclog.QueueReader;
import com.ericsson.bss.cassandra.ecaudit.eclog.ToolOptions;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;
import net.jcip.annotations.NotThreadSafe;
import net.openhft.chronicle.queue.RollCycles;
import org.apache.cassandra.utils.FBUtilities;
import org.assertj.core.data.Offset;
import org.assertj.core.data.Percentage;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ITVerifyChronicleBackend
{
    private static final String SUITE_SUPER_USER = "superchronicle";
    private static final String SUITE_TEST_USER_PREFIX = "chroniclerole";
    private static final AtomicInteger usernameNumber = new AtomicInteger();

    private static CassandraDaemonForAuditTest cdt;
    private static Cluster superCluster;
    private static Session superSession;

    private static String testUsername;
    private static Cluster testCluster;
    private static Session testSession;

    private static QueueReader reader;
    private static AuditLogger customLogger;

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

        Path auditDirectory = CassandraDaemonForAuditTest.getInstance().getAuditDirectory();
        ToolOptions options = ToolOptions
                              .builder()
                              .withPath(auditDirectory)
                              .withRollCycle(RollCycles.TEST_SECONDLY)
                              .build();
        reader = new QueueReader(options);

        // Configure custom chronicle logger
        Map<String, String> configParameters = new HashMap<>();
        configParameters.put("log_dir", auditDirectory.toString());
        configParameters.put("roll_cycle", "TEST_SECONDLY");
        configParameters.put("max_log_size", "314572800"); // 300MB
        customLogger = new ChronicleAuditLogger(configParameters);
        // Add custom logger
        AuditAdapter.getInstance().getAuditor().addLogger(customLogger);
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
        AuditAdapter.getInstance().getAuditor().removeLogger(customLogger);

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
    public void testFailedAuthenticationRequest()
    {
        assertThatExceptionOfType(AuthenticationException.class)
        .isThrownBy(() -> cdt.createCluster("user", "password").connect())
        .withMessageContaining("Authentication error");

        List<StoredAuditRecord> records = waitAndGetRecords();
        assertThat(records).hasSize(2);
        assertAuthRecord(records.get(0), "user", Status.ATTEMPT, "Authentication attempt");
        assertAuthRecord(records.get(1), "user", Status.FAILED, "Authentication failed");
    }

    private void assertAuthRecord(StoredAuditRecord record, String expectedUser, Status expectedStatus, String expectedOperation)
    {
        assertThat(record.getTimestamp().get()).isCloseTo(System.currentTimeMillis(), Offset.offset(30_000L));
        assertThat(record.getClientAddress()).isEmpty(); // Client address is not available in C2.2.x
        assertThat(record.getClientPort()).isEmpty(); // Client port is not available in C2.2.x
        assertThat(record.getCoordinatorAddress()).contains(FBUtilities.getBroadcastAddress());
        assertThat(record.getUser()).contains(expectedUser);
        assertThat(record.getStatus()).contains(expectedStatus);
        assertThat(record.getOperation()).contains(expectedOperation);
        assertThat(record.getNakedOperation()).isEmpty();
    }

    @Test
    public void simpleUpdateIsLogged()
    {
        givenTable("ks1", "tbl");
        String username = getSuperuserWithMinimalWhitelist();

        testSession.execute("UPDATE ks1.tbl SET value = 'hepp' WHERE key = 88");

        thenChronicleLogContainEntryForUser("UPDATE ks1.tbl SET value = 'hepp' WHERE key = 88", username);
    }

    @Test
    public void preparedInsertIsLogged()
    {
        givenTable("ks2", "tbl");
        String username = getSuperuserWithMinimalWhitelist();

        PreparedStatement preparedStatement = testSession.prepare("INSERT INTO ks2.tbl (key, value) VALUES (?, ?)");
        testSession.execute(preparedStatement.bind(5, "hepp"));

        thenChronicleLogContainEntryForUser("INSERT INTO ks2.tbl (key, value) VALUES (?, ?)[5, 'hepp']", username);
    }

    @Test
    public void sizeIsCloseToThreshold() throws Exception
    {
        givenTable("ks3", "tbl");

        for (int i = 0; i < 10; i++)
        {
            Thread.sleep(300);
            testSession.execute("UPDATE ks3.tbl SET value = 'hepp' WHERE key = 88");
        }

        verifyAuditSize();
    }

    private void verifyAuditSize() throws IOException
    {
        long actualSize = Files.walk(cdt.getAuditDirectory())
                               .map(Path::toFile)
                               .filter(File::isFile)
                               .mapToLong(File::length)
                               .sum();

        assertThat(actualSize).isCloseTo(300L * 1024 * 1024, Percentage.withPercentage(20));
    }

    private void thenChronicleLogContainEntryForUser(String operation, String username)
    {
        List<StoredAuditRecord> records = waitAndGetRecords();
        assertThat(records).hasSize(1);

        StoredAuditRecord record = records.get(0);
        assertThat(record.getOperation()).contains(operation);
        assertThat(record.getNakedOperation()).isEmpty();
        assertThat(record.getUser()).contains(username);
        assertThat(record.getStatus()).contains(Status.ATTEMPT);
        assertThat(record.getClientAddress()).contains(InetAddress.getLoopbackAddress());
        assertThat(record.getClientPort().get()).isGreaterThan(0);
        assertThat(record.getCoordinatorAddress()).contains(FBUtilities.getBroadcastAddress());
        assertThat(record.getTimestamp().get()).isLessThanOrEqualTo(System.currentTimeMillis());
        assertThat(record.getTimestamp().get()).isGreaterThan(System.currentTimeMillis() - 30_000);
    }

    private List<StoredAuditRecord> waitAndGetRecords()
    {
        try
        {
            Thread.sleep(100);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("Interrupted during delay", e);
        }

        return getRecords();
    }

    private List<StoredAuditRecord> getRecords()
    {
        List<StoredAuditRecord> records = new ArrayList<>();

        while (reader.hasRecordAvailable())
        {
            StoredAuditRecord record = reader.nextRecord();
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

    private static String getSuperuserWithMinimalWhitelist()
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
