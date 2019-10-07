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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ericsson.bss.cassandra.ecaudit.entry.obfuscator.ShowAllObfuscator;
import com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger;
import org.apache.cassandra.config.ParameterizedClass;

/**
 * Data class for configuration
 */
public final class AuditYamlConfig
{
    private static final List<String> DEFAULT_WHITELIST = Collections.emptyList();
    private static final ParameterizedClass DEFAULT_LOGGER_BACKEND = new ParameterizedClass(Slf4jAuditLogger.class.getCanonicalName(), Collections.emptyMap());
    private static final String DEFAULT_WRAPPED_AUTHORIZER = "org.apache.cassandra.auth.CassandraAuthorizer";
    private static final String DEFAULT_COLUMN_OBFUSCATOR = ShowAllObfuscator.class.getName();

    private boolean fromFile = true;

    // Configuration parameters
    // Has to be public for SnakeYaml to inject values
    public List<String> whitelist;
    public ParameterizedClass logger_backend;
    public LoggerTiming log_timing_strategy;
    public String wrapped_authorizer;
    public String column_obfuscator;

    static AuditYamlConfig createWithoutFile()
    {
        AuditYamlConfig auditYamlConfig = new AuditYamlConfig();
        auditYamlConfig.fromFile = false;
        return auditYamlConfig;
    }

    boolean isFromFile()
    {
        return fromFile;
    }

    /**
     * Get the user whitelist in this configuration
     *
     * @return the list of whitelisted users
     */
    List<String> getWhitelist()
    {
        return whitelist != null ? Collections.unmodifiableList(whitelist) : DEFAULT_WHITELIST;
    }

    ParameterizedClass getLoggerBackendParameters()
    {
        // We hand out a deep copy since:
        // - ParameterizedClass is mutable
        // - The original map of options may contain a mix of Integers and Strings
        //   Converting it all to Strings makes life easier for plug-ins when parsing options
        return logger_backend != null ? deepToStringCopy(logger_backend) : deepToStringCopy(DEFAULT_LOGGER_BACKEND);
    }

    private ParameterizedClass deepToStringCopy(ParameterizedClass original)
    {
        Map<String, String> parameters;
        if (original.parameters == null)
        {
            parameters = Collections.emptyMap();
        }
        else
        {
            parameters = original.parameters
                         .entrySet()
                         .stream()
                         .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        }

        return new ParameterizedClass(original.class_name, parameters);
    }

    boolean isPostLogging()
    {
        return log_timing_strategy == LoggerTiming.post_logging;
    }

    String getWrappedAuthorizer()
    {
        return wrapped_authorizer != null ? wrapped_authorizer : DEFAULT_WRAPPED_AUTHORIZER;
    }

    String getColumnObfuscator()
    {
        return column_obfuscator != null ? column_obfuscator : DEFAULT_COLUMN_OBFUSCATOR;
    }
}
