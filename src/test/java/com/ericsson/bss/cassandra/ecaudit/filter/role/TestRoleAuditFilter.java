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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.auth.ConnectionResource;

@RunWith(MockitoJUnitRunner.class)
public class TestRoleAuditFilter
{
    @Test
    public void testAllResourcesWhitelisted()
    {
        RoleAuditFilter filter = new RoleAuditFilter();
        Map<String, List<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", Arrays.asList(DataResource.root(), ConnectionResource.root()));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(true);
    }

    @Test
    public void testDataResourcesWhitelisted()
    {
        RoleAuditFilter filter = new RoleAuditFilter();
        Map<String, List<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", Arrays.asList(DataResource.root()));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(false);
    }

    @Test
    public void testKeyspaceResourceWhitelisted()
    {
        RoleAuditFilter filter = new RoleAuditFilter();
        Map<String, List<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", Arrays.asList(DataResource.fromName("data/wlks1"), DataResource.fromName("data/wlks2")));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks/tbl"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/wlk1s/wltbl1"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/wlks1"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(false);
    }

    @Test
    public void testTableResourceWhitelisted()
    {
        RoleAuditFilter filter = new RoleAuditFilter();
        Map<String, List<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", Arrays.asList(DataResource.fromName("data/wlks1/wltbl1"), DataResource.fromName("data/wlks2/wltb2l")));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/ks/tbl"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data/wlks1/wltbl1"))).isEqualTo(true);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(false);
    }

    @Test
    public void testConnectionResourcesWhitelisted()
    {
        RoleAuditFilter filter = new RoleAuditFilter();
        Map<String, List<IResource>> optionMap = Collections.singletonMap("audit_whitelist_for_all", Arrays.asList(ConnectionResource.root()));

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(true);
    }

    @Test
    public void testNoWhitelist()
    {
        RoleAuditFilter filter = new RoleAuditFilter();
        Map<String, List<IResource>> optionMap = Collections.emptyMap();

        assertThat(filter.isResourceOperationWhitelisted(optionMap, DataResource.fromName("data"))).isEqualTo(false);
        assertThat(filter.isResourceOperationWhitelisted(optionMap, ConnectionResource.fromName("connections"))).isEqualTo(false);
    }

}
