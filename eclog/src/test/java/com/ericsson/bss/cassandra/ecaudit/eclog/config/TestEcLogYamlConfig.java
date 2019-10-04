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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the {@link EcLogYamlConfig} class.
 */
public class TestEcLogYamlConfig
{
    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss zZ";

    @Test
    public void testDefaultLogFormat()
    {
        EcLogYamlConfig defaultConfig = new EcLogYamlConfig();

        assertThat(defaultConfig.getLogFormat()).isEqualTo("{?${TIMESTAMP}?}{?|${CLIENT_IP}?}{?:${CLIENT_PORT}?}{?|${COORDINATOR_IP}?}{?|${USER}?}{?|${STATUS}?}{?|${BATCH_ID}?}{?|${OPERATION}?}");
    }

    @Test
    public void testCustomLogFormat()
    {
        EcLogYamlConfig config = new EcLogYamlConfig();
        config.log_format = "Custom";

        assertThat(config.getLogFormat()).isEqualTo("Custom");
    }

    @Test
    public void testDefaultTimeFormatter()
    {
        EcLogYamlConfig defaultConfig = new EcLogYamlConfig();

        assertThat(defaultConfig.getTimeFormatter()).isEmpty();
    }

    @Test
    public void testInvalidTimeFormat()
    {
        EcLogYamlConfig config = new EcLogYamlConfig();
        config.time_format = "INVALID1";

        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(config::getTimeFormatter)
        .withMessage("Invalid time format parameter: INVALID1");
    }

    @Test
    public void testInvalidTimeZone()
    {
        EcLogYamlConfig config = new EcLogYamlConfig();
        config.time_format = TIME_FORMAT;
        config.time_zone = "INVALID2";

        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(config::getTimeFormatter)
        .withMessage("Invalid time zone parameter: INVALID2");
    }


    @Test
    public void testCustomTimeFormatterWithDefaultZone()
    {
        EcLogYamlConfig config = new EcLogYamlConfig();
        config.time_format = TIME_FORMAT;

        Optional<DateTimeFormatter> timeFormatter = config.getTimeFormatter();
        assertThat(timeFormatter).map(DateTimeFormatter::getZone).contains(ZoneId.systemDefault());
    }

    @Test
    public void testCustomTimeFormatterWithCustomZone()
    {
        EcLogYamlConfig config = new EcLogYamlConfig();
        config.time_format = TIME_FORMAT;
        config.time_zone = "Europe/Stockholm";

        Optional<DateTimeFormatter> timeFormatter = config.getTimeFormatter();
        assertThat(timeFormatter).map(DateTimeFormatter::getZone).contains(ZoneId.of(config.time_zone));
        assertThat(timeFormatter).map(formatter -> formatter.format(Instant.ofEpochSecond(42))).contains("1970-01-01 01:00:42 CET+0100");
    }
}
