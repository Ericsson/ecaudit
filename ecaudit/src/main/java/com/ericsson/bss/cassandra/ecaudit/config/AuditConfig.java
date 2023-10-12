/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.config;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;

import org.apache.cassandra.config.ParameterizedClass;
import org.apache.cassandra.exceptions.ConfigurationException;

public class AuditConfig
{
    private final AuditYamlConfigurationLoader yamlConfigurationLoader;
    private AuditYamlConfig yamlConfig;

    @VisibleForTesting
    AuditConfig(AuditYamlConfigurationLoader yamlConfigurationLoader)
    {
        this.yamlConfigurationLoader = yamlConfigurationLoader;
    }

    public static AuditConfig getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder
    {
        private static final AuditConfig INSTANCE = new AuditConfig(AuditYamlConfigurationLoader.withSystemProperties());
    }

    public List<String> getYamlWhitelist() throws ConfigurationException
    {
        loadConfigIfNeeded();
        if (!yamlConfig.isFromFile())
        {
            throw new ConfigurationException("No audit configuration file found for yaml based whitelist");
        }
        return yamlConfig.getWhitelist();
    }

    public ParameterizedClass getLoggerBackendParameters() throws ConfigurationException
    {
        loadConfigIfNeeded();
        return yamlConfig.getLoggerBackendParameters();
    }

    public boolean isPostLogging()
    {
        loadConfigIfNeeded();
        return yamlConfig.isPostLogging();
    }

    public String getWrappedAuthorizer()
    {
        loadConfigIfNeeded();
        return yamlConfig.getWrappedAuthorizer();
    }

    public String getWrappedAuthenticator()
    {
        loadConfigIfNeeded();

        return yamlConfig.getWrappedAuthenticator();
    }

    public String getBoundValueSuppressor()
    {
        loadConfigIfNeeded();
        return yamlConfig.getBoundValueSuppressor();
    }

    public int getWhitelistCacheValidity()
    {
        loadConfigIfNeeded();
        return yamlConfig.getWhitelistCacheValidity();
    }

    public void setWhitelistCacheValidity(int whitelistCacheValidityInMs)
    {
        loadConfigIfNeeded();
        yamlConfig.setWhitelistCacheValidity(whitelistCacheValidityInMs);
    }

    public int getWhitelistCacheUpdateInterval()
    {
        loadConfigIfNeeded();
        return yamlConfig.getWhitelistCacheUpdateInterval();
    }

    public void setWhitelistCacheUpdateInterval(int whitelistCacheUpdateIntervalInMs)
    {
        loadConfigIfNeeded();
        yamlConfig.setWhitelistCacheUpdateInterval(whitelistCacheUpdateIntervalInMs);
    }

    public int getWhitelistCacheMaxEntries()
    {
        loadConfigIfNeeded();
        return yamlConfig.getWhitelistCacheMaxEntries();
    }

    public void setWhitelistCacheMaxEntries(int whitelistCacheMaxEntries)
    {
        loadConfigIfNeeded();
        yamlConfig.setWhitelistCacheMaxEntries(whitelistCacheMaxEntries);
    }
    public boolean isSuppressPrepareStatements()
    {
        loadConfigIfNeeded();
        return yamlConfig.isSuppressPrepareStatements();
    }

    private synchronized void loadConfigIfNeeded()
    {
        if (yamlConfig == null)
        {
            yamlConfig = yamlConfigurationLoader.loadConfig();
        }
    }
}
