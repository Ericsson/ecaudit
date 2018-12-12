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
package com.ericsson.bss.cassandra.ecaudit.filter.role;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.auth.AuditWhitelistCache;
import com.ericsson.bss.cassandra.ecaudit.auth.ConnectionResource;
import com.ericsson.bss.cassandra.ecaudit.auth.WhitelistDataAccess;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestRoleAuditFilter
{
    @Mock
    private AuditWhitelistCache auditWhitelistCacheMock;
    private Map<RoleResource, Map<IResource, Set<Permission>>> whitelistMap;

    @Mock
    private WhitelistDataAccess whitelistDataAccessMock;

    private RoleAuditFilter filter;

    @Before
    public void before()
    {
        filter = new RoleAuditFilter(auditWhitelistCacheMock, whitelistDataAccessMock);

        whitelistMap = Maps.newHashMap();
        when(auditWhitelistCacheMock.getWhitelist(any(RoleResource.class)))
        .thenAnswer((invocation) -> {
            Map<IResource, Set<Permission>> whitelist = whitelistMap.get(invocation.getArgument(0));
            return whitelist != null ? whitelist : Collections.emptyMap();
        });
    }

    @Test
    public void testSetupDelegation()
    {
        filter.setup();
        verify(whitelistDataAccessMock, times(1)).setup();
    }

    @Test
    public void primaryRoleWithWhitelistedDataRootDoSelect()
    {
        givenRoleIsWhitelisted("primary", Permission.SELECT, DataResource.root());
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithWhitelistedKsDoSelect()
    {
        givenRoleIsWhitelisted("primary", Permission.SELECT, DataResource.fromName("data/ks"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithWhitelistedTableDoSelect()
    {
        givenRoleIsWhitelisted("primary", Permission.SELECT, DataResource.fromName("data/ks/tbl"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithWhitelistedTableDoModify()
    {
        givenRoleIsWhitelisted("primary", Permission.MODIFY, DataResource.fromName("data/ks/tbl"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.MODIFY), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithWhitelistedTableDoCAS()
    {
        givenRoleIsWhitelisted("primary", Permission.SELECT, DataResource.fromName("data/ks/tbl"));
        givenRoleIsWhitelisted("primary", Permission.MODIFY, DataResource.fromName("data/ks/tbl"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Sets.newHashSet(Permission.SELECT, Permission.MODIFY), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void mixedRoleWithWhitelistedTableDoCAS()
    {
        givenRoleIsWhitelisted("primary", Permission.SELECT, DataResource.fromName("data/ks/tbl"));
        givenRoleIsWhitelisted("inherited", Permission.MODIFY, DataResource.fromName("data/ks/tbl"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Sets.newHashSet(Permission.SELECT, Permission.MODIFY), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithOtherWhitelistedTableDoSelect()
    {
        givenRoleIsWhitelisted("primary", Permission.SELECT, DataResource.fromName("data/ks/tbl"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/other_tbl"))).isEqualTo(false);
    }

    @Test
    public void inheritedRoleWithWhitelistedDataRootDoSelect()
    {
        givenRoleIsWhitelisted("inherited", Permission.SELECT, DataResource.root());
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void inheritedRoleWithWhitelistedConnectionDoConnect()
    {
        givenRoleIsWhitelisted("inherited", Permission.EXECUTE, ConnectionResource.root());
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.EXECUTE), ConnectionResource.root())).isEqualTo(true);
    }

    @Test
    public void roleDoConnect()
    {
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.EXECUTE), ConnectionResource.root())).isEqualTo(false);
    }

    @Test
    public void roleDoModify()
    {
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.MODIFY), DataResource.fromName("data/ks/tbl"))).isEqualTo(false);
    }

    private Set<RoleResource> givenRolesOfRequest(String... roleNames)
    {
        return Arrays.stream(roleNames)
                     .map(RoleResource::role)
                     .collect(Collectors.toSet());
    }

    private void givenRoleIsWhitelisted(String roleName, Permission operation, IResource resource)
    {
        whitelistMap.compute(RoleResource.role(roleName), (name, operWl) -> createOrExtend(operWl, operation, resource));
    }

    private Map<IResource, Set<Permission>> createOrExtend(Map<IResource, Set<Permission>> operWl, Permission operation, IResource resource)
    {
        Map<IResource, Set<Permission>> newPermissionWhitelist = operWl != null ? operWl : Maps.newHashMap();
        newPermissionWhitelist.compute(resource, (res, oper) -> createOrExtend(oper, operation));
        return newPermissionWhitelist;
    }

    private Set<Permission> createOrExtend(Set<Permission> permissions, Permission permission)
    {
        Set<Permission> newPermissionSet = permissions != null ? permissions : Sets.newHashSet();
        newPermissionSet.add(permission);
        return newPermissionSet;
    }
}
