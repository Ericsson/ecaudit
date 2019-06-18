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

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;
import com.ericsson.bss.cassandra.ecaudit.config.AuditYamlConfigurationLoader;
import com.ericsson.bss.cassandra.ecaudit.facade.Auditor;
import com.ericsson.bss.cassandra.ecaudit.facade.DefaultAuditor;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.DefaultAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.role.RoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.YamlAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yamlandrole.YamlAndRoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.AuditObfuscator;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.PasswordObfuscator;
import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.ParameterizedClass;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditAdapterFactory
{
    @BeforeClass
    public static void beforeAll()
    {
        ClientInitializer.beforeClass();
    }

    @Before
    public void before()
    {
        System.setProperty(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, getPathToTestResourceFile());
    }

    @After
    public void after()
    {
        System.clearProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME);
        System.clearProperty(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE);
    }

    @AfterClass
    public static void afterAll()
    {
        ClientInitializer.afterClass();
    }

    @Test
    public void testLoadDefaultWithoutErrorHasExpectedType() throws Exception
    {
        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(loggerIn(defaultAuditor)).isInstanceOf(Slf4jAuditLogger.class);
        assertThat(filterIn(defaultAuditor)).isInstanceOf(RoleAuditFilter.class);
        assertThat(obfuscatorIn(defaultAuditor)).isInstanceOf(PasswordObfuscator.class);
    }

    @Test
    public void testLoadYamlFilterWithoutErrorHasExpectedType() throws Exception
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_YAML);

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(filterIn(defaultAuditor)).isInstanceOf(YamlAuditFilter.class);
    }

    @Test
    public void testLoadRoleFilterWithoutErrorHasExpectedType() throws Exception
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_ROLE);

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(filterIn(defaultAuditor)).isInstanceOf(RoleAuditFilter.class);
    }

    @Test
    public void testLoadYamlAndRoleFilterWithoutErrorHasExpectedType() throws Exception
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_YAML_AND_ROLE);

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(filterIn(defaultAuditor)).isInstanceOf(YamlAndRoleAuditFilter.class);
    }

    @Test
    public void testLoadNoFilterWithoutErrorHasExpectedType() throws Exception
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_NONE);

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(filterIn(defaultAuditor)).isInstanceOf(DefaultAuditFilter.class);
    }

    @Test
    public void testLoadUnknownFilterFails()
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, "UNKNOWN");

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(AuditAdapterFactory::createAuditAdapter)
        .withMessageContaining("Unrecognized audit filter type")
        .withMessageContaining("UNKNOWN");
    }

    @Test
    public void testLoadSlf4jLogger() throws Exception
    {
        AuditConfig auditConfig = givenAuditConfig("com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger", Collections.emptyMap());

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter(auditConfig);

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(loggerIn(defaultAuditor)).isInstanceOf(Slf4jAuditLogger.class);
    }

    @Test
    public void testLoadChronicleLogger() throws Exception
    {
        File tempDir = Files.createTempDir();
        tempDir.deleteOnExit();
        AuditConfig auditConfig = givenAuditConfig("com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger", ImmutableMap.of("log_dir", tempDir.getPath()));

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter(auditConfig);

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(loggerIn(defaultAuditor)).isInstanceOf(ChronicleAuditLogger.class);
    }

    @Test
    public void testLoadChronicleLoggerWithoutMandatoryParameters()
    {
        AuditConfig auditConfig = givenAuditConfig("com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger", Collections.emptyMap());

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> AuditAdapterFactory.createAuditAdapter(auditConfig))
        .withMessageContaining("Audit logger backend failed at initialization")
        .withMessageContaining("log_dir");
    }

    @Test
    public void testLoadInvalidLogger()
    {
        AuditConfig auditConfig = givenAuditConfig("no.working.InvalidAuditLogger", Collections.emptyMap());

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> AuditAdapterFactory.createAuditAdapter(auditConfig))
        .withMessageContaining("Failed to initialize audit logger backend")
        .withMessageContaining("InvalidAuditLogger");
    }

    @Test
    public void testLogTimingStrategy() throws Exception
    {
        // Given
        AuditConfig defaultConfig = givenAuditConfig("com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger", Collections.emptyMap());
        AuditConfig postLoggingConfig = givenAuditConfig("com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger", Collections.emptyMap());
        when(postLoggingConfig.isPostLogging()).thenReturn(true);
        // When
        AuditAdapter adapterWithDefaultConfig = AuditAdapterFactory.createAuditAdapter(defaultConfig);
        AuditAdapter adapterWithPostLogging = AuditAdapterFactory.createAuditAdapter(postLoggingConfig);
        // Then
        assertThat(logTimingStrategyIn(adapterWithDefaultConfig)).isSameAs(LogTimingStrategy.PRE_LOGGING_STRATEGY);
        assertThat(logTimingStrategyIn(adapterWithPostLogging)).isSameAs(LogTimingStrategy.POST_LOGGING_STRATEGY);
    }

    private static String getPathToTestResourceFile()
    {
        URL url = TestAuditAdapterFactory.class.getResource("/mock_configuration.yaml");
        return url.getPath();
    }

    private static AuditConfig givenAuditConfig(String s, Map<String, String> parameters)
    {
        ParameterizedClass parameterizedClass = new ParameterizedClass(s, parameters);
        AuditConfig auditConfig = mock(AuditConfig.class);
        when(auditConfig.getLoggerBackendParameters()).thenReturn(parameterizedClass);
        return auditConfig;
    }

    private static Auditor auditorIn(AuditAdapter adapter) throws Exception
    {
        Field field = AuditAdapter.class.getDeclaredField("auditor");
        field.setAccessible(true);
        return (Auditor) field.get(adapter);
    }

    private static AuditLogger loggerIn(DefaultAuditor auditor) throws Exception
    {
        Field field = DefaultAuditor.class.getDeclaredField("logger");
        field.setAccessible(true);
        return (AuditLogger) field.get(auditor);
    }

    private static AuditFilter filterIn(DefaultAuditor auditor) throws Exception
    {
        Field field = DefaultAuditor.class.getDeclaredField("filter");
        field.setAccessible(true);
        return (AuditFilter) field.get(auditor);
    }

    private static AuditObfuscator obfuscatorIn(DefaultAuditor auditor) throws Exception
    {
        Field field = DefaultAuditor.class.getDeclaredField("obfuscator");
        field.setAccessible(true);
        return (AuditObfuscator) field.get(auditor);
    }

    private static LogTimingStrategy logTimingStrategyIn(AuditAdapter auditAdapter) throws Exception
    {
        Field field = AuditAdapter.class.getDeclaredField("logTimingStrategy");
        field.setAccessible(true);
        return (LogTimingStrategy) field.get(auditAdapter);
    }
}
