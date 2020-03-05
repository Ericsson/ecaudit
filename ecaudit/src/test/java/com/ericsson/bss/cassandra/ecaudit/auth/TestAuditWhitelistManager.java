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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.UnauthorizedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
        ClientInitializer.beforeClass();
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
        ClientInitializer.afterClass();
    }

    @Test
    public void testSetupDelegation()
    {
        whitelistManager.setup();
        verify(mockWhitelistDataAccess, times(1)).setup();
    }

    @Test
    public void testWhitelistAtCreateIsRejected()
    {
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_select", "data"));

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> whitelistManager.createRoleOption(options));
    }

    @Test
    public void testNoOptionAtCreate()
    {
        RoleOptions options = new RoleOptions();

        whitelistManager.createRoleOption(options);
    }

    @Test
    public void testGrantAtAlter()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_select", "data"));

        whitelistManager.alterRoleOption(performer, role, options);

        verify(mockWhitelistDataAccess, times(1))
        .addToWhitelist(eq(role), eq(DataResource.fromName("data")), eq(ImmutableSet.of(Permission.SELECT)));
    }

    @Test
    public void testGrantLevelTwoResourceAtAlter()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_modify", "data/myks"));

        whitelistManager.alterRoleOption(performer, role, options);

        verify(mockWhitelistDataAccess, times(1))
        .addToWhitelist(eq(role), eq(DataResource.fromName("data/myks")), eq(ImmutableSet.of(Permission.MODIFY)));
    }

    @Test
    public void testGrantOnGrantBasedResourceAtAlter()
    {
        DataResource dataResource = DataResource.fromName("data/myks");
        when(performer.getPermissions(eq(dataResource))).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_modify", "grants/data/myks"));

        whitelistManager.alterRoleOption(performer, role, options);

        verify(mockWhitelistDataAccess, times(1))
        .addToWhitelist(eq(role), eq(GrantResource.fromResource(dataResource)), eq(ImmutableSet.of(Permission.MODIFY)));
    }

    @Test
    public void testGrantOnTopLevelGrantBasedResourceAtAlterIsDenied()
    {
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_modify", "grants"));

        assertThatExceptionOfType(UnauthorizedException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testGrantOnTopLevelGrantBasedResourceAtAlterIsAllowedBySuperUser()
    {
        when(performer.isSuper()).thenReturn(true);
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("grant_audit_whitelist_for_modify", "grants"));

        whitelistManager.alterRoleOption(performer, role, options);

        verify(mockWhitelistDataAccess, times(1))
        .addToWhitelist(eq(role), eq(GrantResource.root()), eq(ImmutableSet.of(Permission.MODIFY)));
    }

    @Test
    public void testGrantAtAlterIsDenied()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.MODIFY, Permission.SELECT));
        RoleOptions options = createRoleOptions(
                ImmutableMap.of("grant_audit_whitelist_for_execute", "connections"));

        assertThatExceptionOfType(UnauthorizedException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testRevokeAtAlter()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.AUTHORIZE));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("revoke_audit_whitelist_for_execute", "connections"));

        whitelistManager.alterRoleOption(performer, role, options);

        verify(mockWhitelistDataAccess, times(1))
        .removeFromWhitelist(eq(role), eq(ConnectionResource.fromName("connections")), eq(ImmutableSet.of(Permission.EXECUTE)));
    }

    @Test
    public void testRevokeAtAlterIsDenied()
    {
        when(performer.getPermissions(any())).thenReturn(ImmutableSet.of(Permission.MODIFY, Permission.SELECT));
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("revoke_audit_whitelist_for_modify", "data"));

        assertThatExceptionOfType(UnauthorizedException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testUnknownOptionAtAlterIsRejected()
    {
        RoleOptions options = createRoleOptions(
                Collections.singletonMap("unknown_option", "guck"));

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testUnsupportedGrantOperationAtAlterIsRejected()
    {
        RoleOptions options = createRoleOptions(Collections.singletonMap("grant_audit_whitelist_for_execute", "data"));

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testUnsupportedRevokeOperationAtAlterIsRejected()
    {
        RoleOptions options = createRoleOptions(Collections.singletonMap("revoke_audit_whitelist_for_execute", "data"));

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testUnsupportedResourceAtAlterIsRejected()
    {
        RoleOptions options = createRoleOptions(
        Collections.singletonMap("grant_audit_whitelist_for_all", "mbean"));

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testSeveralOperationsAtAlterIsRejected()
    {
        RoleOptions options = createRoleOptions(
        ImmutableMap.of("grant_audit_whitelist_for_select", "data",
                        "grant_audit_whitelist_for_modify", "data"));

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testSeveralResourcesAtAlterIsRejected()
    {
        RoleOptions options = createRoleOptions(
        Collections.singletonMap("grant_audit_whitelist_for_select", "data/system,data/system_auth"));

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testNoOptionAtAlter()
    {
        RoleOptions options = new RoleOptions();

        whitelistManager.alterRoleOption(performer, role, options);
    }

    @Test
    public void testGetWhitelist()
    {
        Map<IResource, Set<Permission>> daoWhitelist = ImmutableMap.of(DataResource.fromName("data/someks/sometable"), EnumSet.of(Permission.SELECT),
                                                                            ConnectionResource.fromName("connections"), EnumSet.of(Permission.EXECUTE));

        when(mockWhitelistDataAccess.getWhitelist(eq(role)))
                .thenReturn(daoWhitelist);

        Map<String, String> whitelistOptions = whitelistManager.getRoleWhitelist(role);

        verify(mockWhitelistDataAccess, times(1)).getWhitelist(eq(role));
        assertThat(whitelistOptions.keySet()).containsExactlyInAnyOrder("AUDIT WHITELIST ON data/someks/sometable", "AUDIT WHITELIST ON connections");
        assertThat(whitelistOptions.get("AUDIT WHITELIST ON data/someks/sometable")).isEqualTo(Permission.SELECT.name());
        assertThat(whitelistOptions.get("AUDIT WHITELIST ON connections")).isEqualTo(Permission.EXECUTE.name());
    }

    @Test
    public void testGetEmptyWhitelist()
    {
        Map<IResource, Set<Permission>> daoWhitelist = Collections.emptyMap();

        when(mockWhitelistDataAccess.getWhitelist(eq(role)))
        .thenReturn(daoWhitelist);

        Map<String, String> whitelistOptions = whitelistManager.getRoleWhitelist(role);

        verify(mockWhitelistDataAccess, times(1)).getWhitelist(eq(role));
        assertThat(whitelistOptions.keySet()).isEmpty();
    }

    @Test
    public void testDropWhitelist()
    {
        whitelistManager.dropRoleWhitelist(role);
        verify(mockWhitelistDataAccess, times(1)).deleteWhitelist(eq(role));
    }

    @Test
    public void testDropLegacyTable()
    {
        when(performer.isSuper()).thenReturn(true);
        when(role.getRoleName()).thenReturn("bob");

        RoleOptions options = createRoleOptions(
        Collections.singletonMap("drop_legacy_audit_whitelist_table", "now"));

        whitelistManager.alterRoleOption(performer, role, options);

        verify(mockWhitelistDataAccess, times(1))
        .dropLegacyWhitelistTable();
    }

    @Test
    public void testDropLegacyTableWithWrongValueIsRejected()
    {
        RoleOptions options = createRoleOptions(
        Collections.singletonMap("drop_legacy_audit_whitelist_table", "not"));

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testDropLegacyTableWithOtherRoleIsRejected()
    {
        when(performer.isSuper()).thenReturn(true);
        when(role.getRoleName()).thenReturn("hans");

        RoleOptions options = createRoleOptions(
        Collections.singletonMap("drop_legacy_audit_whitelist_table", "now"));

        assertThatExceptionOfType(UnauthorizedException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testDropLegacyTableWithNonSuperIsRejected()
    {
        RoleOptions options = createRoleOptions(
        Collections.singletonMap("drop_legacy_audit_whitelist_table", "now"));

        assertThatExceptionOfType(UnauthorizedException.class)
        .isThrownBy(() -> whitelistManager.alterRoleOption(performer, role, options));
    }

    @Test
    public void testUnknownOption()
    {
        WhitelistOperation invalidOperation = Mockito.mock(WhitelistOperation.class);

        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() ->  whitelistManager.dispatchOperation(invalidOperation, performer, role, null));
    }

    private RoleOptions createRoleOptions(Map<String, String> whitelistOptions)
    {
        RoleOptions options = new RoleOptions();
        options.setOption(IRoleManager.Option.OPTIONS, whitelistOptions);
        return options;
    }
}
