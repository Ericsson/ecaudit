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

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests the {@link AuditEntry} class.
 */
public class TestAuditEntry
{
    private static final InetAddress CLIENT = mock(InetAddress.class);
    private static final Set<Permission> PERMISSIONS = Collections.emptySet();
    private static final IResource RESOURCE = mock(IResource.class);
    private static final AuditOperation OPERATION = mock(AuditOperation.class);
    private static final String USER = "user1";
    private static final UUID BATCH = mock(UUID.class);
    private static final Status STATUS = mock(Status.class);
    private static final long TIMESTAMP = 42L;

    @Test
    public void testThatBuilderBasedOnPreservesOriginalValues()
    {
        // Given
        AuditEntry auditEntry = AuditEntry.newBuilder()
                                     .client(CLIENT)
                                     .permissions(PERMISSIONS)
                                     .resource(RESOURCE)
                                     .operation(OPERATION)
                                     .user(USER)
                                     .batch(BATCH)
                                     .status(STATUS)
                                     .timestamp(TIMESTAMP)
                                     .build();
        // When
        AuditEntry newEntry = AuditEntry.newBuilder().basedOn(auditEntry).build();
        // Then
        assertThat(auditEntry.getClientAddress()).isSameAs(newEntry.getClientAddress()).isSameAs(CLIENT);
        assertThat(auditEntry.getPermissions()).isSameAs(newEntry.getPermissions()).isSameAs(PERMISSIONS);
        assertThat(auditEntry.getResource()).isSameAs(newEntry.getResource()).isSameAs(RESOURCE);
        assertThat(auditEntry.getOperation()).isSameAs(newEntry.getOperation()).isSameAs(OPERATION);
        assertThat(auditEntry.getUser()).isSameAs(newEntry.getUser()).isSameAs(USER);
        assertThat(auditEntry.getBatchId()).isEqualTo(newEntry.getBatchId()).contains(BATCH);
        assertThat(auditEntry.getStatus()).isSameAs(newEntry.getStatus()).isSameAs(STATUS);
        assertThat(auditEntry.getTimestamp()).isSameAs(newEntry.getTimestamp()).isSameAs(TIMESTAMP);
    }
}
