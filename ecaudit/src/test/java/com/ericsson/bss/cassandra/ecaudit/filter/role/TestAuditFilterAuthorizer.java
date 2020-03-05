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

import java.util.List;

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
}
