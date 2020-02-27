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
    private static CassandraClusterFacade ccf = new CassandraClusterFacade();

    private static String testUsername;;
    private static Cluster testCluster;
    private static Session testSession;

    @BeforeClass
    public static void beforeClass()
    {
        ccf.setup();
        testUsername = ccf.givenUniqueSuperuserWithMinimalWhitelist();
        testCluster = ccf.createCluster(testUsername, "secret");
        testSession = testCluster.connect();

        ccf.givenKeyspace("funcks");
        ccf.givenKeyspace("aggks");
        givenStateFunction("aggks.avgState1");
        givenFinalStateFunction("aggks.avgFinal1");
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
    }

    @AfterClass
    public static void afterClass()
    {
        ccf.givenStatementExecutedAsSuperuserWithoutAudit("DROP FUNCTION aggks.avgState1");
        ccf.givenStatementExecutedAsSuperuserWithoutAudit("DROP FUNCTION aggks.avgFinal1");
        testSession.close();
        testCluster.close();
        ccf.tearDown();
    }

    @SuppressWarnings("unused")
    private Object[] parametersForFunctionStatements()
    {
        return new Object[]{
            new Object[]{ "CREATE FUNCTION funcks.flog1 (input double) CALLED ON NULL INPUT RETURNS double LANGUAGE java AS 'return Double.valueOf(Math.log(input.doubleValue()));'", "create", "functions/funcks" },
            new Object[]{ "DROP FUNCTION IF EXISTS funcks.flog1(double)", "drop", "functions/funcks/flog1|DoubleType" },
            new Object[]{ "CREATE AGGREGATE aggks.aaverage1 (int) SFUNC avgState1 STYPE tuple<int,bigint> FINALFUNC avgFinal1 INITCOND (0,0)", "create", "functions/aggks" },
            new Object[]{ "DROP AGGREGATE IF EXISTS aggks.aaverage1(int)", "drop", "functions/aggks/aaverage1|Int32Type" },
        };
    }

    @Test
    @Parameters(method = "parametersForFunctionStatements")
    @SuppressWarnings("unused")
    public void statementIsLogged(String statement, String operation, String resource)
    {
        testSession.execute(statement);
        ccf.thenAuditLogContainEntryForUser(statement, testUsername);
    }

    @Test
    @Parameters(method = "parametersForFunctionStatements")
    public void statementIsWhitelisted(String statement, String operation, String resource)
    {
        ccf.givenRoleIsWhitelistedForOperationOnResource(testUsername, operation, resource);
        testSession.execute(statement);
        ccf.thenAuditLogContainNothingForUser();
    }

    private static void givenStateFunction(String func)
    {
        ccf.givenStatementExecutedAsSuperuserWithoutAudit(
        "CREATE FUNCTION " + func + " (state tuple<int, bigint>, val int) " +
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

    private static void givenFinalStateFunction(String func)
    {
        ccf.givenStatementExecutedAsSuperuserWithoutAudit(
        "CREATE FUNCTION " + func + " (state tuple<int, bigint>) " +
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
