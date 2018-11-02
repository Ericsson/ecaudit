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
package com.ericsson.bss.cassandra.ecaudit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.entry.factory.AuditEntryBuilderFactory;
import com.ericsson.bss.cassandra.ecaudit.facade.Auditor;
import com.ericsson.bss.cassandra.ecaudit.facade.DefaultAuditor;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.DefaultAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.role.RoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.YamlAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yamlandrole.YamlAndRoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.PasswordObfuscator;
import org.apache.cassandra.exceptions.ConfigurationException;

/**
 * Factory class for creating configured instances of {@link AuditAdapter}.
 */
public class AuditAdapterFactory
{
    private final static Logger LOG = LoggerFactory.getLogger(AuditAdapterFactory.class);

    public static final String FILTER_TYPE_PROPERTY_NAME = "ecaudit.filter_type";
    public static final String FILTER_TYPE_YAML = "YAML";
    public static final String FILTER_TYPE_ROLE = "ROLE";
    public static final String FILTER_TYPE_YAML_AND_ROLE = "YAML_AND_ROLE";
    public static final String FILTER_TYPE_NONE = "NONE";

    private volatile static AuditAdapter AUDIT_ADAPTER_INSTANCE = null;

    /**
     * Provide a reference to the {@link AuditAdapter} instance, creating it if necessary.
     *
     * The instance will be configured based on different system properties.
     *
     * @return a configured instance of {@link AuditAdapter}.
     */
    public synchronized AuditAdapter getInstance()
    {
        if (AUDIT_ADAPTER_INSTANCE != null)
        {
            return AUDIT_ADAPTER_INSTANCE;
        }

        AuditLogger logger = new Slf4jAuditLogger();
        PasswordObfuscator obfuscator = new PasswordObfuscator();

        AuditFilter filter = createFilter();

        Auditor auditor = new DefaultAuditor(logger, filter, obfuscator);
        AuditEntryBuilderFactory entryBuilderFactory = new AuditEntryBuilderFactory();
        AUDIT_ADAPTER_INSTANCE =  new AuditAdapter(auditor, entryBuilderFactory);

        return AUDIT_ADAPTER_INSTANCE;
    }

    /**
     * Construct an audit filter based on a system property.
     *
     * A role based filter will be created by default.
     *
     * @return a new audit filter
     */
    private AuditFilter createFilter()
    {
        String filterType = System.getProperty(FILTER_TYPE_PROPERTY_NAME, FILTER_TYPE_ROLE);

        switch (filterType)
        {
        case FILTER_TYPE_YAML:
            LOG.info("Audit whitelist from YAML file");
            return new YamlAuditFilter();
        case FILTER_TYPE_ROLE:
            LOG.info("Audit whitelist from ROLE options");
            return new RoleAuditFilter();
        case FILTER_TYPE_YAML_AND_ROLE:
            LOG.info("Audit whitelist from YAML file and ROLE options");
            return new YamlAndRoleAuditFilter();
        case FILTER_TYPE_NONE:
            LOG.info("No audit whitelist");
            return new DefaultAuditFilter();
        default:
            LOG.error("Unrecognized audit filter type: {}", filterType);
            throw new ConfigurationException(String.format("Unrecognized audit filter type: %s", filterType));
        }
    }
}
