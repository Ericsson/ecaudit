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
package com.ericsson.bss.cassandra.ecaudit.facade;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.AuditObfuscator;

/**
 * Default implementation of {@link Auditor} which will do following task required to auditing:
 *
 * - Filtering populated {@link AuditEntry} instance using {@link AuditFilter}
 * - Obfuscation on filtered using {@link AuditObfuscator}
 * - Write log entry using {@link AuditLogger}
 */
public class DefaultAuditor implements Auditor
{
    private AuditLogger logger;
    private AuditFilter filter;
    private AuditObfuscator obfuscator;

    public DefaultAuditor(AuditLogger logger, AuditFilter filter, AuditObfuscator obfuscator)
    {
        this.logger = logger;
        this.filter = filter;
        this.obfuscator = obfuscator;
    }

    @Override
    public void audit(AuditEntry logEntry)
    {
        if (!filter.isFiltered(logEntry))
        {
            AuditEntry obfuscatedEntry = obfuscator.obfuscate(logEntry);
            logger.log(obfuscatedEntry);
        }
    }

}
