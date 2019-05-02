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
package com.ericsson.bss.cassandra.ecaudit;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;
import com.ericsson.bss.cassandra.ecaudit.entry.factory.AuditEntryBuilderFactory;
import com.ericsson.bss.cassandra.ecaudit.facade.Auditor;
import com.ericsson.bss.cassandra.ecaudit.facade.DefaultAuditor;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.DefaultAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.role.RoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.YamlAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yamlandrole.YamlAndRoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.PasswordObfuscator;
import org.apache.cassandra.config.ParameterizedClass;
import org.apache.cassandra.exceptions.ConfigurationException;

/**
 * Factory class for creating configured instances of AuditAdapter.
 */
class AuditAdapterFactory
{
    private final static Logger LOG = LoggerFactory.getLogger(AuditAdapterFactory.class);

    static final String FILTER_TYPE_PROPERTY_NAME = "ecaudit.filter_type";
    static final String FILTER_TYPE_YAML = "YAML";
    static final String FILTER_TYPE_ROLE = "ROLE";
    static final String FILTER_TYPE_YAML_AND_ROLE = "YAML_AND_ROLE";
    static final String FILTER_TYPE_NONE = "NONE";

    /**
     * Create a new AuditAdapter instance.
     *
     * The instance will be configured based on different system properties and the audit.yaml file.
     *
     * @return a new configured instance of AuditAdapter.
     */
    static AuditAdapter createAuditAdapter()
    {
        return createAuditAdapter(AuditConfig.getInstance());
    }

    @VisibleForTesting
    static AuditAdapter createAuditAdapter(AuditConfig auditConfig)
    {
        AuditLogger logger = createLogger(auditConfig);
        AuditFilter filter = createFilter(auditConfig);
        PasswordObfuscator obfuscator = new PasswordObfuscator();

        Auditor auditor = new DefaultAuditor(logger, filter, obfuscator);
        AuditEntryBuilderFactory entryBuilderFactory = new AuditEntryBuilderFactory();

        return new AuditAdapter(auditor, entryBuilderFactory, auditConfig.isPostLogging());
    }

    /**
     * Construct a audit logger backend based on yaml config.
     *
     * @param auditConfig the audit configuration
     * @return a new audit logger backend
     */
    private static AuditLogger createLogger(AuditConfig auditConfig)
    {
        ParameterizedClass auditLoggerParameters = auditConfig.getLoggerBackendParameters();

        try
        {
            Class<?> loggerBackendClass = Class.forName(auditLoggerParameters.class_name);
            Map<String, String> parameters = auditLoggerParameters.parameters != null ? auditLoggerParameters.parameters : Collections.emptyMap();
            return (AuditLogger) loggerBackendClass.getConstructor(Map.class).newInstance(parameters);
        }
        catch (InvocationTargetException e)
        {
            throw new ConfigurationException("Audit logger backend failed at initialization: " + e.getTargetException().getMessage(), e);
        }
        catch (Exception e)
        {
            throw new ConfigurationException("Failed to initialize audit logger backend: " + auditLoggerParameters, e);
        }
    }

    /**
     * Construct an audit filter based on a system property.
     *
     * A role based filter will be created by default.
     *
     * @param auditConfig the audit configuration
     * @return a new audit filter
     */
    private static AuditFilter createFilter(AuditConfig auditConfig)
    {
        String filterType = System.getProperty(FILTER_TYPE_PROPERTY_NAME, FILTER_TYPE_ROLE);

        switch (filterType)
        {
        case FILTER_TYPE_YAML:
            LOG.info("Audit whitelist from YAML file");
            return new YamlAuditFilter(auditConfig);
        case FILTER_TYPE_ROLE:
            LOG.info("Audit whitelist from ROLE options");
            return new RoleAuditFilter();
        case FILTER_TYPE_YAML_AND_ROLE:
            LOG.info("Audit whitelist from YAML file and ROLE options");
            return new YamlAndRoleAuditFilter(auditConfig);
        case FILTER_TYPE_NONE:
            LOG.info("No audit whitelist");
            return new DefaultAuditFilter();
        default:
            LOG.error("Unrecognized audit filter type: {}", filterType);
            throw new ConfigurationException(String.format("Unrecognized audit filter type: %s", filterType));
        }
    }
}
