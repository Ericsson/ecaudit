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
package com.ericsson.bss.cassandra.ecaudit.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.io.util.FileUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Loads configuration from a YAML file.
 */
public final class AuditYamlConfigurationLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditYamlConfigurationLoader.class);

    private static final String DEFAULT_CONFIG_FILE = "audit.yaml";

    /**
     * Property for the path to a {@link AuditYamlConfig} file that configures audit logging.
     */
    public static final String PROPERTY_CONFIG_FILE = "com.ericsson.bss.cassandra.ecaudit.config";

    private final Properties properties;

    /**
     * Construct a new {@link AuditYamlConfigurationLoader} instance that loads configuration from the provided
     * properties
     *
     * @param properties where to get the property file url from
     */
    private AuditYamlConfigurationLoader(Properties properties)
    {
        this.properties = new Properties(properties);
    }

    /**
     * Loads a {@link AuditYamlConfig} object.
     *
     * @return the {@link AuditYamlConfig}.
     * @throws ConfigurationException
     *             if the configuration cannot be properly loaded.
     */
    AuditYamlConfig loadConfig() throws ConfigurationException
    {
        URL url = getConfigURL();

        if (url == null)
        {
            return AuditYamlConfig.createWithoutFile();
        }

        InputStream input = null;
        try
        {
            LOG.info("Loading audit settings from {}", url);
            try
            {
                input = url.openStream();
            }
            catch (IOException e)
            {
                // Shouldn't happen if getConfigURL() is used
                throw new AssertionError(e);
            }

            final Constructor constructor = new Constructor(AuditYamlConfig.class);
            final Yaml yaml = new Yaml(constructor);

            AuditYamlConfig auditYamlConfig = (AuditYamlConfig) yaml.load(input);

            if (auditYamlConfig == null) {
                return new AuditYamlConfig();
            }

            return auditYamlConfig;
        }
        catch (YAMLException e)
        {
            throw new ConfigurationException("Invalid configuration file", e);
        }
        finally
        {
            FileUtils.closeQuietly(input);
        }
    }

    private URL getConfigURL()
    {
        String customConfigPath = properties.getProperty(PROPERTY_CONFIG_FILE);

        URL url;
        if (customConfigPath != null)
        {
            LOG.debug("Looking for audit config at " + customConfigPath);
            try
            {
                url = new File(customConfigPath).toURI().toURL();
                url.openStream().close();
            }
            catch (IOException e)
            {
                throw new ConfigurationException("Failed to load configuration from " + customConfigPath, e);
            }
        }
        else
        {
            LOG.debug("Looking for audit config in Cassandra config directory");
            url = AuditYamlConfigurationLoader.class.getClassLoader().getResource(DEFAULT_CONFIG_FILE);
        }

        return url;
    }

    /**
     * Loads a configuration from the path defined in the system properties.
     *
     * @return an instance of {@link AuditYamlConfig}.
     */
    static AuditYamlConfigurationLoader withSystemProperties()
    {
        return withProperties(System.getProperties());
    }

    /**
     * Loads a configuration from the path defined in the given properties.
     *
     * @param properties the properties containing the property url
     * @return an instance of {@link AuditYamlConfigurationLoader}.
     * @see AuditYamlConfigurationLoader#PROPERTY_CONFIG_FILE
     */
    static AuditYamlConfigurationLoader withProperties(Properties properties)
    {
        return new AuditYamlConfigurationLoader(properties);
    }
}
