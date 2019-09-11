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
package com.ericsson.bss.cassandra.ecaudit.eclog.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.eclog.ToolOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the {@link EcLogYamlConfigLoader} class.
 */
public class TestEcLogYamlConfigLoader
{
    private static final Path LOG_DIR = Paths.get("/log");
    private static final Path CUSTOM_FILE = Paths.get("/custom/config.yaml");
    private static final Path WORKING_DIR = Paths.get(System.getProperty("user.dir"));

    private static final String EXPECTED_FILE_NAME = "ecLog.yaml";
    private static final Path CONFIG_FILE_DIR = Paths.get("src/test/resources/");
    private static final Path CONFIG_FILE = CONFIG_FILE_DIR.resolve(EXPECTED_FILE_NAME);

    @Test
    public void testLoadWithoutConfigFileGivesDefaultValues()
    {
        ToolOptions options = ToolOptions.builder().withPath(LOG_DIR).build();
        EcLogYamlConfig config = EcLogYamlConfigLoader.load(options);

        assertDefaultConfig(config);
    }

    @Test
    public void testLoadWithConfigFromFileInLogDirPath()
    {
        ToolOptions options = ToolOptions.builder().withPath(CONFIG_FILE_DIR).build();
        EcLogYamlConfig config = EcLogYamlConfigLoader.load(options);

        assertCustomConfig(config);
    }

    @Test
    public void testLoadWithConfigFileProvidedOnCommandLine()
    {
        ToolOptions options = ToolOptions.builder().withPath(LOG_DIR).withConfig(CONFIG_FILE).build();
        EcLogYamlConfig config = EcLogYamlConfigLoader.load(options);

        assertCustomConfig(config);
    }

    @Test
    public void testConfigFileLocations()
    {
        ToolOptions options = ToolOptions.builder().withPath(LOG_DIR).build();
        List<Path> locations = EcLogYamlConfigLoader.getConfigFileLocations(options);

        assertThat(locations).containsExactly(WORKING_DIR.resolve(EXPECTED_FILE_NAME),
                                              LOG_DIR.resolve(EXPECTED_FILE_NAME));
    }

    @Test
    public void testConfigFileLocationsIncludingCommandLineConfig()
    {
        ToolOptions options = ToolOptions.builder().withPath(LOG_DIR).withConfig(CUSTOM_FILE).build();
        List<Path> locations = EcLogYamlConfigLoader.getConfigFileLocations(options);

        assertThat(locations).containsExactly(CUSTOM_FILE,
                                              WORKING_DIR.resolve(EXPECTED_FILE_NAME),
                                              LOG_DIR.resolve(EXPECTED_FILE_NAME));
    }

    @Test
    public void testLoadEmptyConfigFromUrlGivesDefaultConfig()
    {
        Path path = Paths.get("src/test/resources/empty.yaml");
        EcLogYamlConfig config = EcLogYamlConfigLoader.loadConfigFromUrl(path);

        assertDefaultConfig(config);
    }

    @Test
    public void testLoadConfigFromInvalidUrl()
    {
        Path path = Paths.get("invalid_path");
        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> EcLogYamlConfigLoader.loadConfigFromUrl(path))
        .withMessage("Invalid configuration file: invalid_path");
    }

    @Test
    public void testLoadConfigFromFileWithInvalidContent()
    {
        Path path = Paths.get("src/test/resources/invalid.yaml");
        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> EcLogYamlConfigLoader.loadConfigFromUrl(path))
        .withMessage("Invalid configuration file: src/test/resources/invalid.yaml");
    }

    private void assertDefaultConfig(EcLogYamlConfig config)
    {
        assertThat(config).isNotNull();
        assertThat(config.getLogFormat()).isEqualTo(EcLogYamlConfig.DEFAULT_FORMAT);
        assertThat(config.getTimeFormatter()).isEmpty();
    }

    private void assertCustomConfig(EcLogYamlConfig config)
    {
        assertThat(config).isNotNull();
        assertThat(config.getLogFormat()).isEqualTo("${TIMESTAMP} -> Client=${CLIENT_IP}, Status=${STATUS}, Operation=${OPERATION}");
        assertThat(config.getTimeFormatter()).map(formatter -> formatter.format(Instant.ofEpochSecond(42)))
                                             .contains("1970-01-01 00:00:42 UTC");
    }
}
