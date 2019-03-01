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
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Properties;

import org.junit.Test;

import org.apache.cassandra.exceptions.ConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestAuditYamlConfigurationLoader
{
    @Test
    public void testEmptyPropertyThrowsConfigurationException()
    {
        Properties properties = new Properties();
        properties.put(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, "");

        AuditConfig config = givenLoadedConfig(properties);

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getYamlWhitelist);
        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getLogFormat);
        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getTimeFormatter);
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
        .isThrownBy(config::getLogFormat);
        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getTimeFormatter);
    }

    // If ecAudit is configured to use a yaml based whitelist we expect a file to be there
    // This behavior is different for other properties which may fall back to default values
    @Test
    public void testMissingDefaultFileForWhitelistThrowsConfigurationException()
    {
        AuditConfig config = givenLoadedDefaultConfig();

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(config::getYamlWhitelist);
    }

    @Test
    public void testMissingWhitelistIsDefault()
    {
        Properties properties = getProperties("empty.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getYamlWhitelist()).isEmpty();
    }

    @Test
    public void testEmptyWhitelistIsStillValid()
    {
        Properties properties = getProperties("missing_all.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getYamlWhitelist()).isEmpty();
    }

    @Test
    public void testLoadWhitelistAllPresent()
    {
        Properties properties = getProperties("mock_configuration.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getYamlWhitelist()).containsOnly("User1", "User2");
    }

    @Test
    public void testSLF4JconfigWhenMissingDefaultFile()
    {
        AuditConfig config = givenLoadedDefaultConfig();

        assertThat(config.getLogFormat()).isEqualTo("client:'${CLIENT}'|user:'${USER}'{?|batchId:'${BATCH_ID}'?}|status:'${STATUS}'|operation:'${OPERATION}'");
        assertThat(config.getTimeFormatter()).isEmpty();
    }

    @Test
    public void testSLF4JconfigWhenConfigFileIsEmpty()
    {
        Properties properties = getProperties("empty.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getLogFormat()).isEqualTo("client:'${CLIENT}'|user:'${USER}'{?|batchId:'${BATCH_ID}'?}|status:'${STATUS}'|operation:'${OPERATION}'");
        assertThat(config.getTimeFormatter()).isEmpty();
    }

    @Test
    public void testSLF4JconfigWhenLoadingCustomConfiguration()
    {
        Properties properties = getProperties("mock_log_format.yaml");

        AuditConfig config = givenLoadedConfig(properties);

        assertThat(config.getLogFormat()).isEqualTo("user:{USER}, client:{CLIENT}");
    }

    @Test
    public void testGetTimeFormatterWithFormatAndZoneConfigured()
    {
        AuditConfig auditConfig = givenMockedTimeConfig("yyyy-MM-dd HH:mm:ss.SSSZ", "Europe/Stockholm");
        Optional<DateTimeFormatter> timeFormatter = auditConfig.getTimeFormatter();
        assertThat(timeFormatter).map(f -> f.format(Instant.EPOCH.plusMillis(42))).contains("1970-01-01 01:00:00.042+0100");
        assertThat(timeFormatter).map(DateTimeFormatter::getZone).contains(ZoneId.of("Europe/Stockholm"));
    }

    @Test
    public void testGetTimeFormatterWithoutZoneConfiguredGetSystemDefault()
    {
        AuditConfig auditConfig = givenMockedTimeConfig("yyyy", null);
        Optional<DateTimeFormatter> timeFormatter = auditConfig.getTimeFormatter();
        assertThat(timeFormatter).map(DateTimeFormatter::getZone).contains(ZoneId.systemDefault());
    }

    @Test
    public void testGetTimeFormatterWithoutFormatConfiguredIsEmpty()
    {
        AuditConfig auditConfig = givenMockedTimeConfig(null, "Europe/Stockholm");
        Optional<DateTimeFormatter> timeFormatter = auditConfig.getTimeFormatter();
        assertThat(timeFormatter).isEmpty();
    }

    @Test
    public void testGetTimeFormatterThrowsExceptionWhenFaultyTimePattern()
    {
        AuditConfig auditConfig = givenMockedTimeConfig("]NOK[", "Europe/Stockholm");

        assertThatThrownBy(auditConfig::getTimeFormatter).isInstanceOf(ConfigurationException.class)
                                                         .hasCauseInstanceOf(IllegalArgumentException.class)
                                                         .hasMessage("Invalid time format configuration.");
    }

    @Test
    public void testGetTimeFormatterThrowsExceptionWhenFaultyTimeZone()
    {
        AuditConfig auditConfig = givenMockedTimeConfig("yyyy-MM-dd HH:mm:ss.SSSZ", "DoesNotExist");

        assertThatThrownBy(auditConfig::getTimeFormatter).isInstanceOf(ConfigurationException.class)
                                                         .hasCauseInstanceOf(DateTimeException.class)
                                                         .hasMessage("Invalid time zone configuration.");
    }

    private AuditConfig givenMockedTimeConfig(String timeFormat, String timeZone)
    {
        AuditYamlSlf4jConfig slf4jMock = mock(AuditYamlSlf4jConfig.class);
        if (timeFormat != null)
        {
            when(slf4jMock.getTimeFormat()).thenReturn(timeFormat);
        }
        if (timeZone != null)
        {
            when(slf4jMock.getTimeZone()).thenReturn(timeZone);
        }

        AuditYamlConfig configMock = mock(AuditYamlConfig.class);
        when(configMock.getSlf4j()).thenReturn(slf4jMock);

        AuditYamlConfigurationLoader loaderMock = mock(AuditYamlConfigurationLoader.class);
        when(loaderMock.loadConfig()).thenReturn(configMock);

        return new AuditConfig(loaderMock);
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