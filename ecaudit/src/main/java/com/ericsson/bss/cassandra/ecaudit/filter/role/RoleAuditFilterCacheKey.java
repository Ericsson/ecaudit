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

import java.util.Objects;
import java.util.Set;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

public class RoleAuditFilterCacheKey
{
    private final String user;
    private final IResource resource;
    private final Set<Permission> permissions;

    RoleAuditFilterCacheKey(String user, IResource resource, Set<Permission> permissions)
    {
        this.user = user;
        this.resource = resource;
        this.permissions = permissions;
    }

    public String getUser()
    {
        return user;
    }

    public IResource getResource()
    {
        return resource;
    }

    public Set<Permission> getPermissions()
    {
        return permissions;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        RoleAuditFilterCacheKey cacheKey = (RoleAuditFilterCacheKey) o;
        return Objects.equals(user, cacheKey.user) &&
               Objects.equals(resource, cacheKey.resource) &&
               Objects.equals(permissions, cacheKey.permissions);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(user, resource, permissions);
    }
}
