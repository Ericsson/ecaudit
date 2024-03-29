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
package com.ericsson.bss.cassandra.ecaudit.filter;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;

/**
 * Implements an {@link AuditFilter} that does not filters any {@link AuditEntry} instances
 */
public class DefaultAuditFilter implements AuditFilter {

    @Override
    public boolean isWhitelisted(AuditEntry logEntry) {
        return false;
    }

    @Override
    public void setup()
    {
        // Intentionally left empty
    }

    @Override
    public boolean shouldLogPrepareStatements()
    {
        return true;
    }
}
