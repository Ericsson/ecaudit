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

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

import org.apache.cassandra.exceptions.ConfigurationException;

public class AuditConfig
{
    private static final String DEFAULT_FORMAT = "client:'${CLIENT}'|user:'${USER}'{?|batchId:'${BATCH_ID}'?}|status:'${STATUS}'|operation:'${OPERATION}'";

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

    public String getLogFormat() throws ConfigurationException
    {
        loadConfigIfNeeded();

        return Optional.ofNullable(yamlConfig.getSlf4j())
                       .map(AuditYamlSlf4jConfig::getLogFormat)
                       .orElse(DEFAULT_FORMAT);
    }

    public Optional<DateTimeFormatter> getTimeFormatter() throws ConfigurationException
    {
        loadConfigIfNeeded();
        try
        {
            Optional<DateTimeFormatter> timeFormatter = getFormatter();
            return timeFormatter;
        }
        catch (IllegalArgumentException e)
        {
            throw new ConfigurationException("Invalid time format configuration.", e);
        }
        catch (DateTimeException e)
        {
            throw new ConfigurationException("Invalid time zone configuration.", e);
        }

    }

    private Optional<DateTimeFormatter> getFormatter()
    {
        Optional<AuditYamlSlf4jConfig> slf4j = Optional.ofNullable(yamlConfig.getSlf4j());

        ZoneId timeZoneId = slf4j.map(AuditYamlSlf4jConfig::getTimeZone)
                                 .map(ZoneId::of)
                                 .orElse(ZoneId.systemDefault());

        return slf4j.map(AuditYamlSlf4jConfig::getTimeFormat)
                    .map(DateTimeFormatter::ofPattern)
                    .map(formatter -> formatter.withZone(timeZoneId));
    }

    private synchronized void loadConfigIfNeeded()
    {
        if (yamlConfig == null)
        {
            yamlConfig = yamlConfigurationLoader.loadConfig();
        }
    }
}
