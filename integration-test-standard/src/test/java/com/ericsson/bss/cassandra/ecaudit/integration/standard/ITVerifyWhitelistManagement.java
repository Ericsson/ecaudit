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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.exceptions.UnauthorizedException;
import com.ericsson.bss.cassandra.ecaudit.test.daemon.CassandraDaemonForAuditTest;
import net.jcip.annotations.NotThreadSafe;
import org.apache.cassandra.auth.Permission;
import org.mockito.junit.MockitoJUnitRunner;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

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

    private static final String SUPER_USER = "super_user";
    private static Cluster superCluster;
    private static Session superSession;

    private static final String AUTHORIZED_USER = "authorized_user";
    private static Cluster authorizedCluster;
    private static Session authorizedSession;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        cdt = CassandraDaemonForAuditTest.getInstance();

        cluster = cdt.createCluster();
        session = cluster.connect();

        session.execute(new SimpleStatement(
        "CREATE KEYSPACE ecks_itvwm WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false"));
        session.execute(new SimpleStatement(
        "CREATE TABLE ecks_itvwm.ectbl (partk int PRIMARY KEY, clustk text, value text)"));

        session.execute(new SimpleStatement(
        "CREATE ROLE ordinary_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON TABLE ecks_itvwm.ectbl TO ordinary_user"));

        session.execute(new SimpleStatement(
        "CREATE ROLE create_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON TABLE ecks_itvwm.ectbl TO create_user"));
        session.execute(new SimpleStatement(
        "GRANT CREATE ON ALL ROLES TO create_user"));

        session.execute(new SimpleStatement(
        "CREATE ROLE other_user WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = false"));

        session.execute(new SimpleStatement(
        "CREATE ROLE authorized_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON TABLE ecks_itvwm.ectbl TO authorized_user"));
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
        "CREATE ROLE whitelist_role WITH LOGIN = false"));
        session.execute(new SimpleStatement(
        "ALTER ROLE whitelist_role WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        session.execute(new SimpleStatement(
        "CREATE ROLE trusted_user WITH PASSWORD = 'secret' AND LOGIN = true"));
        session.execute(new SimpleStatement(
        "GRANT SELECT ON TABLE ecks_itvwm.ectbl TO trusted_user"));
        session.execute(new SimpleStatement(
        "GRANT AUTHORIZE ON ROLE whitelist_role TO trusted_user"));
        session.execute(new SimpleStatement(
        "GRANT CREATE ON ALL ROLES TO trusted_user"));

        superCluster = cdt.createCluster(SUPER_USER, "secret");
        superSession = superCluster.connect();

        authorizedCluster = cdt.createCluster(AUTHORIZED_USER, "secret");
        authorizedSession = authorizedCluster.connect();
    }

    @After
    public void after()
    {
        session.execute(new SimpleStatement("DROP ROLE IF EXISTS temporary_user"));
    }

    @AfterClass
    public static void afterClass()
    {
        if(session != null)
        {
            session.execute(new SimpleStatement("DROP KEYSPACE IF EXISTS ecks_itvwm"));
            session.execute(new SimpleStatement("DROP ROLE IF EXISTS whitelist_role"));
            session.execute(new SimpleStatement("DROP ROLE IF EXISTS ordinary_user"));
            session.execute(new SimpleStatement("DROP ROLE IF EXISTS create_user"));
            session.execute(new SimpleStatement("DROP ROLE IF EXISTS authorized_user"));
            session.execute(new SimpleStatement("DROP ROLE IF EXISTS super_user"));

            session.close();
        }
        if(cluster != null)
        {
            cluster.close();
        }

        if(superSession != null)
        {
            superSession.close();
        }
        if(superCluster != null)
        {
            superCluster.close();
        }

        if(authorizedSession != null)
        {
            authorizedSession.close();
        }
        if(authorizedCluster != null)
        {
            authorizedCluster.close();
        }
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
    public void testOrdinaryUserCannotGrantWhitelistHimself()
    {
        try (Cluster privateCluster = cdt.createCluster("ordinary_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            privateSession.execute(new SimpleStatement(
                    "ALTER ROLE ordinary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/data' }"));
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void testCreateUserCannotWhitelistUser()
    {
        try (Cluster privateCluster = cdt.createCluster("create_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            given_temporary_user(privateSession);
            privateSession.execute(new SimpleStatement(
            "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        }
    }

    @Test(expected = InvalidQueryException.class)
    public void testAuthorizedUserCannotWhitelistUserAtCreate()
    {
        given_temporary_user(authorizedSession);
        authorizedSession.execute(new SimpleStatement(
        "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
    }

    @Test
    public void testAuthorizedUserCanGrantWhitelistToHimself()
    {
        authorizedSession.execute(new SimpleStatement(
        "ALTER ROLE authorized_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));

        assertRoleOperations("authorized_user", "data", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test
    public void testAuthorizedUserCanGrantWhitelistToOther()
    {
        authorizedSession.execute(new SimpleStatement(
        "ALTER ROLE other_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));

        assertRoleOperations("other_user", "data", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test
    public void testAuthorizedUserCanGrantPermissionDerivedWhitelistToHimself()
    {
        authorizedSession.execute(new SimpleStatement(
        "ALTER ROLE authorized_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/data' }"));

        assertRoleOperations("authorized_user", "grants/data", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test
    public void testAuthorizedUserCanGrantPermissionDerivedWhitelistToOthers()
    {
        authorizedSession.execute(new SimpleStatement(
        "ALTER ROLE other_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/data' }"));

        assertRoleOperations("other_user", "grants/data", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test(expected = UnauthorizedException.class)
    public void testAuthorizedUserCanNotAlterPasswordOfOther()
    {
        authorizedSession.execute(new SimpleStatement(
        "ALTER ROLE other_user WITH PASSWORD = 'secret'"));
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
    public void testSuperUserCanWhitelistOnData()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data' }"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_modify' : 'data' }"));

        assertRoleOperations("temporary_user", "data", asList("SELECT", "MODIFY"));
    }

    @Test
    public void testSuperUserCanWhitelistOnDataPartly()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks_itvwm/ectbl' }"));

        assertRoleOperations("temporary_user", "data/ecks_itvwm/ectbl", asList("ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test
    public void testSuperUserCanWhitelistOnNonexistingKeyspace()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/unknownks' }"));

        assertRoleOperations("temporary_user", "data/unknownks", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnInvalidKeyspace()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/unknownk%s' }"));
    }

    @Test
    public void testSuperUserCanWhitelistOnNonexistingTable()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks_itvwm/unknowntbl' }"));

        assertRoleOperations("temporary_user", "data/ecks_itvwm/unknowntbl", asList("ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnInvalidTable()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks_itvwm/unknown?tbl' }"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistTwoOperationsInOneStatement()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/ecks_itvwm' ,'grant_audit_whitelist_for_modify' : 'data/ecks_itvwm'}"));
    }

    @Test
    public void testSuperUserCanWhitelistOnConnection()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections' }"));

        assertRoleOperations("temporary_user", "connections", asList("AUTHORIZE", "EXECUTE"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnConnectionWithName()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections/native' }"));
    }

    @Test
    public void testSuperUserCanWhitelistOnGrant()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants' }"));

        List<String> allPermissions = Permission.ALL.stream().map(Enum::name).collect(Collectors.toList());
        assertRoleOperations("temporary_user", "grants", allPermissions);
    }

    @Test
    public void testSuperUserCanWhitelistOnTableDataGrant()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/data/ks/tb' }"));
        assertRoleOperations("temporary_user", "grants/data/ks/tb", asList("ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnGrantWithInvalidResource()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/non_existing_resource' }"));
    }

    @Test
    public void testSuperUserCanWhitelistOnRoles()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles' }"));

        assertRoleOperations("temporary_user", "roles", asList("CREATE", "ALTER", "AUTHORIZE", "DESCRIBE", "DROP"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnInvalidDataResource()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks_itvwm/unknowntbl/invalid' }"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnInvalidRoleResource()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles/t%s' }"));
    }

    @Test
    public void testTrustedUserCanDelegateWhitelistedRole()
    {
        try (Cluster privateCluster = cdt.createCluster("trusted_user", "secret");
                Session privateSession = privateCluster.connect())
        {
            given_temporary_user(privateSession);
            privateSession.execute(new SimpleStatement(
                    "GRANT whitelist_role TO temporary_user"));
        }
    }

    @Test
    public void testRevokeOperations()
    {
        given_temporary_user(superSession);
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'revoke_audit_whitelist_for_modify' : 'data' }"));
        superSession.execute(new SimpleStatement(
        "ALTER ROLE temporary_user WITH OPTIONS = { 'revoke_audit_whitelist_for_drop' : 'data' }"));

        assertRoleOperations("temporary_user", "data", asList("CREATE", "ALTER", "SELECT", "AUTHORIZE"));
    }

    private void given_temporary_user(Session privateSession)
    {
        privateSession.execute(new SimpleStatement(
        "CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true"));
    }

    private void assertRoleOperations(String roleName, String resource, List<String> expectedOperations)
    {
        ResultSet result = superSession.execute(new SimpleStatement("LIST ROLES OF " + roleName));
        Map<String, String> optionsMap = result.one().getMap("options", String.class, String.class);

        String expectedKey = "AUDIT WHITELIST ON " + resource;
        assertThat(optionsMap).containsKey(expectedKey);

        String operationsString = optionsMap.get(expectedKey);
        List<String> operations = Splitter.on(",").trimResults().splitToList(operationsString);
        assertThat(operations).hasSameElementsAs(expectedOperations);
    }
}
