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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Properties;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.filter.yaml.AuditConfig;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.AuditYamlConfigurationLoader;

@RunWith(MockitoJUnitRunner.class)
public class TestAuditYamlConfigurationLoader
{
    @Test(expected = ConfigurationException.class)
    public void testMissingPropertyThrowsConfigurationException()
    {
        Properties properties = new Properties();
        AuditYamlConfigurationLoader loader = AuditYamlConfigurationLoader.withProperties(properties);

        loader.loadConfig();
    }

    @Test(expected = ConfigurationException.class)
    public void testEmptyPropertyThrowsConfigurationException()
    {
        Properties properties = new Properties();
        properties.put(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, "");
        AuditYamlConfigurationLoader loader = AuditYamlConfigurationLoader.withProperties(properties);

        loader.loadConfig();
    }

    @Test(expected = ConfigurationException.class)
    public void testInvalidPathPropertyThrowsConfigurationException()
    {
        Properties properties = new Properties();
        properties.put(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, "does_not_exist.yaml");

        AuditYamlConfigurationLoader loader = AuditYamlConfigurationLoader.withProperties(properties);
        loader.loadConfig();
    }

    @Test
    public void testEmptyConfigurationIsStillValid()
    {
        Properties properties = getProperties("missing_all.yaml");
        AuditYamlConfigurationLoader loader = AuditYamlConfigurationLoader.withProperties(properties);

        AuditConfig loadedConfig = loader.loadConfig();
        assertThat(loadedConfig).isNotNull();
        assertThat(loadedConfig.getWhitelist()).isNotNull().isEmpty();
    }

    @Test
    public void testLoadWhitelistAllPresent()
    {
        Properties properties = getProperties("mock_configuration.yaml");
        AuditYamlConfigurationLoader loader = AuditYamlConfigurationLoader.withProperties(properties);

        AuditConfig loadedConfig = loader.loadConfig();
        assertThat(loadedConfig.getWhitelist()).containsOnly("User1", "User2");
    }

    private static Properties getProperties(String fileName)
    {
        URL url = TestAuditYamlConfigurationLoader.class.getResource("/" + fileName);
        Properties properties = new Properties();
        properties.put(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, url.getPath());

        return properties;
    }
}