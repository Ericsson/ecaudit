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

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.role.RoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.YamlAuditFilter;

/**
 * The Yaml 'n' Role filter is a combination of filters.
 *
 * If the log entry is filtered out by any of the filters, it will be exempt by the combined filter.
 */
public class YamlAndRoleAuditFilter implements AuditFilter
{
    private final YamlAuditFilter yamlFilter;
    private final RoleAuditFilter roleFilter;

    public YamlAndRoleAuditFilter()
    {
        this(new YamlAuditFilter(), new RoleAuditFilter());
    }

    // Visible for testing
    YamlAndRoleAuditFilter(YamlAuditFilter yamlFilter, RoleAuditFilter roleFilter)
    {
        this.yamlFilter = yamlFilter;
        this.roleFilter = roleFilter;
    }

    @Override
    public boolean isFiltered(AuditEntry logEntry)
    {
        return yamlFilter.isFiltered(logEntry) || roleFilter.isFiltered(logEntry);
    }
}
