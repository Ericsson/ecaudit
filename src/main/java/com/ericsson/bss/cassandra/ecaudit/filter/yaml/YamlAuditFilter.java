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
package com.ericsson.bss.cassandra.ecaudit.filter.yaml;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.bss.cassandra.ecaudit.auth.ConnectionResource;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;

/**
 * A simple whitelist filter that exempts certain users from being audited by having them in a whitelist.
 */
public class YamlAuditFilter implements AuditFilter
{
    private final List<String> whitelist;

    /**
     * Create a new instance of {@link YamlAuditFilter}.
     */
    public YamlAuditFilter()
    {
        this(AuditYamlConfigurationLoader.withSystemProperties());
    }

    /**
     * Create a new instance of {@link YamlAuditFilter}.
     *
     * @param config
     *            the configuration to load the whitelist from
     */
    YamlAuditFilter(AuditConfigurationLoader configuratoinLoader)
    {
        AuditConfig config = configuratoinLoader.loadConfig();
        whitelist = new ArrayList<>(config.getWhitelist());
    }

    @Override
    public boolean isFiltered(AuditEntry logEntry)
    {
        // When using YAML-based whitelist, always audit log authentication operations
        if (logEntry.getResource() instanceof ConnectionResource)
        {
            return false;
        }

        String user = logEntry.getUser();
        return whitelist.contains(user);
    }

}
