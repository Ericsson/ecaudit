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
package com.ericsson.bss.cassandra.ecaudit.entry;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link AuditEntry} class.
 */
public class TestAuditEntry
{
    @Test
    public void testThatBuilderBasedOnPreservesOriginalTimestamp()
    {
        // Given
        AuditEntry originalEntry = newAuditEntryBuilderWithTimestamp(42).build();
        // When
        AuditEntry newEntry = AuditEntry.newBuilder().basedOn(originalEntry).build();
        // Then
        assertThat(newEntry.getTimestamp()).isEqualTo(42);
    }

    public static final AuditEntry.Builder newAuditEntryBuilderWithTimestamp(long timestamp)
    {
        return new AuditEntry.Builder(timestamp);
    }
}
