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
package com.ericsson.bss.cassandra.ecaudit.filter.yaml;

import java.util.Collections;
import java.util.List;

/**
 * Data class for configuration
 */
public final class AuditConfig
{
    public static final String DEFAULT_FORMAT = "client:'${CLIENT}'|user:'${USER}'{?|batchId:'${BATCH_ID}'?}|status:'${STATUS}'|operation:'${OPERATION}'";

    private List<String> whitelist;
    private String logFormat;

    /**
     * Get the user whitelist in this configuration
     *
     * @return the list of whitelisted users
     */
    public List<String> getWhitelist()
    {
        return whitelist != null ? Collections.unmodifiableList(whitelist) : Collections.emptyList();
    }

    /**
     * Set the whitelist in this configuration
     *
     * @param whitelist a list of whitelisted users
     */
    public void setWhitelist(List<String> whitelist)
    {
        this.whitelist = whitelist;
    }

    /**
     * @return the log format in this configuration
     */
    public String getLogFormat()
    {
        return logFormat != null ? logFormat : DEFAULT_FORMAT;
    }

    /**
     * Set the log format in this configuration
     *
     * @param logFormat the log format string
     */
    public void setLogFormat(String logFormat)
    {
        this.logFormat = logFormat;
    }
}
