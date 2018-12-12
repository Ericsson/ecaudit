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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.ericsson.bss.cassandra.ecaudit.auth.cache.AuthCache;
import com.ericsson.bss.cassandra.ecaudit.auth.cache.DescriptorBridge;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.DatabaseDescriptor;

public class AuditWhitelistCache extends AuthCache<RoleResource, Map<IResource, Set<Permission>>>
{
    private AuditWhitelistCache()
    {
        this(WhitelistDataAccess.getInstance());
    }

    private AuditWhitelistCache(WhitelistDataAccess whitelistDataAccess)
    {
        super("AuditWhitelistCache",
              DatabaseDescriptor::setRolesValidity,
              DatabaseDescriptor::getRolesValidity,
              DatabaseDescriptor::setRolesUpdateInterval,
              DatabaseDescriptor::getRolesUpdateInterval,
              DescriptorBridge::setRolesCacheMaxEntries,
              DatabaseDescriptor::getRolesCacheMaxEntries,
              whitelistDataAccess::getWhitelist,
              () -> true);
    }

    public static AuditWhitelistCache getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder
    {
        private static final AuditWhitelistCache INSTANCE = new AuditWhitelistCache();
    }

    /**
     * Get all whitelisted operation/resource combinations associated with the supplied role. The returned whitelist may be cached if
     * roles_validity_in_ms has a value greater than zero.
     *
     * @param role the Role
     * @return map of all whitelisted operation/resource combinations associated with the role
     */
    public Map<IResource, Set<Permission>> getWhitelist(RoleResource role)
    {
        try
        {
            return get(role);
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e);
        }
    }
}
