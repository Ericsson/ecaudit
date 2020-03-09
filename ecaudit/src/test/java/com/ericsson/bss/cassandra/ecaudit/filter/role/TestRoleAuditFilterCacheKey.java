/*
 * Copyright 2020 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.filter.role;

import java.util.Collections;
import java.util.Set;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRoleAuditFilterCacheKey
{
    @Test
    public void testKeyValuesCanBeRead()
    {
        // Given
        String user = "user";
        IResource resource = DataResource.fromName("data/ks/tbl");
        Set<Permission> permissions = Collections.singleton(Permission.SELECT);
        // When
        RoleAuditFilterCacheKey key = new RoleAuditFilterCacheKey(user, resource, permissions);
        // Then
        assertThat(key.getUser()).isSameAs(user);
        assertThat(key.getResource()).isSameAs(resource);
        assertThat(key.getPermissions()).isSameAs(permissions);
    }

    @Test
    public void testEqualsContract()
    {
        EqualsVerifier.forClass(RoleAuditFilterCacheKey.class)
                      .usingGetClass()
                      .verify();
    }
}
