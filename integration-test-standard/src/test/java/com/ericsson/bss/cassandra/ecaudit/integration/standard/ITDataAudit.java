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
public class ITDataAudit
{
    private static CassandraClusterFacade ccf = new CassandraClusterFacade();

    private static final String testUsername = "datasuperuser";
    private static Cluster testCluster;
    private static Session testSession;

    @BeforeClass
    public static void beforeClass()
    {
        ccf.beforeClass();
        ccf.givenSuperuserWithMinimalWhitelist(testUsername);
        testCluster = ccf.createCluster(testUsername, "secret");
        testSession = testCluster.connect();

        ccf.givenUser("data_grantee");
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
    private Object[] parametersForSimpleStatements()
    {
        return new Object[]{
            new Object[]{ "CREATE KEYSPACE dataks WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1} AND DURABLE_WRITES = false", "create", "data/dataks" },
            new Object[]{ "CREATE TABLE dataks.tbl (key int PRIMARY KEY, value text)", "create", "data/dataks" },
            new Object[]{ "CREATE INDEX idx ON dataks.tbl (value)", "alter", "data/dataks/tbl" },
            new Object[]{ "CREATE TYPE dataks.tp (data1 int, data2 int)", "create", "data/dataks" },
            new Object[]{ "SELECT * FROM dataks.tbl WHERE key = 12", "select", "data/dataks/tbl" },
            new Object[]{ "INSERT INTO dataks.tbl (key, value) VALUES (45, 'hepp')", "modify", "data/dataks/tbl" },
            new Object[]{ "UPDATE dataks.tbl SET value = 'hepp' WHERE key = 99", "modify", "data/dataks/tbl" },
            new Object[]{ "GRANT SELECT ON TABLE dataks.tbl TO data_grantee", "authorize", "data/dataks/tbl" },
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
        testSession.execute(statement);
        ccf.thenAuditLogContainEntryForUser(statement, testUsername);
    }

    @Test
    @Parameters(method = "parametersForSimpleStatements")
    public void simpleStatementIsWhitelisted(String statement, String operation, String resource)
    {
        ccf.givenRoleIsWhitelistedForOperationOnResource(testUsername, operation, resource);
        testSession.execute(statement);
        ccf.thenAuditLogContainNothingForUser();
    }
}
