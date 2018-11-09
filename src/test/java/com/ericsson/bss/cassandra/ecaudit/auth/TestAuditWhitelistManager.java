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

import java.util.Collections;
import java.util.Map;
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
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.UnauthorizedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        Config.setClientMode(true);
    }

    @Before
    public void before()
    {
        when(performer.getName()).thenReturn("bob");
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
        Config.setClientMode(false);
    }

    @Test
    public void testGrantAtCreate()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data, connections"));

        whitelistManager.createRoleWhitelist(performer, role, options);

        verify(mockWhitelistDataAccess, times(1)).addToWhitelist(eq(role), eq("ALL"),
                eq(ImmutableSet.of(DataResource.fromName("data"), ConnectionResource.fromName("connections"))));
    }

    @Test
    public void testGrantLevelTwoResourceAtCreate()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data/myks"));

        whitelistManager.createRoleWhitelist(performer, role, options);

        verify(mockWhitelistDataAccess, times(1)).addToWhitelist(eq(role), eq("ALL"),
                eq(ImmutableSet.of(DataResource.fromName("data/myks"))));
    }

    @Test(expected = InvalidRequestException.class)
    public void testRevokeAtCreateIsRejected()
    {
        RoleOptions options = createRoleOptions(
        Collections.singletonMap("revoke_audit_whitelist_for_all", "data"));

        whitelistManager.createRoleWhitelist(performer, role, options);
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
    public void testUnknownOptionAtCreateIsRejected()
    {
        RoleOptions options = createRoleOptions(Collections.singletonMap("unknown_option", "guck"));

        whitelistManager.createRoleWhitelist(performer, role, options);
    }

    @Test(expected = InvalidRequestException.class)
    public void testUnsupportedOperationAtCreateIsRejected()
    {
        RoleOptions options = createRoleOptions(Collections.singletonMap("grant_audit_whitelist_for_execute", "data"));

        whitelistManager.createRoleWhitelist(performer, role, options);
    }

    @Test(expected = InvalidRequestException.class)
    public void testUnsupportedResourceAtCreateIsRejected()
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

        verify(mockWhitelistDataAccess, times(1)).addToWhitelist(eq(role), eq("ALL"),
                eq(ImmutableSet.of(DataResource.fromName("data"))));
    }

    @Test
    public void testGrantLevelTwoResourceAtAlter()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_all", "data/myks"));

        whitelistManager.alterRoleWhitelist(performer, role, options);

        verify(mockWhitelistDataAccess, times(1)).addToWhitelist(eq(role), eq("ALL"),
                eq(ImmutableSet.of(DataResource.fromName("data/myks"))));
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

        verify(mockWhitelistDataAccess, times(1)).removeFromWhitelist(eq(role), eq("ALL"),
                eq(ImmutableSet.of(ConnectionResource.fromName("connections"))));
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
    public void testUnknownOptionAtAlterIsRejected()
    {
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("unknown_option", "guck"));

        whitelistManager.alterRoleWhitelist(performer, role, options);
    }

    @Test(expected = InvalidRequestException.class)
    public void testUnsupportedOperationAtAlterIsRejected()
    {
        RoleOptions options = createRoleOptions(Collections.singletonMap("grant_audit_whitelist_for_execute", "data"));

        whitelistManager.alterRoleWhitelist(performer, role, options);
    }

    @Test(expected = InvalidRequestException.class)
    public void testUnsupportedResourceAtAlterIsRejected()
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
        Set<IResource> expectedResources = ImmutableSet.of(DataResource.fromName("data/someks/sometable"), ConnectionResource.fromName("connections"));
        Map<String, Set<IResource>> daoWhitelist = Collections.singletonMap("ALL", expectedResources);

        when(mockWhitelistDataAccess.getWhitelist(eq(role)))
                .thenReturn(daoWhitelist);

        Map<String, Set<IResource>> whitelistOptions = whitelistManager.getRoleWhitelist(role);

        verify(mockWhitelistDataAccess, times(1)).getWhitelist(eq(role));
        assertThat(whitelistOptions.keySet()).containsExactly("audit_whitelist_for_all");
        assertThat(whitelistOptions.get("audit_whitelist_for_all")).isEqualTo(expectedResources);
    }

    @Test
    public void testDropWhitelist()
    {
        whitelistManager.dropRoleWhitelist(role);
        verify(mockWhitelistDataAccess, times(1)).deleteWhitelist(eq(role));
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
}
