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

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.facade.Auditor;
import com.ericsson.bss.cassandra.ecaudit.facade.DefaultAuditor;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.DefaultAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.role.RoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.config.AuditYamlConfig;
import com.ericsson.bss.cassandra.ecaudit.config.AuditYamlConfigurationLoader;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.YamlAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.filter.yamlandrole.YamlAndRoleAuditFilter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.AuditObfuscator;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.PasswordObfuscator;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.exceptions.ConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestAuditAdapterFactory
{
    @BeforeClass
    public static void beforeAll()
    {
        Config.setClientMode(true);
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
        Config.setClientMode(false);
    }

    @Test
    public void testLoadDefaultWithoutErrorHasExpectedTypes() throws Exception
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
    public void testLoadYamlWithoutErrorHasExpectedTypes() throws Exception
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_YAML);

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(loggerIn(defaultAuditor)).isInstanceOf(Slf4jAuditLogger.class);
        assertThat(filterIn(defaultAuditor)).isInstanceOf(YamlAuditFilter.class);
        assertThat(obfuscatorIn(defaultAuditor)).isInstanceOf(PasswordObfuscator.class);
    }

    @Test
    public void testLoadRoleWithoutErrorHasExpectedTypes() throws Exception
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_ROLE);

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(loggerIn(defaultAuditor)).isInstanceOf(Slf4jAuditLogger.class);
        assertThat(filterIn(defaultAuditor)).isInstanceOf(RoleAuditFilter.class);
        assertThat(obfuscatorIn(defaultAuditor)).isInstanceOf(PasswordObfuscator.class);
    }

    @Test
    public void testLoadYamlAndRoleWithoutErrorHasExpectedTypes() throws Exception
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_YAML_AND_ROLE);

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(loggerIn(defaultAuditor)).isInstanceOf(Slf4jAuditLogger.class);
        assertThat(filterIn(defaultAuditor)).isInstanceOf(YamlAndRoleAuditFilter.class);
        assertThat(obfuscatorIn(defaultAuditor)).isInstanceOf(PasswordObfuscator.class);
    }

    @Test
    public void testLoadNoneWithoutErrorHasExpectedTypes() throws Exception
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_NONE);

        AuditAdapter adapter = AuditAdapterFactory.createAuditAdapter();

        Auditor auditor = auditorIn(adapter);
        assertThat(auditor).isInstanceOf(DefaultAuditor.class);

        DefaultAuditor defaultAuditor = (DefaultAuditor) auditor;
        assertThat(loggerIn(defaultAuditor)).isInstanceOf(Slf4jAuditLogger.class);
        assertThat(filterIn(defaultAuditor)).isInstanceOf(DefaultAuditFilter.class);
        assertThat(obfuscatorIn(defaultAuditor)).isInstanceOf(PasswordObfuscator.class);
    }

    @Test
    public void testLoadUnknownFails()
    {
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, "UNKNOWN");

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(AuditAdapterFactory::createAuditAdapter);
    }

    private static String getPathToTestResourceFile()
    {
        URL url = TestAuditAdapterFactory.class.getResource("/mock_configuration.yaml");
        return url.getPath();
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
}
