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
package com.ericsson.bss.cassandra.ecaudit.logger;

import java.util.Collections;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.junit.Ignore;
import org.junit.Test;

import net.openhft.chronicle.queue.RollCycles;
import org.apache.cassandra.exceptions.ConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestChronicleAuditLoggerConfig
{
    @Test
    public void testMissingLogPath()
    {
        Map<String, String> options = Collections.emptyMap();

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> new ChronicleAuditLoggerConfig(options))
        .withMessageContaining("log_dir");
    }

    @Ignore
    @Test
    public void testInvalidLogPath()
    {
        Map<String, String> options = ImmutableMap.of("log_dir", "/is there no / way to craete an invalid path? :-(");

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> new ChronicleAuditLoggerConfig(options))
        .withMessageContaining("Invalid")
        .withMessageContaining("log_dir");
    }

    @Test
    public void testValidLogPath()
    {
        Map<String, String> options = ImmutableMap.of("log_dir", "/tmp");

        ChronicleAuditLoggerConfig config = new ChronicleAuditLoggerConfig(options);

        assertThat(config.getLogPath().toString()).isEqualTo("/tmp");
        assertThat(config.getRollCycle()).isEqualTo(RollCycles.HOURLY);
    }

    @Test
    public void testInvalidRollCycle()
    {
        Map<String, String> options = ImmutableMap.of("log_dir", "/tmp",
                                                      "roll_cycle", "HEPP");

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> new ChronicleAuditLoggerConfig(options))
        .withMessageContaining("Invalid chronicle logger roll cycle")
        .withMessageContaining("HEPP");
    }

    @Test
    public void testValidRollCycle()
    {
        Map<String, String> options = ImmutableMap.of("log_dir", "/tmp",
                                                      "roll_cycle", "MINUTELY");

        ChronicleAuditLoggerConfig config = new ChronicleAuditLoggerConfig(options);

        assertThat(config.getLogPath().toString()).isEqualTo("/tmp");
        assertThat(config.getRollCycle()).isEqualTo(RollCycles.MINUTELY);
    }

    @Test
    public void testInvalidMaxLogSize()
    {
        Map<String, String> options = ImmutableMap.of("log_dir", "/tmp",
                                                      "max_log_size", "0");

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> new ChronicleAuditLoggerConfig(options))
        .withMessageContaining("Invalid chronicle logger max log size")
        .withMessageContaining("0");
    }

    @Test
    public void testValidMaxLogSize()
    {
        Map<String, String> options = ImmutableMap.of("log_dir", "/tmp",
                                                      "max_log_size", "1024");

        ChronicleAuditLoggerConfig config = new ChronicleAuditLoggerConfig(options);

        assertThat(config.getLogPath().toString()).isEqualTo("/tmp");
        assertThat(config.getMaxLogSize()).isEqualTo(1024L);
    }
}
