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
package com.ericsson.bss.cassandra.ecaudit.filter.role;

import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;

import com.ericsson.bss.cassandra.ecaudit.auth.AuditWhitelistCache;
import com.ericsson.bss.cassandra.ecaudit.auth.AuditWhitelistManager;
import com.ericsson.bss.cassandra.ecaudit.auth.WhitelistDataAccess;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.auth.Roles;

/**
 * A role based white-list filter that exempts users based on custom options on roles in Cassandra.
 */
public class RoleAuditFilter implements AuditFilter
{
    private final AuditWhitelistCache whitelistCache;
    private final WhitelistDataAccess whitelistDataAccess;

    public RoleAuditFilter()
    {
        this(AuditWhitelistCache.getInstance(), WhitelistDataAccess.getInstance());
    }

    @VisibleForTesting
    RoleAuditFilter(AuditWhitelistCache whitelistCache, WhitelistDataAccess whitelistDataAccess)
    {
        this.whitelistCache = whitelistCache;
        this.whitelistDataAccess = whitelistDataAccess;
    }

    @Override
    public void setup()
    {
        whitelistDataAccess.setup();
    }

    /**
     * Returns true if the supplied log entry's role or any other role granted to it (directly or indirectly) is
     * white-listed for the log entry's specified operation and resource.
     *
     * @param logEntry
     *            the log entry specifying the primary role as well as operation and resource
     * @return true if the operation is white-listed, false otherwise
     */
    @Override
    public boolean isFiltered(AuditEntry logEntry)
    {
        RoleResource primaryRole = RoleResource.role(logEntry.getUser());
        return isFiltered(Roles.getRoles(primaryRole), logEntry.getPermissions(), logEntry.getResource());
    }

    boolean isFiltered(Set<RoleResource> roles, Set<Permission> operations, IResource resource)
    {
        for (RoleResource role : roles)
        {
            Map<String, Set<IResource>> roleOptions = whitelistCache.getWhitelist(role);
            if (isResourceOperationWhitelisted(roleOptions, resource))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the supplied option map is white-listing the specified resource.
     *
     * @param roleOptions
     *            the role options as stored in Cassandra
     * @param operationResource
     *            the resource being accessed
     * @return true if the resource is white-listed, false otherwise
     */
    boolean isResourceOperationWhitelisted(Map<String, Set<IResource>> roleOptions, IResource operationResource)
    {
        Set<IResource> whitelistResources = roleOptions.get(AuditWhitelistManager.OPERATION_ALL);
        if (whitelistResources == null)
        {
            return false;
        }

        for (IResource whitelistResource : whitelistResources)
        {
            if (isResourceAccepted(whitelistResource, operationResource))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isResourceAccepted(IResource whitelistResource, IResource operationResource)
    {
        if (whitelistResource.equals(operationResource))
        {
            return true;
        }

        if (operationResource.hasParent())
        {
            return isResourceAccepted(whitelistResource, operationResource.getParent());
        }

        return false;
    }
}
