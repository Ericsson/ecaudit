//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.logger;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;

/**
 * An interface for logging audit records.
 */
public interface AuditLogger {

    /**
     * Add an entry to the audit log.
     *
     * @param logEntry
     *            the entry to commit to the log
     */
    void log(AuditEntry logEntry);
}
