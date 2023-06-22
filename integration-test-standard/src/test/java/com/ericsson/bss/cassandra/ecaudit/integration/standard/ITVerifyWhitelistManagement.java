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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.core.servererrors.UnauthorizedException;
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
    private static CqlSession session;

    private static final String SUPER_USER = "super_user";
    private static CqlSession superSession;

    private static final String AUTHORIZED_USER = "authorized_user";
    private static CqlSession authorizedSession;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        cdt = CassandraDaemonForAuditTest.getInstance();

        session = cdt.createSession();

        session.execute("CREATE KEYSPACE ecks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false");
        session.execute("CREATE TABLE ecks.ectbl (partk int PRIMARY KEY, clustk text, value text)");

        session.execute("CREATE ROLE ordinary_user WITH PASSWORD = 'secret' AND LOGIN = true");
        session.execute("GRANT SELECT ON TABLE ecks.ectbl TO ordinary_user");

        session.execute("CREATE ROLE create_user WITH PASSWORD = 'secret' AND LOGIN = true");
        session.execute("GRANT SELECT ON TABLE ecks.ectbl TO create_user");
        session.execute("GRANT CREATE ON ALL ROLES TO create_user");

        session.execute("CREATE ROLE other_user WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = false");

        session.execute("CREATE ROLE authorized_user WITH PASSWORD = 'secret' AND LOGIN = true");
        session.execute("GRANT SELECT ON TABLE ecks.ectbl TO authorized_user");
        session.execute("GRANT CREATE ON ALL ROLES TO authorized_user");
        session.execute("GRANT AUTHORIZE ON ALL KEYSPACES TO authorized_user");

        session.execute("CREATE ROLE uber_role WITH LOGIN = false");
        session.execute("GRANT ALTER ON ALL ROLES TO uber_role");

        session.execute("CREATE ROLE uber_user WITH PASSWORD = 'secret' AND LOGIN = true");
        session.execute("GRANT uber_role TO uber_user");

        session.execute("CREATE ROLE super_user WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true");

        session.execute("CREATE ROLE whitelist_role WITH LOGIN = false");
        session.execute("ALTER ROLE whitelist_role WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }");
        session.execute("CREATE ROLE trusted_user WITH PASSWORD = 'secret' AND LOGIN = true");
        session.execute("GRANT SELECT ON TABLE ecks.ectbl TO trusted_user");
        session.execute("GRANT AUTHORIZE ON ROLE whitelist_role TO trusted_user");
        session.execute("GRANT CREATE ON ALL ROLES TO trusted_user");

        superSession = cdt.createSession(SUPER_USER, "secret");

        authorizedSession = cdt.createSession(AUTHORIZED_USER, "secret");
    }

    @After
    public void after()
    {
        session.execute("DROP ROLE IF EXISTS temporary_user");
    }

    @AfterClass
    public static void afterClass()
    {
        authorizedSession.close();

        superSession.close();

        session.execute("DROP KEYSPACE IF EXISTS ecks");
        session.execute("DROP ROLE IF EXISTS whitelist_role");
        session.execute("DROP ROLE IF EXISTS ordinary_user");
        session.execute("DROP ROLE IF EXISTS create_user");
        session.execute("DROP ROLE IF EXISTS authorized_user");
        session.execute("DROP ROLE IF EXISTS super_user");

        session.close();
    }

    @Test
    public void testOrdinaryCanUpdatePassword()
    {
        try (CqlSession privateSession = cdt.createSession("ordinary_user", "secret"))
        {
            privateSession.execute("ALTER ROLE ordinary_user WITH PASSWORD = 'secret'");
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void testOrdinaryUserCannotWhitelistHimself()
    {
        try (CqlSession privateSession = cdt.createSession("ordinary_user", "secret"))
        {
            privateSession.execute("ALTER ROLE ordinary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }");
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void testOrdinaryUserCannotGrantWhitelistHimself()
    {
        try (CqlSession privateSession = cdt.createSession("ordinary_user", "secret"))
        {
            privateSession.execute("ALTER ROLE ordinary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/data' }");
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void testCreateUserCannotWhitelistUser()
    {
        try (CqlSession privateSession = cdt.createSession("create_user", "secret"))
        {
            given_temporary_user(privateSession);
            privateSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }");
        }
    }

    @Test(expected = InvalidQueryException.class)
    public void testAuthorizedUserCannotWhitelistUserAtCreate()
    {
        given_temporary_user(authorizedSession);
        authorizedSession.execute("CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }");
    }

    @Test
    public void testAuthorizedUserCanGrantWhitelistToHimself()
    {
        authorizedSession.execute("ALTER ROLE authorized_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }");

        assertRoleOperations("authorized_user", "data", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test
    public void testAuthorizedUserCanGrantWhitelistToOther()
    {
        authorizedSession.execute("ALTER ROLE other_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }");

        assertRoleOperations("other_user", "data", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test
    public void testAuthorizedUserCanGrantPermissionDerivedWhitelistToHimself()
    {
        authorizedSession.execute("ALTER ROLE authorized_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/data' }");

        assertRoleOperations("authorized_user", "grants/data", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test
    public void testAuthorizedUserCanGrantPermissionDerivedWhitelistToOthers()
    {
        authorizedSession.execute("ALTER ROLE other_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/data' }");

        assertRoleOperations("other_user", "grants/data", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test(expected = UnauthorizedException.class)
    public void testAuthorizedUserCanNotAlterPasswordOfOther()
    {
        authorizedSession.execute("ALTER ROLE other_user WITH PASSWORD = 'secret'");
    }

    @Test
    public void testUberUserCanAlterPasswordOfOther()
    {
        try (CqlSession privateSession = cdt.createSession("uber_user", "secret"))
        {
            privateSession.execute("ALTER ROLE other_user WITH PASSWORD = 'secret'");
        }
    }

    @Test
    public void testSuperUserCanWhitelistOnData()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data' }");
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_modify' : 'data' }");

        assertRoleOperations("temporary_user", "data", asList("SELECT", "MODIFY"));
    }

    @Test
    public void testSuperUserCanWhitelistOnDataPartly()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/ectbl' }");

        assertRoleOperations("temporary_user", "data/ecks/ectbl", asList("ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test
    public void testSuperUserCanWhitelistOnNonexistingKeyspace()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/unknownks' }");

        assertRoleOperations("temporary_user", "data/unknownks", asList("CREATE", "ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnInvalidKeyspace()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/unknownk%s' }");
    }

    @Test
    public void testSuperUserCanWhitelistOnNonexistingTable()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/unknowntbl' }");

        assertRoleOperations("temporary_user", "data/ecks/unknowntbl", asList("ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnInvalidTable()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/unknown?tbl' }");
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistTwoOperationsInOneStatement()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/ecks' ,'grant_audit_whitelist_for_modify' : 'data/ecks'}");
    }

    @Test
    public void testSuperUserCanWhitelistOnConnection()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections' }");

        assertRoleOperations("temporary_user", "connections", asList("AUTHORIZE", "EXECUTE"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnConnectionWithName()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections/native' }");
    }

    @Test
    public void testSuperUserCanWhitelistOnGrant()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants' }");

        List<String> allPermissions = Permission.ALL.stream().map(Enum::name).collect(Collectors.toList());
        assertRoleOperations("temporary_user", "grants", allPermissions);
    }

    @Test
    public void testSuperUserCanWhitelistOnTableDataGrant()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/data/ks/tb' }");
        assertRoleOperations("temporary_user", "grants/data/ks/tb", asList("ALTER", "DROP", "SELECT", "MODIFY", "AUTHORIZE"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnGrantWithInvalidResource()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'grants/non_existing_resource' }");
    }

    @Test
    public void testSuperUserCanWhitelistOnRoles()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles' }");

        assertRoleOperations("temporary_user", "roles", asList("CREATE", "ALTER", "AUTHORIZE", "DESCRIBE", "DROP"));
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnInvalidDataResource()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/ecks/unknowntbl/invalid' }");
    }

    @Test (expected = InvalidQueryException.class)
    public void testSuperUserCanNotWhitelistOnInvalidRoleResource()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles/t%s' }");
    }

    @Test
    public void testTrustedUserCanDelegateWhitelistedRole()
    {
        try (CqlSession privateSession = cdt.createSession("trusted_user", "secret"))
        {
            given_temporary_user(privateSession);
            privateSession.execute("GRANT whitelist_role TO temporary_user");
        }
    }

    @Test
    public void testRevokeOperations()
    {
        given_temporary_user(superSession);
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' }");
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'revoke_audit_whitelist_for_modify' : 'data' }");
        superSession.execute("ALTER ROLE temporary_user WITH OPTIONS = { 'revoke_audit_whitelist_for_drop' : 'data' }");

        assertRoleOperations("temporary_user", "data", asList("CREATE", "ALTER", "SELECT", "AUTHORIZE"));
    }

    private void given_temporary_user(CqlSession privateSession)
    {
        privateSession.execute("CREATE ROLE temporary_user WITH PASSWORD = 'secret' AND LOGIN = true");
    }

    private void assertRoleOperations(String roleName, String resource, List<String> expectedOperations)
    {
        ResultSet result = superSession.execute("LIST ROLES OF " + roleName);
        Map<String, String> optionsMap = result.one().getMap("options", String.class, String.class);

        String expectedKey = "AUDIT WHITELIST ON " + resource;
        assertThat(optionsMap).containsKey(expectedKey);

        String operationsString = optionsMap.get(expectedKey);
        List<String> operations = Splitter.on(",").trimResults().splitToList(operationsString);
        assertThat(operations).hasSameElementsAs(expectedOperations);
    }
}
