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
    private Map<RoleResource, Map<String, Set<IResource>>> whitelistMap;

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
            Map<String, Set<IResource>> whitelist = whitelistMap.get(invocation.getArgument(0));
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
        givenRoleIsWhitelisted("primary", "ALL", DataResource.root());
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithWhitelistedKsDoSelect()
    {
        givenRoleIsWhitelisted("primary", "ALL", DataResource.fromName("data/ks"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithWhitelistedTableDoSelect()
    {
        givenRoleIsWhitelisted("primary", "ALL", DataResource.fromName("data/ks/tbl"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithWhitelistedTableDoCAS()
    {
        givenRoleIsWhitelisted("primary", "ALL", DataResource.fromName("data/ks/tbl"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Sets.newHashSet(Permission.SELECT, Permission.MODIFY), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void primaryRoleWithOtherWhitelistedTableDoSelect()
    {
        givenRoleIsWhitelisted("primary", "ALL", DataResource.fromName("data/ks/tbl"));
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/other_tbl"))).isEqualTo(false);
    }

    @Test
    public void inheritedRoleWithWhitelistedDataRootDoSelect()
    {
        givenRoleIsWhitelisted("inherited", "ALL", DataResource.root());
        Set<RoleResource> effectiveRoles = givenRolesOfRequest("primary", "inherited");
        assertThat(filter.isFiltered(effectiveRoles, Collections.singleton(Permission.SELECT), DataResource.fromName("data/ks/tbl"))).isEqualTo(true);
    }

    @Test
    public void inheritedRoleWithWhitelistedConnectionDoConnect()
    {
        givenRoleIsWhitelisted("inherited", "ALL", ConnectionResource.root());
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

    private void givenRoleIsWhitelisted(String roleName, String operation, IResource resource)
    {
        whitelistMap.compute(RoleResource.role(roleName), (name, operWl) -> createOrExtend(operWl, operation, resource));
    }

    private Map<String, Set<IResource>> createOrExtend(Map<String, Set<IResource>> operWl, String operation, IResource resource)
    {
        Map<String, Set<IResource>> newOperaitonWhitelist = operWl != null ? operWl : Maps.newHashMap();
        newOperaitonWhitelist.compute(operation, (oper, res) -> createOrExtend(res, resource));
        return newOperaitonWhitelist;
    }

    private Set<IResource> createOrExtend(Set<IResource> resources, IResource resource)
    {
        Set<IResource> newResourceSet = resources != null ? resources : Sets.newHashSet();
        newResourceSet.add(resource);
        return newResourceSet;
    }
}
