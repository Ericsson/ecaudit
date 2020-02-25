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
public class ITFunctionsAudit
{
    private static CassandraAuditTester cat = new CassandraAuditTester();

    private static String testUsername;
    private static Cluster testCluster;
    private static Session testSession;

    @BeforeClass
    public static void beforeClass()
    {
        testUsername = cat.createUniqueSuperUser();
        testCluster = cat.createCluster(testUsername, "secret");
        testSession = testCluster.connect();

        cat.createKeyspace("funcks");
        givenStateFunction("aggks", "avgState1");
        givenFinalStateFunction("aggks", "avgFinal1");
    }

    @Before
    public void before()
    {
        cat.before();
        cat.resetTestUserWithMinimalWhitelist(testUsername);
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
    private Object[] parametersForFunctionStatements()
    {
        return new Object[]{
            new Object[]{ "CREATE FUNCTION IF NOT EXISTS funcks.flog1 (input double) CALLED ON NULL INPUT RETURNS double LANGUAGE java AS 'return Double.valueOf(Math.log(input.doubleValue()));'", "create", "functions/funcks" },
            new Object[]{ "DROP FUNCTION IF EXISTS funcks.flog1(double)", "drop", "functions/funcks/flog1|DoubleType" },
            new Object[]{ "CREATE AGGREGATE IF NOT EXISTS aggks.aaverage1 (int) SFUNC avgState1 STYPE tuple<int,bigint> FINALFUNC avgFinal1 INITCOND (0,0)", "create", "functions/aggks" },
            new Object[]{ "DROP AGGREGATE IF EXISTS aggks.aaverage1(int)", "drop", "functions/aggks/aaverage1|Int32Type" },
        };
    }

    @Test
    @Parameters(method = "parametersForFunctionStatements")
    @SuppressWarnings("unused")
    public void statementIsLogged(String statement, String operation, String resource)
    {
        // When
        testSession.execute(statement);
        // Then
        cat.expectAuditLogContainEntryForUser(statement, testUsername);
    }

    @Test
    @Parameters(method = "parametersForFunctionStatements")
    public void statementIsWhitelisted(String statement, String operation, String resource)
    {
        // Given
        cat.whitelistRoleForOperationOnResource(testUsername, operation, resource);
        // When
        testSession.execute(statement);
        // Then
        cat.expectNoAuditLog();
    }

    private static void givenStateFunction(String keyspace, String func)
    {
        cat.createKeyspace(keyspace);
        cat.executeStatementAsSuperuserWithoutAudit(
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
        "'");
    }

    private static void givenFinalStateFunction(String keyspace, String func)
    {
        cat.createKeyspace(keyspace);
        cat.executeStatementAsSuperuserWithoutAudit(
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
        "'");
    }
}
