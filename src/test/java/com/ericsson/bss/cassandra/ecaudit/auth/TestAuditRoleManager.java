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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.Config;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditRoleManager
{
    private AuditRoleManager auditRoleManager;

    @Mock
    private IRoleManager mockWrappedRoleManager;

    @Mock
    private AuditWhitelistManager mockAuditWhitelistManager;

    @BeforeClass
    public static void beforeClass()
    {
        Config.setClientMode(true);
    }

    @Before
    public void before()
    {
        auditRoleManager = new AuditRoleManager(mockWrappedRoleManager, mockAuditWhitelistManager, true);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockWrappedRoleManager);
        verifyNoMoreInteractions(mockAuditWhitelistManager);
    }

    @AfterClass
    public static void afterClass()
    {
        Config.setClientMode(false);
    }

    @Test
    public void testSetupDelegation()
    {
        auditRoleManager.setup();

        verify(mockWrappedRoleManager).setup();
        verify(mockAuditWhitelistManager).setup();
    }

    @Test
    public void testSupportedOptions()
    {
        Set<IRoleManager.Option> options = auditRoleManager.supportedOptions();

        assertThat(options).containsExactlyElementsOf(ImmutableSet.of(IRoleManager.Option.LOGIN, IRoleManager.Option.SUPERUSER, IRoleManager.Option.PASSWORD, IRoleManager.Option.OPTIONS));
    }

    @Test
    public void testStandAloneSupportedOptions()
    {
        AuditRoleManager standAloneAuditRoleManager = new AuditRoleManager(mockWrappedRoleManager, mockAuditWhitelistManager, false);

        Set<IRoleManager.Option> options = standAloneAuditRoleManager.supportedOptions();

        assertThat(options).containsExactlyElementsOf(ImmutableSet.of(IRoleManager.Option.LOGIN, IRoleManager.Option.SUPERUSER));
    }

    @Test
    public void testAlterableOptions()
    {
        Set<IRoleManager.Option> options = auditRoleManager.alterableOptions();

        assertThat(options).containsExactlyElementsOf(ImmutableSet.of(IRoleManager.Option.PASSWORD));
    }

    @Test
    public void testStandAloneAlterableOptions()
    {
        AuditRoleManager standAloneAuditRoleManager = new AuditRoleManager(mockWrappedRoleManager, mockAuditWhitelistManager, false);

        Set<IRoleManager.Option> options = standAloneAuditRoleManager.alterableOptions();

        assertThat(options).isEmpty();
    }

    @Test
    public void testCreateRole()
    {
        AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
        RoleResource role = mock(RoleResource.class);
        RoleOptions roleOptions = mock(RoleOptions.class);

        auditRoleManager.createRole(authenticatedUser, role, roleOptions);

        verify(mockAuditWhitelistManager).createRoleOption(eq(roleOptions));
        verify(mockWrappedRoleManager).createRole(eq(authenticatedUser), eq(role), eq(roleOptions));
    }

    @Test
    public void testAlterRole()
    {
        AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
        RoleResource role = mock(RoleResource.class);
        RoleOptions roleOptions = mock(RoleOptions.class);
        when(roleOptions.getPassword()).thenReturn(Optional.absent());
        when(roleOptions.getLogin()).thenReturn(Optional.absent());
        when(roleOptions.getSuperuser()).thenReturn(Optional.absent());

        auditRoleManager.alterRole(authenticatedUser, role, roleOptions);

        verify(mockAuditWhitelistManager).alterRoleOption(eq(authenticatedUser), eq(role), eq(roleOptions));
        verify(mockWrappedRoleManager).alterRole(eq(authenticatedUser), eq(role), eq(roleOptions));
    }

    @Test
    public void testGrantRole()
    {
        AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
        RoleResource role = mock(RoleResource.class);
        RoleResource grantee = mock(RoleResource.class);

        auditRoleManager.grantRole(authenticatedUser, role, grantee);

        verify(mockWrappedRoleManager).grantRole(eq(authenticatedUser), eq(role), eq(grantee));
    }

    @Test
    public void testRevokeRole()
    {
        AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
        RoleResource role = mock(RoleResource.class);
        RoleResource grantee = mock(RoleResource.class);

        auditRoleManager.revokeRole(authenticatedUser, role, grantee);

        verify(mockWrappedRoleManager).revokeRole(eq(authenticatedUser), eq(role), eq(grantee));
    }

    @Test
    public void testDropRole()
    {
        AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
        RoleResource role = mock(RoleResource.class);

        auditRoleManager.dropRole(authenticatedUser, role);

        verify(mockWrappedRoleManager).dropRole(eq(authenticatedUser), eq(role));
        verify(mockAuditWhitelistManager).dropRoleWhitelist(eq(role));
    }

    @Test
    public void testGetRoles()
    {
        RoleResource role = mock(RoleResource.class);
        Set<RoleResource> expectedRoles = givenRoleManagerHasRoleWithRoles(mockWrappedRoleManager, role);

        Set<RoleResource> actualRoles = auditRoleManager.getRoles(role, true);

        verify(mockWrappedRoleManager).getRoles(eq(role), eq(true));
        assertThat(actualRoles).containsExactlyInAnyOrderElementsOf(expectedRoles);
    }

    @Test
    public void testGetAllRoles()
    {
        RoleResource role = mock(RoleResource.class);
        Set<RoleResource> expectedRoles = givenRoleManagerHasRoleWithRoles(mockWrappedRoleManager, role);

        Set<RoleResource> actualRoles = auditRoleManager.getAllRoles();

        verify(mockWrappedRoleManager).getAllRoles();
        assertThat(actualRoles).containsExactlyInAnyOrderElementsOf(expectedRoles);
    }

    @Test
    public void testIsSuper()
    {
        RoleResource role = mock(RoleResource.class);
        when(mockWrappedRoleManager.isSuper(eq(role))).thenReturn(true);

        boolean isSuper = auditRoleManager.isSuper(role);

        verify(mockWrappedRoleManager).isSuper(eq(role));
        assertThat(isSuper).isTrue();
    }

    @Test
    public void testCanLogin()
    {
        RoleResource role = mock(RoleResource.class);
        when(mockWrappedRoleManager.canLogin(eq(role))).thenReturn(true);

        boolean canLogin = auditRoleManager.canLogin(role);

        verify(mockWrappedRoleManager).canLogin(eq(role));
        assertThat(canLogin).isTrue();
    }

    @Test
    public void testGetCustomOptions()
    {
        RoleResource role = mock(RoleResource.class);
        Map<String, String> expectedOptions = givenWhitelistManagerHasRoleWithOptions(mockAuditWhitelistManager, role);

        Map<String, String> actualOptions = auditRoleManager.getCustomOptions(role);

        verify(mockAuditWhitelistManager).getRoleWhitelist(eq(role));
        assertThat(actualOptions.entrySet()).containsExactlyInAnyOrderElementsOf(expectedOptions.entrySet());
    }

    @Test
    public void testIsExistingRole()
    {
        RoleResource role = mock(RoleResource.class);
        when(mockWrappedRoleManager.isExistingRole(eq(role))).thenReturn(true);

        boolean isExitingRole = auditRoleManager.isExistingRole(role);

        verify(mockWrappedRoleManager).isExistingRole(eq(role));
        assertThat(isExitingRole).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProtectedResources()
    {
        Set<IResource> cassandraResources = givenProtectedCassandraResources(mockWrappedRoleManager);

        Set<IResource> actualResources = (Set<IResource>) auditRoleManager.protectedResources();

        assertThat(actualResources).containsAll(cassandraResources);
        assertThat(actualResources).containsAll(ImmutableSet.of(DataResource.table("system_auth", "role_audit_whitelists"),
                                                                DataResource.table("system_auth", "role_audit_whitelists_v2")));
        assertThat(actualResources).hasSize(cassandraResources.size() + 2);
    }

    @Test
    public void testValidateConfiguration()
    {
        auditRoleManager.validateConfiguration();

        verify(mockWrappedRoleManager).validateConfiguration();
    }

    private Set<RoleResource> givenRoleManagerHasRoleWithRoles(IRoleManager roleManager, RoleResource role)
    {
        RoleResource otherRole = mock(RoleResource.class);

        Set<RoleResource> expectedRoles = ImmutableSet.of(role, otherRole);
        when(roleManager.getRoles(eq(role), anyBoolean())).thenReturn(expectedRoles);
        when(roleManager.getAllRoles()).thenReturn(expectedRoles);

        return expectedRoles;
    }

    private Map<String, String> givenWhitelistManagerHasRoleWithOptions(AuditWhitelistManager auditWhitelistManager, RoleResource role)
    {
        Map<String, String> expectedOptions = ImmutableMap.of("optionKey", "optionValue");
        when(auditWhitelistManager.getRoleWhitelist(eq(role))).thenReturn(expectedOptions);

        return expectedOptions;
    }

    private Set<IResource> givenProtectedCassandraResources(IRoleManager roleManager)
    {
        Set<IResource> cassandraResources = ImmutableSet.of(DataResource.table("ks", "tbl"));
        doReturn(cassandraResources).when(roleManager).protectedResources();
        return cassandraResources;
    }
}
