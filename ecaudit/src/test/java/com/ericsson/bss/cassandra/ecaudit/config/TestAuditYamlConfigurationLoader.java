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

import java.net.URL;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.SuppressNothing;
import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.ConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

public class TestAuditYamlConfigurationLoader
{
    @BeforeClass
    public static void beforeClass()
    {
        ClientInitializer.beforeClass();
    }

    @AfterClass
    public static void afterClass()
    {
        ClientInitializer.afterClass();
    }

    @Test
    public void testEmptyPropertyThrowsConfigurationException()
    {
        Properties properties = new Properties();
        properties.put(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, "");

        AuditConfig config = givenLoadedConfig(properties);

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getYamlWhitelist);
        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getLoggerBackendParameters);
    }

    @Test
    public void testNonExistingPathPropertyThrowsConfigurationExceptionOnAllGetters()
    {
        Properties properties = new Properties();
        properties.put(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, "does_not_exist.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getYamlWhitelist);
        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getLoggerBackendParameters);
    }

    // If ecAudit is configured to use a yaml based whitelist we expect a file to be there
    // This behavior is different for other properties which should fall back to default values
    @Test
    public void testMissingDefaultFileForWhitelistThrowsConfigurationException()
    {
        AuditConfig config = givenLoadedDefaultConfig();

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getYamlWhitelist);
    }

    @Test
    public void testDefaultConfiguration()
    {
        Properties properties = getProperties("empty.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getYamlWhitelist()).isEmpty();
        assertThat(config.isPostLogging()).isFalse();
        assertThat(config.getBoundValueSuppressor()).isEqualTo(SuppressNothing.class.getName());
        assertThat(config.getWhitelistCacheValidity()).isEqualTo(DatabaseDescriptor.getRolesValidity());
        assertThat(config.getWhitelistCacheUpdateInterval()).isEqualTo(DatabaseDescriptor.getRolesUpdateInterval());
        assertThat(config.getWhitelistCacheMaxEntries()).isEqualTo(DatabaseDescriptor.getRolesCacheMaxEntries() * 10);
        assertThat(config.isSuppressPrepareStatements()).isEqualTo(true);
    }

    @Test
    public void testEmptyWhitelistIsStillValid()
    {
        Properties properties = getProperties("missing_all.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getYamlWhitelist()).isEmpty();
    }

    @Test
    public void testCustomConfiguration()
    {
        Properties properties = getProperties("mock_configuration.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getYamlWhitelist()).containsOnly("User1", "User2");
        assertThat(config.isPostLogging()).isTrue();
        assertThat(config.getBoundValueSuppressor()).isEqualTo("SuppressBlobs");
        assertThat(config.getWhitelistCacheValidity()).isEqualTo(42);
        assertThat(config.getWhitelistCacheUpdateInterval()).isEqualTo(41);
        assertThat(config.getWhitelistCacheMaxEntries()).isEqualTo(40);
        assertThat(config.isSuppressPrepareStatements()).isEqualTo(false);
    }

    @Test
    public void testPostLoggingDefault()
    {
        Properties properties = getProperties("empty.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.isPostLogging()).isFalse();
    }

    @Test
    public void testPostLoggingConfigured()
    {
        Properties properties = getProperties("mock_configuration.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.isPostLogging()).isTrue();
    }

    @Test
    public void testLoggerTypeWhenMissingDefaultFile()
    {
        AuditConfig config = givenLoadedDefaultConfig();

        assertThat(config.getLoggerBackendParameters().class_name).isEqualTo("com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger");
        assertThat(config.getLoggerBackendParameters().parameters).isEmpty();
    }

    @Test
    public void testLoggerParametersWhenLoadingCustomConfiguration()
    {
        Properties properties = getProperties("mock_log_format.yaml");
        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getLoggerBackendParameters().class_name).isEqualTo("com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger");
        assertThat(config.getLoggerBackendParameters().parameters).containsOnly(entry("log_dir", "/tmp"),
                                                                                entry("roll_cycle", "MINUTELY"),
                                                                                entry("max_log_size", "1000000"));
    }

    @Test
    public void testAuthorizerTypeWhenMissingDefaultFile()
    {
        AuditConfig config = givenLoadedDefaultConfig();

        assertThat(config.getWrappedAuthorizer()).isEqualTo("org.apache.cassandra.auth.CassandraAuthorizer");
    }

    @Test
    public void testCustomAuthorizerType()
    {
        Properties properties = getProperties("mock_configuration.yaml");
        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getWrappedAuthorizer()).isEqualTo("org.apache.cassandra.auth.AllowAllAuthorizer");
    }

    @Test
    public void testWhitelistCacheParametersCanBeSet()
    {
        Properties properties = getProperties("empty.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        config.setWhitelistCacheValidity(99);
        config.setWhitelistCacheUpdateInterval(88);
        config.setWhitelistCacheMaxEntries(77);

        assertThat(config.getWhitelistCacheValidity()).isEqualTo(99);
        assertThat(config.getWhitelistCacheUpdateInterval()).isEqualTo(88);
        assertThat(config.getWhitelistCacheMaxEntries()).isEqualTo(77);
    }

    private AuditConfig givenLoadedConfig(Properties properties)
    {
        AuditYamlConfigurationLoader loader = AuditYamlConfigurationLoader.withProperties(properties);
        return new AuditConfig(loader);
    }

    private AuditConfig givenLoadedDefaultConfig()
    {
        AuditYamlConfigurationLoader loader = AuditYamlConfigurationLoader.withSystemProperties();
        return new AuditConfig(loader);
    }

    private static Properties getProperties(String fileName)
    {
        URL url = TestAuditYamlConfigurationLoader.class.getResource("/" + fileName);
        Properties properties = new Properties();
        properties.put(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, url.getPath());

        return properties;
    }
}