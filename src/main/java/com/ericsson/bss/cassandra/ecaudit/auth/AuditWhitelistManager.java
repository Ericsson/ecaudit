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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.UnauthorizedException;

/**
 * The {@link AuditWhitelistManager} maintain role specific audit white-lists in a dedicated table in Cassandra.
 *
 * It provides an interface to manage white-lists via custom role options.
 * Only users with permission can manage white-lists.
 * It is possible to white-list access to all data and to authentication attempts on connections.
 */
class AuditWhitelistManager
{
    private final WhitelistDataAccess whitelistDataAccess;
    private final WhitelistOptionParser whitelistOptionParser;
    private final WhitelistContract whitelistContract;

    AuditWhitelistManager()
    {
        this(WhitelistDataAccess.getInstance());
    }

    @VisibleForTesting
    AuditWhitelistManager(WhitelistDataAccess whitelistDataAccess)
    {
        this.whitelistDataAccess = whitelistDataAccess;
        this.whitelistOptionParser = new WhitelistOptionParser();
        this.whitelistContract = new WhitelistContract();
    }

    void verifyCreateRoleOptions(RoleOptions options)
    {
        if (options.getCustomOptions().isPresent())
        {
            throw new InvalidRequestException("Options are not supported when creating a new role");
        }
    }

    void alterRoleWhitelist(AuthenticatedUser performer, RoleResource role, RoleOptions options)
    {
        if (options.getCustomOptions().isPresent())
        {
            Map<Permission, Set<IResource>> whitelistToGrant = new HashMap<>();
            Map<Permission, Set<IResource>> whitelistToRevoke = new HashMap<>();
            for (Map.Entry<String, String> optionEntry : options.getCustomOptions().get().entrySet())
            {
                WhitelistOperation whitelistOperation = whitelistOptionParser.parseWhitelistOperation(optionEntry.getKey());
                IResource resource = whitelistOptionParser.parseResource(optionEntry.getValue());
                Set<Permission> operations = whitelistOptionParser.parseTargetOperation(optionEntry.getKey(), resource);

                whitelistContract.verify(operations, resource);
                checkPermissionToWhitelist(performer, resource);

                if (whitelistOperation == WhitelistOperation.GRANT)
                {
                    for (Permission operation : operations)
                    {
                        whitelistToGrant.compute(operation, (opr, res) -> createOrExtend(res, resource));
                    }
                }
                else
                {
                    for (Permission operation : operations)
                    {
                        whitelistToRevoke.compute(operation, (opr, res) -> createOrExtend(res, resource));
                    }
                }
            }

            whitelistDataAccess.addToWhitelist(role, whitelistToGrant);
            whitelistDataAccess.removeFromWhitelist(role, whitelistToRevoke);
        }
    }

    private Set<IResource> createOrExtend(Set<IResource> resources, IResource resource)
    {
        Set<IResource> newSet = resources != null ? resources : Sets.newHashSet();
        newSet.add(resource);
        return newSet;
    }

    Map<Permission, Set<IResource>> getRoleWhitelist(RoleResource role)
    {
        return whitelistDataAccess.getWhitelist(role);
    }

    void dropRoleWhitelist(RoleResource role)
    {
        whitelistDataAccess.deleteWhitelist(role);
    }

    private static void checkPermissionToWhitelist(AuthenticatedUser performer, IResource resource)
    {
        Set<Permission> userPermissions = performer.getPermissions(resource);
        if (!userPermissions.contains(Permission.AUTHORIZE))
        {
            throw new UnauthorizedException(String.format("User %s is not authorized to whitelist access to %s",
                                                          performer.getName(), resource));
        }
    }
}
