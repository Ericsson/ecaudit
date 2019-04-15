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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import org.apache.cassandra.exceptions.ConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestSlf4jAuditLoggerConfig
{
    @Test
    public void testSlf4jLoggerWhenConfigFileIsEmpty()
    {
        Map<String, String> options = Collections.emptyMap();

        Slf4jAuditLoggerConfig config = new Slf4jAuditLoggerConfig(options);

        assertThat(config.getLogFormat()).isEqualTo("client:'${CLIENT}'|user:'${USER}'{?|batchId:'${BATCH_ID}'?}|status:'${STATUS}'|operation:'${OPERATION}'");
        assertThat(config.getTimeFormatter()).isEmpty();
    }

    @Test
    public void testGetTimeFormatterWithFormatAndZoneConfigured()
    {
        Map<String, String> options = ImmutableMap.of("time_format", "yyyy-MM-dd HH:mm:ss.SSSZ",
                                                      "time_zone", "Europe/Stockholm");

        Slf4jAuditLoggerConfig auditConfig = new Slf4jAuditLoggerConfig(options);

        Optional<DateTimeFormatter> timeFormatter = auditConfig.getTimeFormatter();
        assertThat(timeFormatter).map(f -> f.format(Instant.EPOCH.plusMillis(42))).contains("1970-01-01 01:00:00.042+0100");
        assertThat(timeFormatter).map(DateTimeFormatter::getZone).contains(ZoneId.of("Europe/Stockholm"));
    }

    @Test
    public void testGetTimeFormatterWithoutZoneConfiguredGetSystemDefault()
    {
        Map<String, String> options = ImmutableMap.of("time_format", "yyyy");

        Slf4jAuditLoggerConfig auditConfig = new Slf4jAuditLoggerConfig(options);

        Optional<DateTimeFormatter> timeFormatter = auditConfig.getTimeFormatter();
        assertThat(timeFormatter).map(DateTimeFormatter::getZone).contains(ZoneId.systemDefault());
    }

    @Test
    public void testGetTimeFormatterWithoutFormatConfiguredIsEmpty()
    {
        Map<String, String> options = ImmutableMap.of("time_zone", "Europe/Stockholm");

        Slf4jAuditLoggerConfig auditConfig = new Slf4jAuditLoggerConfig(options);

        Optional<DateTimeFormatter> timeFormatter = auditConfig.getTimeFormatter();
        assertThat(timeFormatter).isEmpty();
    }

    @Test
    public void testGetTimeFormatterThrowsExceptionWhenFaultyTimePattern()
    {
        Map<String, String> options = ImmutableMap.of("time_format", "]NOK[",
                                                      "time_zone", "Europe/Stockholm");

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> new Slf4jAuditLoggerConfig(options))
        .withCauseInstanceOf(IllegalArgumentException.class)
        .withMessageContaining("Invalid SLF4J logger time format parameter")
        .withMessageContaining("]NOK[");
    }

    @Test
    public void testGetTimeFormatterThrowsExceptionWhenFaultyTimeZone()
    {
        Map<String, String> options = ImmutableMap.of("time_format", "yyyy-MM-dd HH:mm:ss.SSSZ",
                                                      "time_zone", "DoesNotExist");

        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> new Slf4jAuditLoggerConfig(options))
        .withCauseInstanceOf(DateTimeException.class)
        .withMessageContaining("Invalid SLF4J logger time zone parameter")
        .withMessageContaining("DoesNotExist");
    }
}
