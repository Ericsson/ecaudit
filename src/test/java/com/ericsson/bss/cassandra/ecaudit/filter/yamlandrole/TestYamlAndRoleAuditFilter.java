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
package com.ericsson.bss.cassandra.ecaudit.filter.yamlandrole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.role.RoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.YamlAuditFilter;

@RunWith(MockitoJUnitRunner.class)
public class TestYamlAndRoleAuditFilter
{
    @Mock
    private AuditEntry auditEntry;

    @Mock
    private YamlAuditFilter yamlFilter;

    @Mock
    private RoleAuditFilter roleFilter;

    private YamlAndRoleAuditFilter combinedFilter;

    @Before
    public void before()
    {
        combinedFilter = new YamlAndRoleAuditFilter(yamlFilter, roleFilter);
    }

    @Test
    public void testFilteredByBothResultInFiltered()
    {
        when(yamlFilter.isFiltered(eq(auditEntry))).thenReturn(true);
        when(roleFilter.isFiltered(eq(auditEntry))).thenReturn(true);

        assertThat(combinedFilter.isFiltered(auditEntry)).isEqualTo(true);
    }

    @Test
    public void testFilteredByYamlOnlyResultInFiltered()
    {
        when(yamlFilter.isFiltered(eq(auditEntry))).thenReturn(true);
        when(roleFilter.isFiltered(eq(auditEntry))).thenReturn(false);

        assertThat(combinedFilter.isFiltered(auditEntry)).isEqualTo(true);
    }

    @Test
    public void testFilteredByRoleOnlyResultInFiltered()
    {
        when(yamlFilter.isFiltered(eq(auditEntry))).thenReturn(false);
        when(roleFilter.isFiltered(eq(auditEntry))).thenReturn(true);

        assertThat(combinedFilter.isFiltered(auditEntry)).isEqualTo(true);
    }

    @Test
    public void testFilteredByNoneResultInNotFiltered()
    {
        when(yamlFilter.isFiltered(eq(auditEntry))).thenReturn(false);
        when(roleFilter.isFiltered(eq(auditEntry))).thenReturn(false);

        assertThat(combinedFilter.isFiltered(auditEntry)).isEqualTo(false);
    }
}
