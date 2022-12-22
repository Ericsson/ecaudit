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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.cassandra.exceptions.ConfigurationException;

class Slf4jAuditLoggerConfig
{
    private static final String CONFIG_LOG_FORMAT = "log_format";
    private static final String CONFIG_TIME_FORMAT = "time_format";
    private static final String CONFIG_TIME_ZONE = "time_zone";
    private static final String CONFIG_ESCAPE_CHARACTERS = "escape_characters";

    private static final String DEFAULT_LOG_FORMAT = "client:'${CLIENT_IP}'|user:'${USER}'{?|batchId:'${BATCH_ID}'?}|status:'${STATUS}'|operation:'${OPERATION}'";

    private final String logFormat;
    private final DateTimeFormatter timeFormatter;
    private final Set<String> escapeCharacters;

    Slf4jAuditLoggerConfig(Map<String, String> parameters)
    {
        logFormat = resolveLogFormat(parameters);
        timeFormatter = resolveTimeFormatter(parameters);
        escapeCharacters = resolveEscapeCharacters(parameters);
    }

    private static String resolveLogFormat(Map<String, String> parameters) throws ConfigurationException
    {
        return Optional.ofNullable(parameters.get(CONFIG_LOG_FORMAT))
                       .orElse(DEFAULT_LOG_FORMAT);
    }

    private static DateTimeFormatter resolveTimeFormatter(Map<String, String> configuration) throws ConfigurationException
    {
        try
        {
            return Optional.ofNullable(configuration.get(CONFIG_TIME_FORMAT))
                           .map(DateTimeFormatter::ofPattern)
                           .map(formatter -> formatter.withZone(resolveZoneId(configuration)))
                           .orElse(null);
        }
        catch (IllegalArgumentException e)
        {
            throw new ConfigurationException("Invalid SLF4J logger time format parameter: " + configuration.get(CONFIG_TIME_FORMAT), e);
        }
    }

    private static ZoneId resolveZoneId(Map<String, String> parameters) throws ConfigurationException
    {
        try
        {
            return Optional.ofNullable(parameters.get(CONFIG_TIME_ZONE))
                           .map(ZoneId::of)
                           .orElse(ZoneId.systemDefault());
        }
        catch (DateTimeException e)
        {
            throw new ConfigurationException("Invalid SLF4J logger time zone parameter: " + parameters.get(CONFIG_TIME_ZONE), e);
        }
    }

    private static Set<String> resolveEscapeCharacters(Map<String, String> parameters)
    {
        String escape = parameters.get(CONFIG_ESCAPE_CHARACTERS); //We expect a comma-separated list
        if (escape == null || escape.isEmpty())
        {
            return Collections.emptySet();
        }
        Set<String> escapeChars = new HashSet<>();
        for (String escapeChar : escape.split(","))
        {
            String trimmed = escapeChar.trim();
            if (!trimmed.isEmpty())
            {
                escapeChars.add(trimmed);
            }
        }
        return escapeChars;
    }

    String getLogFormat()
    {
        return logFormat;
    }

    Optional<DateTimeFormatter> getTimeFormatter()
    {
        return Optional.ofNullable(timeFormatter);
    }

    Set<String> getEscapeCharacters()
    {
        return escapeCharacters;
    }
}
