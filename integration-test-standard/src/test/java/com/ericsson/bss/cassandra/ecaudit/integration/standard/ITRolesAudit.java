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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.UnauthorizedException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@RunWith(JUnitParamsRunner.class)
public class ITRolesAudit
{
    private static CassandraClusterFacade ccf = new CassandraClusterFacade();
    private static final String USER = "role_user";
    private static final String ROLE = "role_role";

    private static String testUsername;
    private static Cluster testCluster;
    private static Session testSession;

    private static String basicUsername;
    private static Cluster basicCluster;
    private static Session basicSession;

    @BeforeClass
    public static void beforeClass()
    {
        ccf.setup();
        testUsername = ccf.givenUniqueSuperuserWithMinimalWhitelist();
        testCluster = ccf.createCluster(testUsername);
        testSession = testCluster.connect();

        basicUsername = ccf.givenUniqueBasicUserWithMinimalWhitelist();
        basicCluster = ccf.createCluster(basicUsername);
        basicSession = basicCluster.connect();

        ccf.givenBasicUser(ROLE);
    }

    @Before
    public void before()
    {
        ccf.before();
    }

    @After
    public void after()
    {
        ccf.after();
        ccf.resetTestUserWithMinimalWhitelist(testUsername);
        ccf.resetTestUserWithMinimalWhitelist(basicUsername);
    }

    @AfterClass
    public static void afterClass()
    {
        basicSession.close();
        basicCluster.close();
        testSession.close();
        testCluster.close();
        ccf.tearDown();
    }

    @SuppressWarnings("unused")
    private Object[] parametersForRoleStatements()
    {
        return new Object[]{
            new Object[]{ "CREATE ROLE IF NOT EXISTS " + USER + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true", "create", "roles" },
            new Object[]{ "ALTER ROLE " + USER + " WITH LOGIN = false", "alter", "roles/" + USER },
            new Object[]{ "GRANT " + ROLE + " TO " + USER, "authorize", "roles/" + ROLE },
            new Object[]{ "REVOKE " + ROLE + " FROM " + USER, "authorize", "roles/" + ROLE},
            new Object[]{ "LIST ROLES OF " + USER, "describe", "roles" },
            new Object[]{ "DROP ROLE " + USER, "drop", "roles/" + USER },
        };
    }

    @Test
    @Parameters(method = "parametersForRoleStatements")
    @SuppressWarnings("unused")
    public void statementIsLogged(String statement, String operation, String resource)
    {
        testSession.execute(statement);
        ccf.thenAuditLogContainEntryForUser(statement, testUsername);
    }

    @Test
    @Parameters(method = "parametersForRoleStatements")
    public void statementIsWhitelisted(String statement, String operation, String resource)
    {
        ccf.givenRoleIsWhitelistedForOperationOnResource(testUsername, operation, resource);
        testSession.execute(statement);
        ccf.thenAuditLogContainNothingForUser();
    }

    @Test
    @Parameters(method = "parametersForRoleStatements")
    public void statementIsGrantWhitelisted(String statement, String operation, String resource)
    {
        ccf.givenRoleIsWhitelistedForOperationOnResource(testUsername, operation, "grants/" + resource);
        testSession.execute(statement);
        ccf.thenAuditLogContainNothingForUser();
    }

    @Test
    @Parameters(method = "parametersForRoleStatements")
    @SuppressWarnings("unused")
    public void statementIsGrantWhitelistedUsingTopLevelGrant(String statement, String operation, String resource)
    {
        ccf.givenRoleIsWhitelistedForOperationOnResource(testUsername, operation, "grants");
        testSession.execute(statement);
        ccf.thenAuditLogContainNothingForUser();
    }

    @Test
    @Parameters(method = "parametersForRoleStatements")
    public void statementIsLoggedWhenGrantWhitelistUnauthorized(String statement, String operation, String resource)
    {
        ccf.givenBasicUser(USER);

        ccf.givenRoleIsWhitelistedForOperationOnResource(basicUsername, operation, "grants/" + resource);
        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> basicSession.execute(statement));
        ccf.thenAuditLogContainsFailedEntriesForUser(statement, basicUsername);
    }

}
