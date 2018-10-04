//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.integration;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.exceptions.UnauthorizedException;
import net.jcip.annotations.NotThreadSafe;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * This test class provides a functional integration test with Cassandra itself.
 *
 * This is achieved by starting an embedded Cassandra server where the audit plug-in is used. Then each test case send
 * different requests and capture and verify that expected audit entries are produced.
 *
 * This class also works as a safe guard to changes on the public API of the plug-in. The plug-in has three different
 * interfaces that together make out its public API. It is Cassandra itself, the configuration, and the audit messages
 * sent to the supported log back ends. When a change is necessary here it indicates that the major or minor version
 * should be bumped as well. This class is mostly focused to verify that a correct behavior when managing white lists
 * and permission for that.
 */
@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ITVerifyWhitelistManagement
{
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
        "CREATE KEYSPACE ecks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false"));
        session.execute(new SimpleStatement(
        "CREATE TABLE ecks.ectbl (partk int PRIMARY KEY, clustk text, value text)"));

        session.execute(new SimpleStatement(
        "CREATE ROLE ordinary_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON TABLE ecks.ectbl TO ordinary_user"));

        session.execute(new SimpleStatement(
        "CREATE ROLE create_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON TABLE ecks.ectbl TO create_user"));
        session.execute(new SimpleStatement(
        "GRANT CREATE ON ALL ROLES TO create_user"));

        session.execute(new SimpleStatement(
        "CREATE ROLE other_user WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = false"));

        session.execute(new SimpleStatement(
        "CREATE ROLE authorized_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON TABLE ecks.ectbl TO authorized_user"));
        session.execute(new SimpleStatement(
        "GRANT CREATE ON ALL ROLES TO authorized_user"));
        session.execute(new SimpleStatement(
        "GRANT AUTHORIZE ON ALL KEYSPACES TO authorized_user"));

        session.execute(new SimpleStatement(
        "CREATE ROLE uber_role WITH LOGIN = false"));
        session.execute(new SimpleStatement(
        "GRANT ALTER ON ALL ROLES TO uber_role"));

        session.execute(new SimpleStatement(
        "CREATE ROLE uber_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT uber_role TO uber_user"));

        session.execute(new SimpleStatement(
        "CREATE ROLE super_user WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true"));

        session.execute(new SimpleStatement(
        "CREATE ROLE whitelist_role WITH LOGIN = false AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        session.execute(new SimpleStatement(
        "CREATE ROLE trusted_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON TABLE ecks.ectbl TO trusted_user"));
        session.execute(new SimpleStatement(
        "GRANT AUTHORIZE ON ROLE whitelist_role TO trusted_user"));
        session.execute(new SimpleStatement(
        "GRANT CREATE ON ALL ROLES TO trusted_user"));
    }

    @After
    public void after()
    {
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS temporary_user"));
    }

    @AfterClass
    public static void afterClass()
    {
        session.execute(new SimpleStatement("DROP KEYSPACE IF EXISTS ecks"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS whitelist_role"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS ordinary_user"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS create_user"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS authorized_user"));
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS super_user"));

        session.close();
        cluster.close();
    }

    @Test
    public void testOrdinaryCanUpdatePassword()
    {
        try (Cluster privateCluster = cdt.createCluster("ordinary_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "ALTER ROLE ordinary_user WITH PASSWORD = 'secret'"));
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void testOrdinaryUserCannotWhitelistHimself()
    {
        try (Cluster privateCluster = cdt.createCluster("ordinary_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "ALTER ROLE ordinary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void testCreateUserCannotCreateWhitelistedUser()
    {
        try (Cluster privateCluster = cdt.createCluster("create_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        }
    }

    @Test
    public void testAuthorizedUserCanCreateWhitelisted()
    {
        try (Cluster privateCluster = cdt.createCluster("authorized_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        }
    }

    @Test
    public void testAuthorizedUserCanGrantWhitelistToOther()
    {
        try (Cluster privateCluster = cdt.createCluster("authorized_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "ALTER ROLE other_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void testAuthorizedUserCanNotAlterPasswordOfOther()
    {
        try (Cluster privateCluster = cdt.createCluster("authorized_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "ALTER ROLE other_user WITH PASSWORD = 'secret'"));
        }
    }

    @Test
    public void testUberUserCanAlterPasswordOfOther()
    {
        try (Cluster privateCluster = cdt.createCluster("uber_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "ALTER ROLE other_user WITH PASSWORD = 'secret'"));
        }
    }

    @Test
    public void testSuperUserCanCreateWhitelistedOnData()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        }
    }

    @Test
    public void testSuperUserCanCreateWhitelistedOnDataPartly()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/ectbl' }"));
        }
    }

    @Test
    public void testSuperUserCanCreateWhitelistedOnNonexistingKeyspace()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/unknownks' }"));
        }
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotCreateWhitelistedOnInvalidKeyspace()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/unknownk%s' }"));
        }
    }

    @Test
    public void testSuperUserCanCreateWhitelistedOnNonexistingTable()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/unknowntbl' }"));
        }
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotCreateWhitelistedOnInvalidTable()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/unknown?tbl' }"));
        }
    }

    @Test
    public void testSuperUserCanCreateWhitelistedOnConnection()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections' }"));
        }
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotCreateWhitelistedOnConnectionWithName()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections/native' }"));
        }
    }

    @Test
    public void testSuperUserCanCreateWhitelistedOnRoles()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles' }"));
        }
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotCreateWhitelistedOnInvalidDataResource()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/unknowntbl/invalid' }"));
        }
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotCreateWhitelistedOnInvalidRoleResource()
    {
        try (Cluster privateCluster = cdt.createCluster("super_user", "secret");
             Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
            "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles/t%s' }"));
        }
    }

    @Test
    public void testTrustedUserCanDelegateWhitelistedRole()
    {
        try (Cluster privateCluster = cdt.createCluster("trusted_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true"));
            privateSession.execute(new SimpleStatement(
                    "GRANT whitelist_role TO temporary_user"));
        }
    }
}
