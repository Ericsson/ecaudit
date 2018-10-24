//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.UnauthorizedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditWhitelistManager
{
    @Mock
    private AuthenticatedUser performer;

    @Mock
    private RoleResource role;

    @Mock
    private WhitelistDataAccess mockWhitelistDataAccess;

    private AuditWhitelistManager whitelistManager;

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
        when(performer.getName()).thenReturn("bob");
        when(role.getRoleName()).thenReturn("hans");
        whitelistManager = new AuditWhitelistManager(mockWhitelistDataAccess);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockWhitelistDataAccess);
    }

    @AfterClass
    public static void afterClass()
    {
        DatabaseDescriptor.setAuthenticator(null);
        DatabaseDescriptor.clientInitialization(false);
    }

    @Test
    public void testGrantAtCreate()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        when(role.getRoleName()).thenReturn("hans");
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data, connections"));

        whitelistManager.createRoleWhitelist(performer, role, options);

        verify(mockWhitelistDataAccess, times(1)).addToWhitelist(eq("hans"), eq("ALL"),
                eq(ImmutableSet.of("data", "connections")));
    }

    @Test
    public void testGrantLevelTwoResourceAtCreate()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        when(role.getRoleName()).thenReturn("hans");
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data/myks"));

        whitelistManager.createRoleWhitelist(performer, role, options);

        verify(mockWhitelistDataAccess, times(1)).addToWhitelist(eq("hans"), eq("ALL"),
                eq(ImmutableSet.of("data/myks")));
    }

    @Test(expected = UnauthorizedException.class)
    public void testGrantAtCreateIsDenied()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.MODIFY, Permission.SELECT));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data, connections"));

        whitelistManager.createRoleWhitelist(performer, role, options);
    }

    @Test(expected = InvalidRequestException.class)
    public void testUnknownOptionAtCreate()
    {
        RoleOptions options = createRoleOptions(Collections.singletonMap("unknown_option", "guck"));

        whitelistManager.createRoleWhitelist(performer, role, options);
    }

    @Test(expected = InvalidRequestException.class)
    public void testUnsupportedResourceAtCreate()
    {
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "mbean"));

        whitelistManager.createRoleWhitelist(performer, role, options);
    }

    @Test
    public void testNoOptionAtCreate()
    {
        RoleOptions options = new RoleOptions();

        whitelistManager.createRoleWhitelist(performer, role, options);
    }

    @Test
    public void testGrantAtAlter()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data"));

        whitelistManager.alterRoleWhitelist(performer, role, options);

        verify(mockWhitelistDataAccess, times(1)).addToWhitelist(eq("hans"), eq("ALL"),
                eq(ImmutableSet.of("data")));
    }

    @Test
    public void testGrantLevelTwoResourceAtAlter()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data/myks"));

        whitelistManager.alterRoleWhitelist(performer, role, options);

        verify(mockWhitelistDataAccess, times(1)).addToWhitelist(eq("hans"), eq("ALL"),
                eq(ImmutableSet.of("data/myks")));
    }

    @Test(expected = UnauthorizedException.class)
    public void testGrantAtAlterIsDenied()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.MODIFY, Permission.SELECT));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data, connections"));

        whitelistManager.alterRoleWhitelist(performer, role, options);
    }

    @Test
    public void testRevokeAtAlter()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("revoke_audit_whitelist_for_all", "connections"));

        whitelistManager.alterRoleWhitelist(performer, role, options);

        verify(mockWhitelistDataAccess, times(1)).removeFromWhitelist(eq("hans"), eq("ALL"),
                eq(ImmutableSet.of("connections")));
    }

    @Test(expected = UnauthorizedException.class)
    public void testRevokeAtAlterIsDenied()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.MODIFY, Permission.SELECT));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("revoke_audit_whitelist_for_all", "data"));

        whitelistManager.alterRoleWhitelist(performer, role, options);
    }

    @Test(expected = InvalidRequestException.class)
    public void testUnknownOptionAtAlter()
    {
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("unknown_option", "guck"));

        whitelistManager.alterRoleWhitelist(performer, role, options);
    }

    @Test(expected = InvalidRequestException.class)
    public void testUnsupportedResourceAtAlter()
    {
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "mbean"));

        whitelistManager.alterRoleWhitelist(performer, role, options);
    }

    @Test
    public void testNoOptionAtAlter()
    {
        RoleOptions options = new RoleOptions();

        whitelistManager.alterRoleWhitelist(performer, role, options);
    }

    @Test
    public void testGetWhitelist()
    {
        String expectedRoleName = "hans";
        Set<String> expectedResources = ImmutableSet.of("data/someks/sometable", "connections");

        when(mockWhitelistDataAccess.getWhitelist(eq(expectedRoleName), any(String.class)))
                .thenReturn(expectedResources);

        Map<String, String> whitelistOptions = whitelistManager.getRoleWhitelist(expectedRoleName);

        verify(mockWhitelistDataAccess, times(1)).getWhitelist(eq(expectedRoleName), eq("ALL"));
        assertThat(whitelistOptions.keySet()).containsExactly("audit_whitelist_for_all");
        assertThat(toStringSet(whitelistOptions.get("audit_whitelist_for_all"))).isEqualTo(expectedResources);
    }

    @Test
    public void testDropWhitelist()
    {
        String expectedRoleName = "hans";
        whitelistManager.dropRoleWhitelist(expectedRoleName);
        verify(mockWhitelistDataAccess, times(1)).deleteWhitelist(eq(expectedRoleName));
    }

    @Test
    public void testSetup()
    {
        whitelistManager.setup();
        verify(mockWhitelistDataAccess, times(1)).setup();
    }

    private RoleOptions createRoleOptions(Map<String, String> whitelistOptions)
    {
        RoleOptions options = new RoleOptions();
        options.setOption(IRoleManager.Option.OPTIONS, whitelistOptions);
        return options;
    }

    private Set<String> toStringSet(String string)
    {
        String[] stringArray = StringUtils.split(string, ',');
        return Arrays.stream(stringArray)
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}
