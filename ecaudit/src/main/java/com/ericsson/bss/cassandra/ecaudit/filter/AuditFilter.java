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
 * An interface for filtering audit records.
 */
public interface AuditFilter {

    /**
     * Return a boolean indicating whether the given log entry is to be exempt from audit logging (i.e. it is whitelisted).
     *
     * @param logEntry
     *            the log entry to check if filtered
     * @return true if the log entry is exempt from audit
     */
    boolean isWhitelisted(AuditEntry logEntry);

    /**
     * Setup is called once upon system startup to initialize the AuditFilter.
     *
     * For example, use this method to create any required keyspaces/tables.
     */
    void setup();

    boolean shouldLogPrepareStatements();
}
