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
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class ITRolesAudit
{
    private static CassandraAuditTester cat = new CassandraAuditTester("superrole");

    private static String testUser;
    private static Cluster testCluster;
    private static Session testSession;

    @BeforeClass
    public static void beforeClass()
    {
        testUser = cat.createUniqueSuperUser();
        testCluster = cat.createCluster(testUser, "secret");
        testSession = testCluster.connect();
    }

    @Before
    public void before()
    {
        cat.before();
        cat.resetTestUserWithMinimalWhitelist(testUser);
    }

    @After
    public void after()
    {
        cat.after();
    }

    @AfterClass
    public static void afterClass()
    {
        testSession.close();
        testCluster.close();
        cat.afterClass();
    }

    @SuppressWarnings("unused")
    private Object[] parametersForRoleStatements()
    {
        String alterUser = cat.createUniqueUser();
        String dropUser = cat.createUniqueUser();
        String grantRole = cat.createUniqueUser();
        String grantee = cat.createUniqueUser();
        String revokeRole = cat.createUniqueUser();
        String revokeUser = cat.createUniqueUser();
        givenRoleGrantedTo(revokeRole, revokeUser);
        String describeUser = cat.createUniqueUser();

        return new Object[]{
            new Object[]{ "CREATE ROLE " + cat.getUniqueUsername() + " WITH PASSWORD = 'secret' AND LOGIN = true AND SUPERUSER = true", "create", "roles" },
            new Object[]{ "ALTER ROLE " + alterUser + " WITH LOGIN = false", "alter", "roles/" + alterUser },
            new Object[]{ "DROP ROLE " + dropUser, "drop", "roles/" + dropUser },
            new Object[]{ "GRANT " + grantRole + " TO " + grantee, "authorize", "roles/" + grantRole },
            new Object[]{ "REVOKE " + revokeRole + " FROM " + revokeUser, "authorize", "roles/" + revokeRole },
            new Object[]{ "LIST ROLES OF " + describeUser, "describe", "roles" },
        };
    }

    @Test
    @Parameters(method = "parametersForRoleStatements")
    @SuppressWarnings("unused")
    public void statementIsLogged(String statement, String operation, String resource)
    {
        // When
        testSession.execute(statement);
        // Then
        cat.expectAuditLogContainEntryForUser(statement, testUser);
    }

    @Test
    @Parameters(method = "parametersForRoleStatements")
    public void statementIsWhitelisted(String statement, String operation, String resource)
    {
        // Given
        cat.whitelistRoleForOperationOnResource(testUser, operation, resource);
        // When
        testSession.execute(statement);
        // Then
        cat.expectNoAuditLog();
    }

    void givenRoleGrantedTo(String role, String grantee)
    {
        cat.executeStatementAsSuperuserWithoutAudit("GRANT " + role + " TO " + grantee);
    }
}
