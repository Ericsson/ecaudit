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

import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDefaultAuditFilter
{
    @Test
    public void testSetupDoNotFail()
    {
        new DefaultAuditFilter().setup();
    }

    @Test
    public void testWhitelistNotFilteredWithDefaultFilter()
    {
        DefaultAuditFilter filter = new DefaultAuditFilter();

        assertThat(filter.isFiltered(toLogEntry("user1"))).isFalse();
        assertThat(filter.isFiltered(toLogEntry("user2"))).isFalse();
    }

    private static AuditEntry toLogEntry(String user)
    {
        return AuditEntry.newBuilder()
                         .user(user)
                         .build();
    }
}
