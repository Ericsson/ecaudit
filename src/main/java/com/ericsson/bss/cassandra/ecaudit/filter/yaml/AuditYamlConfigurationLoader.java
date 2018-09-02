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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.io.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Implements an {@link AuditConfigurationLoader} that loads configuration from a YAML file.
 */
public final class AuditYamlConfigurationLoader implements AuditConfigurationLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditYamlConfigurationLoader.class);

    private final static String DEFAULT_CONFIG_FILE = "/etc/cassandra/conf/audit.yaml";

    /**
     * Property for the path to a {@link AuditConfig} file that configures audit logging.
     */
    public final static String PROPERTY_CONFIG_FILE = "com.ericsson.bss.cassandra.eaudit.config";

    private final Properties properties;

    /**
     * Construct a new {@link AuditYamlConfigurationLoader} instance that loads configuration from the provided
     * properties
     * @param properties
     *            where to get the property file url from
     */
    private AuditYamlConfigurationLoader(Properties properties)
    {
        this.properties = new Properties(properties);
    }

    @Override
    public AuditConfig loadConfig() throws ConfigurationException
    {
        URL url = getConfigURL(properties);
        InputStream input = null;

        try
        {
            LOG.info("Loading settings from {}", url);
            try
            {
                input = url.openStream();
            }
            catch (IOException e)
            {
                // Shouldn't happen if getConfigURL() is used
                throw new AssertionError(e);
            }

            final Constructor constructor = new Constructor(AuditConfig.class);
            final Yaml yaml = new Yaml(constructor);
            return (AuditConfig) yaml.load(input);
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

    private static URL getConfigURL(Properties properties)
    {
        String propertiesPath = properties.getProperty(PROPERTY_CONFIG_FILE, DEFAULT_CONFIG_FILE);
        if (!propertiesPath.isEmpty())
        {
            try
            {
                URL url = new File(propertiesPath).toURI().toURL();
                url.openStream().close();
                return url;
            }
            catch (IOException e)
            {
                throw new ConfigurationException("Failed to load configuration from " + propertiesPath, e);
            }
        }

        throw new ConfigurationException(
                "Audit configuration file not in properties, check Cassandra configuration for option: "
                        + PROPERTY_CONFIG_FILE);
    }

    /**
     * Loads a configuration from the path defined in the system properties.
     * @return an instance of {@link AuditConfig}.
     */
    public static AuditYamlConfigurationLoader withSystemProperties()
    {
        return withProperties(System.getProperties());
    }

    /**
     * Loads a configuration from the path defined in the given properties.
     * @param properties
     *            the properties containing the property url
     * @return an instance of {@link AuditYamlConfigurationLoader}.
     * @see AuditYamlConfigurationLoader#PROPERTY_CONFIG_FILE
     */
    public static AuditYamlConfigurationLoader withProperties(Properties properties)
    {
        return new AuditYamlConfigurationLoader(properties);
    }

}
