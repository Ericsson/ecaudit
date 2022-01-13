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
package com.ericsson.bss.cassandra.ecaudit.filter.role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.auth.AuditAuthorizer;
import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.db.SystemKeyspace;
import org.apache.cassandra.schema.SchemaConstants;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnitParamsRunner.class)
public class TestAuditFilterAuthorizer
{
    private static final String AUTH_USER = "auth_user";
    private static final String UNAUTH_USER = "unauth_user";
    private static final IResource RESOURCE_DATA = DataResource.fromName("data");
    private static final IResource RESOURCE_KEYSPACE = DataResource.fromName("data/ks");
    private static final IResource RESOURCE_TABLE = DataResource.fromName("data/ks/tbl");
    private static final AuditFilterAuthorizer AUTHORIZER = new AuditFilterAuthorizer();

    // From SchemaKeyspace
    private static final ImmutableList<String> ALL_SCHEMA_TABLES = ImmutableList.of("columns", "dropped_columns", "triggers", "types", "functions", "aggregates", "indexes", "tables", "views", "keyspaces");

    @BeforeClass
    public static void beforeAll()
    {
        ClientInitializer.beforeClass();

        AuthenticatedUser user = new AuthenticatedUser(AUTH_USER);
        IAuthorizer authorizerMock = mock(IAuthorizer.class);
        when(authorizerMock.authorize(user, RESOURCE_TABLE)).thenReturn(Sets.newHashSet(Permission.SELECT, Permission.MODIFY));
        when(authorizerMock.authorize(user, RESOURCE_KEYSPACE)).thenReturn(Sets.newHashSet(Permission.CREATE));
        AUTHORIZER.setAuthorizer(authorizerMock);
    }

    @AfterClass
    public static void afterAll()
    {
        ClientInitializer.afterClass();
    }

    @Test
    public void testGetAuthorizer()
    {
        assertThat(AUTHORIZER.getAuthorizer())
            .isInstanceOf(IAuthorizer.class)
            .isNotInstanceOf(AuditAuthorizer.class);
    }

    @SuppressWarnings("unused")
    private Object[] parametersForTestOperationAuthorization()
    {
        return new Object[]{
            new Object[]{ Permission.SELECT, AUTH_USER, asList(RESOURCE_TABLE), true },
            new Object[]{ Permission.MODIFY, AUTH_USER, asList(RESOURCE_TABLE), true },
            new Object[]{ Permission.ALTER, AUTH_USER, asList(RESOURCE_TABLE), false },
            new Object[]{ Permission.SELECT, AUTH_USER, asList(RESOURCE_KEYSPACE), false },
            new Object[]{ Permission.CREATE, AUTH_USER, asList(RESOURCE_KEYSPACE), true },
            new Object[]{ Permission.MODIFY, AUTH_USER, asList(RESOURCE_DATA), false },
            new Object[]{ Permission.SELECT, UNAUTH_USER, asList(RESOURCE_TABLE), false },
            new Object[]{ Permission.CREATE, AUTH_USER, asList(RESOURCE_DATA, RESOURCE_KEYSPACE, RESOURCE_TABLE), true },
            new Object[]{ Permission.DESCRIBE, AUTH_USER, asList(RESOURCE_DATA, RESOURCE_KEYSPACE, RESOURCE_TABLE), false },
        };
    }

    @Test
    @Parameters
    public void testOperationAuthorization(Permission operation, String user, List<IResource> resources, boolean expectedAuthorized)
    {
        assertThat(AUTHORIZER.isOperationAuthorizedForUser(operation, user, resources)).isEqualTo(expectedAuthorized);
    }

    @SuppressWarnings("unused")
    private Object[] parametersForTestSystemOperationAuthorization()
    {
        List<Object[]> objects = new ArrayList<>();
        objects.add(new Object[] {toDataResources(SchemaConstants.SYSTEM_KEYSPACE_NAME, SystemKeyspace.LOCAL)});
        objects.add(new Object[] {toDataResources(SchemaConstants.SYSTEM_KEYSPACE_NAME, SystemKeyspace.PEERS_V2)});
        objects.add(new Object[] {toDataResources(SchemaConstants.SYSTEM_KEYSPACE_NAME, "peers")});

        for (String table : ALL_SCHEMA_TABLES)
        {
            objects.add(new Object[] {toDataResources(SchemaConstants.SCHEMA_KEYSPACE_NAME, table)});
        }

        return objects.toArray();
    }

    @Test
    @Parameters
    public void testSystemOperationAuthorization(List<IResource> resources)
    {
        // Select on system keyspaces are ok
        assertThat(AUTHORIZER.isOperationAuthorizedForUser(Permission.SELECT, AUTH_USER, resources)).isEqualTo(true);

        List<Permission> NOT_OK = new ArrayList<>(Permission.ALL);
        NOT_OK.remove(Permission.SELECT);
        for (Permission permission : NOT_OK)
        {
            assertThat(AUTHORIZER.isOperationAuthorizedForUser(permission, AUTH_USER, resources)).isEqualTo(false);
        }
    }

    private static List<DataResource> toDataResources(String keyspace, String table)
    {
        return Arrays.asList(
            DataResource.fromName(String.format("data/%s/%s", keyspace, table)),
            DataResource.fromName(String.format("data/%s", keyspace)),
            DataResource.fromName("data")
        );
    }
}
