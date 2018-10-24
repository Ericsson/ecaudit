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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.auth.ConnectionResource;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestRoleAuditFilter
{
    private RoleAuditFilter filter;

    @Before
    public void before()
    {
        filter = new RoleAuditFilter();
    }

    @Test
    public void testAllResourcesWhitelisted()
    {
        Map<String, Set<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", ImmutableSet.of(DataResource.root(), ConnectionResource.root()));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks/table"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(true);
    }

    @Test
    public void testDataResourcesWhitelisted()
    {
        Map<String, Set<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", ImmutableSet.of(DataResource.root()));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks/table"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(false);
    }

    @Test
    public void testKeyspaceResourceWhitelisted()
    {
        Map<String, Set<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", ImmutableSet.of(DataResource.fromName("data/ks1"), DataResource.fromName("data/ks2")));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks1/table"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks2/table"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks3/table"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(false);
    }

    @Test
    public void testTableResourceWhitelisted()
    {
        Map<String, Set<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", ImmutableSet.of(DataResource.fromName("data/ks1/table1"), DataResource.fromName("data/ks2/table2")));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks1/table1"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks2/table2"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks2/table1"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks3/table3"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(false);
    }

    @Test
    public void testConnectionResourcesWhitelisted()
    {
        Map<String, Set<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", ImmutableSet.of(ConnectionResource.root()));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks/table"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(true);
    }

    @Test
    public void testNoWhitelist()
    {
        Map<String, Set<IResource>> optionMap = Collections.emptyMap();
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks/table"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(false);
    }
}
