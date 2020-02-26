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

import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import static java.util.Arrays.asList;

@RunWith(JUnitParamsRunner.class)
public class ITDataPreparedAudit
{
    private static CassandraClusterFacade ccf = new CassandraClusterFacade();

    private static final String testUsername = "prepsuperuser";
    private static Cluster testCluster;
    private static Session testSession;

    @BeforeClass
    public static void beforeClass()
    {
        ccf.beforeClass();
        ccf.givenSuperuserWithMinimalWhitelist(testUsername);
        testCluster = ccf.createCluster(testUsername, "secret");
        testSession = testCluster.connect();

        ccf.givenKeyspace("prepks");
        ccf.givenTable("prepks.tbl");
    }

    @Before
    public void before()
    {
        ccf.before();
        ccf.resetTestUserWithMinimalWhitelist(testUsername);
    }

    @After
    public void after()
    {
        ccf.after();
    }

    @AfterClass
    public static void afterClass()
    {
        testSession.close();
        testCluster.close();
        ccf.afterClass();
    }

    @SuppressWarnings("unused")
    private Object[] parametersForPreparedStatements()
    {
        return new Object[]{
            new Object[]{ "SELECT * FROM prepks.tbl WHERE key = ?", asList(5), "SELECT * FROM prepks.tbl WHERE key = ?[5]", "select", "data/prepks/tbl" },
            new Object[]{ "INSERT INTO prepks.tbl (key, value) VALUES (?, ?)", asList(5, "hepp"), "INSERT INTO prepks.tbl (key, value) VALUES (?, ?)[5, 'hepp']", "modify", "data/prepks/tbl" },
            new Object[]{ "UPDATE prepks.tbl SET value = ? WHERE key = ?", asList("hepp", 34), "UPDATE prepks.tbl SET value = ? WHERE key = ?['hepp', 34]", "modify", "data/prepks/tbl" },
            new Object[]{ "DELETE value FROM prepks.tbl WHERE key = ?", asList(22), "DELETE value FROM prepks.tbl WHERE key = ?[22]", "modify", "data/prepks/tbl" },
        };
    }

    @Test
    @Parameters(method = "parametersForPreparedStatements")
    @SuppressWarnings("unused")
    public void preparedStatementIsLogged(String statement, List<Object> value, String expectedTrace, String operation, String resource)
    {
        PreparedStatement preparedStatement = testSession.prepare(statement);
        testSession.execute(preparedStatement.bind(value.toArray()));
        ccf.thenAuditLogContainEntryForUser(expectedTrace, testUsername);
    }

    @Test
    @Parameters(method = "parametersForPreparedStatements")
    @SuppressWarnings("unused")
    public void preparedStatementIsWhitelisted(String statement, List<Object> value, String expectedTrace, String operation, String resource)
    {
        ccf.givenRoleIsWhitelistedForOperationOnResource(testUsername, operation, resource);
        PreparedStatement preparedStatement = testSession.prepare(statement);
        testSession.execute(preparedStatement.bind(value.toArray()));
        ccf.thenAuditLogContainNothingForUser();
    }
}
