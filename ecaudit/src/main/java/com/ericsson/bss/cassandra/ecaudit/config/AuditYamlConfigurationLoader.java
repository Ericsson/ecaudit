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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Loads configuration from a YAML file.
 */
public final class AuditYamlConfigurationLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditYamlConfigurationLoader.class);

    private static final String DEFAULT_CONFIG_FILE = "audit.yaml";

    /**
     * Property for the path to a file that is matching {@link AuditYamlConfig}.
     */
    public static final String PROPERTY_CONFIG_FILE = "com.ericsson.bss.cassandra.ecaudit.config";

    private final Properties properties;

    /**
     * Construct a new AuditYamlConfigurationLoader instance that loads configuration from the provided
     * properties
     *
     * @param properties where to get the property file url from
     */
    private AuditYamlConfigurationLoader(Properties properties)
    {
        this.properties = new Properties(properties);
    }

    /**
     * Loads a AuditYamlConfig object.
     *
     * @return the yaml configuration object.
     * @throws ConfigurationException
     *             if the configuration cannot be properly loaded
     */
    AuditYamlConfig loadConfig() throws ConfigurationException
    {
        if (hasCustomConfigPath())
        {
            return loadConfigAtCustomPath();
        }
        else if (hasConfigAtDefaultPath())
        {
            return loadConfigAtDefaultPath();
        }
        else
        {
            return AuditYamlConfig.createWithoutFile();
        }
    }

    private boolean hasCustomConfigPath()
    {
        return properties.getProperty(PROPERTY_CONFIG_FILE) != null;
    }

    private boolean hasConfigAtDefaultPath()
    {
        return getDefaultConfigURL() != null;
    }

    private AuditYamlConfig loadConfigAtCustomPath()
    {
        URL url = getCustomConfigURL();
        return loadConfigFromUrl(url);
    }

    private AuditYamlConfig loadConfigAtDefaultPath()
    {
        URL url = getDefaultConfigURL();
        return loadConfigFromUrl(url);
    }

    private URL getCustomConfigURL()
    {
        String customConfigPath = properties.getProperty(PROPERTY_CONFIG_FILE);
        try
        {
            URL url = new File(customConfigPath).toURI().toURL();
            url.openStream().close();
            return url;
        }
        catch (IOException e)
        {
            throw new ConfigurationException("No audit configuration file found at: " + customConfigPath, e);
        }
    }

    private URL getDefaultConfigURL()
    {
        return Thread.currentThread().getContextClassLoader().getResource(DEFAULT_CONFIG_FILE);
    }

    private AuditYamlConfig loadConfigFromUrl(URL url)
    {
        LOG.info("Loading audit settings from {}", url);

        try (InputStream input = url.openStream())
        {
            SafeConstructor constructor = new CustomClassLoaderConstructor(AuditYamlConfig.class, Thread.currentThread().getContextClassLoader());
            Yaml yaml = new Yaml(constructor);

            AuditYamlConfig auditYamlConfig = (AuditYamlConfig) yaml.load(input);

            if (auditYamlConfig == null) {
                // File is valid but empty
                return new AuditYamlConfig();
            }

            return auditYamlConfig;
        }
        catch (IOException e)
        {
            throw new AssertionError(e);
        }
        catch (YAMLException e)
        {
            throw new ConfigurationException("Invalid configuration file", e);
        }
    }

    /**
     * Loads a configuration from the path defined in the system properties.
     *
     * @return an instance of AuditYamlConfig
     */
    static AuditYamlConfigurationLoader withSystemProperties()
    {
        return withProperties(System.getProperties());
    }

    /**
     * Loads a configuration from the path defined in the given properties.
     *
     * @param properties the properties containing the property url
     * @return an instance of AuditYamlConfigurationLoader
     * @see AuditYamlConfigurationLoader#PROPERTY_CONFIG_FILE
     */
    static AuditYamlConfigurationLoader withProperties(Properties properties)
    {
        return new AuditYamlConfigurationLoader(properties);
    }
}
