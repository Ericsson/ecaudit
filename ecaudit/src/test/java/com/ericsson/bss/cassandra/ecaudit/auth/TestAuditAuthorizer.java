/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.PermissionDetails;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditAuthorizer
{
    @Mock
    private IAuthorizer mockAuthorizer;

    @Mock
    private AuthenticatedUser mockPerformer;

    private Set<Permission> permissions;

    @Mock
    private IResource mockResource;

    @Mock
    private RoleResource mockRole;

    private AuditAuthorizer authorizer;

    @BeforeClass
    public static void beforeClass()
    {
        DatabaseDescriptor.clientInitialization(true);

        IAuthorizer authorizer = mock(IAuthorizer.class);
        DatabaseDescriptor.setAuthorizer(authorizer);
    }

    @Before
    public void before()
    {
        permissions = new HashSet<>();
        authorizer = new AuditAuthorizer(mockAuthorizer);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockAuthorizer);
    }

    @AfterClass
    public static void afterClass()
    {
        DatabaseDescriptor.setAuthenticator(null);
        DatabaseDescriptor.clientInitialization(false);
    }

    @Test
    public void testNormalAuthorize()
    {
        Set<Permission> expectedPermissions = givenAuthorizedPermission(mockAuthorizer, mockResource);

        Set<Permission> actualPermissions = authorizer.authorize(mockPerformer, mockResource);

        assertThat(actualPermissions).isEqualTo(expectedPermissions);
        verify(mockAuthorizer).authorize(eq(mockPerformer), eq(mockResource));
    }

    @Test
    public void testDataInstanceAuthorize()
    {
        DataResource dataResource = DataResource.fromName("data/ks");
        Set<Permission> expectedPermissions = givenAuthorizedPermission(mockAuthorizer, dataResource);

        Set<Permission> actualPermissions = authorizer.authorize(mockPerformer, dataResource);

        assertThat(actualPermissions).isEqualTo(expectedPermissions);
        verify(mockAuthorizer).authorize(eq(mockPerformer), eq(dataResource));
    }

    @Test
    public void testTopRoleAuthorize()
    {
        RoleResource roleResource = RoleResource.fromName("roles");
        Set<Permission> expectedPermissions = givenAuthorizedPermission(mockAuthorizer, roleResource);

        Set<Permission> actualPermissions = authorizer.authorize(mockPerformer, roleResource);

        assertThat(actualPermissions).isEqualTo(expectedPermissions);
        verify(mockAuthorizer).authorize(eq(mockPerformer), eq(roleResource));
    }

    @Test
    public void testRoleInstanceAuthorize()
    {
        RoleResource roleResource = RoleResource.role("bob");
        Set<Permission> expectedPermissions = givenAuthorizedPermission(mockAuthorizer, roleResource);

        Set<Permission> actualPermissions = authorizer.authorize(mockPerformer, roleResource);

        assertThat(actualPermissions).containsAll(expectedPermissions);
        assertThat(actualPermissions).contains(Permission.ALTER);
        verify(mockAuthorizer).authorize(eq(mockPerformer), eq(roleResource));
    }

    @Test
    public void testAuthorizePassThrough()
    {
        Set<Permission> expectedPermissions = givenAuthorizedPermission(mockAuthorizer, mockResource);

        Set<Permission> actualPermissions = authorizer.realAuthorize(mockPerformer, mockResource);

        assertThat(actualPermissions).isEqualTo(expectedPermissions);
        verify(mockAuthorizer).authorize(eq(mockPerformer), eq(mockResource));
    }

    @Test
    public void testGrantDelegation()
    {
        authorizer.grant(mockPerformer, permissions, mockResource, mockRole);
        verify(mockAuthorizer).grant(eq(mockPerformer), eq(permissions), eq(mockResource), eq(mockRole));
    }

    @Test
    public void testRevokeDelegation()
    {
        authorizer.revoke(mockPerformer, permissions, mockResource, mockRole);
        verify(mockAuthorizer).revoke(eq(mockPerformer), eq(permissions), eq(mockResource), eq(mockRole));
    }

    @Test
    public void testListDelegation()
    {
        Set<PermissionDetails> expectedDetails = givenPermissionDetails(mockAuthorizer);

        Set<PermissionDetails> actualDetails = authorizer.list(mockPerformer, permissions, mockResource, mockRole);

        assertThat(actualDetails).isEqualTo(expectedDetails);
        verify(mockAuthorizer).list(eq(mockPerformer), eq(permissions), eq(mockResource), eq(mockRole));
    }

    @Test
    public void testRevokeAllFromDelegation()
    {
        authorizer.revokeAllFrom(mockRole);
        verify(mockAuthorizer).revokeAllFrom(eq(mockRole));
    }

    @Test
    public void testRevokeAllOnDelegation()
    {
        authorizer.revokeAllOn(mockRole);
        verify(mockAuthorizer).revokeAllOn(eq(mockRole));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProtectedResourcesDelegation()
    {
        Set<IResource> expectedResources = givenProtectedCassandraResources(mockAuthorizer);

        Set<IResource> actualResources = (Set<IResource>) authorizer.protectedResources();

        assertThat(actualResources).isEqualTo(expectedResources);
        verify(mockAuthorizer).protectedResources();
    }

    @Test
    public void testValidateConfigurationDelegation()
    {
        authorizer.validateConfiguration();
        verify(mockAuthorizer).validateConfiguration();
    }

    @Test
    public void testSetupDelegation()
    {
        authorizer.setup();
        verify(mockAuthorizer).setup();
    }

    private Set<Permission> givenAuthorizedPermission(IAuthorizer authorizer, IResource resource)
    {
        Set<Permission> permissions = ImmutableSet.of(Permission.SELECT, Permission.MODIFY);
        when(authorizer.authorize(eq(mockPerformer), eq(resource))).thenReturn(permissions);
        return permissions;
    }

    private Set<PermissionDetails> givenPermissionDetails(IAuthorizer authorizer)
    {
        Set<PermissionDetails> details = ImmutableSet.of(new PermissionDetails("pelle", mockResource, Permission.SELECT));
        when(authorizer.list(eq(mockPerformer), eq(permissions), eq(mockResource), eq(mockRole))).thenReturn(details);
        return details;
    }

    private Set<IResource> givenProtectedCassandraResources(IAuthorizer authorizer)
    {
        Set<IResource> cassandraResources = ImmutableSet.of(DataResource.table("ks", "tbl"));
        doReturn(cassandraResources).when(authorizer).protectedResources();
        return cassandraResources;
    }
}
