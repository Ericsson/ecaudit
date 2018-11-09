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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.exceptions.UnauthorizedException;

/**
 * The {@link AuditWhitelistManager} maintain role specific audit white-lists in a dedicated table in Cassandra.
 *
 * It provides an interface to manage white-lists via custom role options.
 * Only users with permission can manage white-lists.
 * It is possible to white-list access to all data and to authentication attempts on connections.
 */
public class AuditWhitelistManager
{
    private static final String OPERATION_ALL = "ALL";

    public static final String OPTION_AUDIT_WHITELIST_ALL = "audit_whitelist_for_all";

    private final WhitelistDataAccess whitelistDataAccess;
    private final WhitelistOptionParser whitelistOptionParser;
    private final WhitelistContract whitelistContract;

    public AuditWhitelistManager()
    {
        this(new WhitelistDataAccess());
    }

    AuditWhitelistManager(WhitelistDataAccess whitelistDataAccess)
    {
        this.whitelistDataAccess = whitelistDataAccess;
        this.whitelistOptionParser = new WhitelistOptionParser();
        this.whitelistContract = new WhitelistContract();
    }

    public void setup()
    {
        whitelistDataAccess.setup();
    }

    public void createRoleWhitelist(AuthenticatedUser performer, RoleResource role, RoleOptions options)
            throws RequestValidationException, RequestExecutionException
    {
        if (options.getCustomOptions().isPresent())
        {
            Map<String, Set<IResource>> addStatements = new HashMap<>();
            for (Map.Entry<String, String> optionEntry : options.getCustomOptions().get().entrySet())
            {
                WhitelistOperation whitelistOperation = whitelistOptionParser.parseWhitelistOperation(optionEntry.getKey());
                Set<IResource> resources = whitelistOptionParser.parseResource(optionEntry.getValue());
                String operation = whitelistOptionParser.parseTargetOperation(optionEntry.getKey());

                whitelistContract.verifyCreateRoleOption(whitelistOperation);
                checkPermissionToWhitelist(performer, resources);

                addStatements.put(operation, resources);
            }

            addStatements.forEach(
                    (o, r) -> whitelistDataAccess.addToWhitelist(role, o, r));
        }
    }

    public void alterRoleWhitelist(AuthenticatedUser performer, RoleResource role, RoleOptions options)
    {
        if (options.getCustomOptions().isPresent())
        {
            Map<String, Set<IResource>> addStatements = new HashMap<>();
            Map<String, Set<IResource>> removeStatements = new HashMap<>();
            for (Map.Entry<String, String> optionEntry : options.getCustomOptions().get().entrySet())
            {
                WhitelistOperation whitelistOperation = whitelistOptionParser.parseWhitelistOperation(optionEntry.getKey());
                Set<IResource> resources = whitelistOptionParser.parseResource(optionEntry.getValue());
                String operation = whitelistOptionParser.parseTargetOperation(optionEntry.getKey());

                checkPermissionToWhitelist(performer, resources);

                if (whitelistOperation == WhitelistOperation.GRANT)
                {
                    addStatements.put(operation, resources);
                }
                else
                {
                    removeStatements.put(operation, resources);
                }
            }

            addStatements.forEach(
                    (o, r) -> whitelistDataAccess.addToWhitelist(role, o, r));
            removeStatements.forEach(
                    (o, r) -> whitelistDataAccess.removeFromWhitelist(role, o, r));
        }
    }

    public Map<String, Set<IResource>> getRoleWhitelist(RoleResource role)
    {
        Map<String, Set<IResource>> daoWhitelist = whitelistDataAccess.getWhitelist(role);
        Set<IResource> resources = daoWhitelist.get(OPERATION_ALL);
        if (resources != null)
        {
            return Collections.singletonMap(OPTION_AUDIT_WHITELIST_ALL, resources);
        }
        else
        {
            return Collections.emptyMap();
        }
    }

    public void dropRoleWhitelist(RoleResource role)
    {
        whitelistDataAccess.deleteWhitelist(role);
    }

    private static void checkPermissionToWhitelist(AuthenticatedUser performer, Set<IResource> resources)
    {
        for (IResource resource : resources)
        {
            Set<Permission> userPermissions = performer.getPermissions(resource);
            if (!userPermissions.contains(Permission.AUTHORIZE))
            {
                throw new UnauthorizedException(String.format("User %s is not authorized to whitelist access to %s",
                        performer.getName(), resource));
            }
        }
    }
}
