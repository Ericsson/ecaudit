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
public class ITDataAudit
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
    private Object[] parametersForSimpleStatements()
    {
        String grantee = cat.createUniqueUser();

        return new Object[]{
            new Object[]{ "CREATE KEYSPACE IF NOT EXISTS dataks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false", "create", "data/dataks" },
            new Object[]{ "CREATE TABLE IF NOT EXISTS dataks.tbl (key int PRIMARY KEY, value text)", "create", "data/dataks" },
            new Object[]{ "CREATE INDEX IF NOT EXISTS idx ON dataks.tbl (value)", "alter", "data/dataks/tbl" },
            new Object[]{ "CREATE TYPE IF NOT EXISTS dataks.tp (data1 int, data2 int)", "create", "data/dataks" },
            new Object[]{ "SELECT * FROM dataks.tbl WHERE key = 12", "select", "data/dataks/tbl" },
            new Object[]{ "INSERT INTO dataks.tbl (key, value) VALUES (45, 'hepp')", "modify", "data/dataks/tbl" },
            new Object[]{ "UPDATE dataks.tbl SET value = 'hepp' WHERE key = 99", "modify", "data/dataks/tbl" },
            new Object[]{ "GRANT SELECT ON TABLE dataks.tbl TO " + grantee, "authorize", "data/dataks/tbl" },
            new Object[]{ "DELETE value FROM dataks.tbl WHERE key = 5654", "modify", "data/dataks/tbl" },
            new Object[]{ "ALTER TYPE dataks.tp ALTER data1 TYPE int", "alter", "data/dataks" },
            new Object[]{ "ALTER TABLE dataks.tbl WITH gc_grace_seconds = 0", "alter", "data/dataks/tbl" },
            new Object[]{ "ALTER KEYSPACE dataks WITH DURABLE_WRITES = false", "alter", "data/dataks" },
            new Object[]{ "DROP TYPE IF EXISTS dataks.tp", "drop", "data/dataks" },
            new Object[]{ "DROP INDEX IF EXISTS dataks.idx", "alter", "data/dataks/tbl" },
            new Object[]{ "DROP TABLE IF EXISTS dataks.tbl", "drop", "data/dataks/tbl" },
            new Object[]{ "DROP KEYSPACE IF EXISTS dataks", "drop", "data/dataks" },
        };
    }

    @Test
    @Parameters(method = "parametersForSimpleStatements")
    @SuppressWarnings("unused")
    public void simpleStatementIsLogged(String statement, String operation, String resource)
    {
        // When
        testSession.execute(statement);
        // Then
        cat.expectAuditLogContainEntryForUser(statement, testUsername);
    }

    @Test
    @Parameters(method = "parametersForSimpleStatements")
    public void simpleStatementIsWhitelisted(String statement, String operation, String resource)
    {
        // Given
        cat.whitelistRoleForOperationOnResource(testUsername, operation, resource);
        // When
        testSession.execute(statement);
        // Then
        cat.expectNoAuditLog();
    }

    @SuppressWarnings("unused")
    private Object[] parametersForPreparedStatements()
    {
        return new Object[]{
            new Object[]{ "SELECT * FROM dataks.tbl WHERE key = ?", asList(5), "SELECT * FROM dataks.tbl WHERE key = ?[5]", "select", "data/dataks/tbl" },
            new Object[]{ "INSERT INTO dataks.tbl (key, value) VALUES (?, ?)", asList(5, "hepp"), "INSERT INTO dataks.tbl (key, value) VALUES (?, ?)[5, 'hepp']", "modify", "data/dataks/tbl" },
            new Object[]{ "UPDATE dataks.tbl SET value = ? WHERE key = ?", asList("hepp", 34), "UPDATE dataks.tbl SET value = ? WHERE key = ?['hepp', 34]", "modify", "data/dataks/tbl" },
            new Object[]{ "DELETE value FROM dataks.tbl WHERE key = ?", asList(22), "DELETE value FROM dataks.tbl WHERE key = ?[22]", "modify", "data/dataks/tbl" },
        };
    }

    @Test
    @Parameters(method = "parametersForPreparedStatements")
    @SuppressWarnings("unused")
    public void preparedStatementIsLogged(String statement, List<Object> value, String expectedTrace, String operation, String resource)
    {
        // Given
        givenTable("dataks", "tbl");
        // When
        PreparedStatement preparedStatement = testSession.prepare(statement);
        testSession.execute(preparedStatement.bind(value.toArray()));
        // Then
        cat.expectAuditLogContainEntryForUser(expectedTrace, testUsername);
    }

    @Test
    @Parameters(method = "parametersForPreparedStatements")
    @SuppressWarnings("unused")
    public void preparedStatementIsWhitelisted(String statement, List<Object> value, String expectedTrace, String operation, String resource)
    {
        // Given
        givenTable("dataks", "tbl");
        cat.whitelistRoleForOperationOnResource(testUsername, operation, resource);
        // When
        PreparedStatement preparedStatement = testSession.prepare(statement);
        testSession.execute(preparedStatement.bind(value.toArray()));
        // Then
        cat.expectNoAuditLog();
    }

    private void givenTable(String keyspace, String table)
    {
        cat.createKeyspace(keyspace);
        cat.executeStatementAsSuperuserWithoutAudit("CREATE TABLE IF NOT EXISTS " + keyspace + "." + table + " (key int PRIMARY KEY, value text)");
    }
}
