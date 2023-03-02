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
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

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

    public void setup()
    {
        whitelistDataAccess.setup();
    }

    void createRoleOption(RoleOptions options)
    {
        if (options.getCustomOptions().isPresent())
        {
            throw new InvalidRequestException("Whitelist options are not supported in CREATE ROLE statements");
        }
    }

    void alterRoleOption(AuthenticatedUser performer, RoleResource role, RoleOptions options)
    {
        if (!options.getCustomOptions().isPresent())
        {
            return;
        }

        if (options.getCustomOptions().get().size() != 1)
        {
            throw new InvalidRequestException("Exactly one whitelist option is supported in ALTER ROLE statements");
        }

        Map.Entry<String, String> optionEntry = options.getCustomOptions().get().entrySet().iterator().next();
        WhitelistOperation whitelistOperation = whitelistOptionParser.parseWhitelistOperation(optionEntry.getKey());

        dispatchOperation(whitelistOperation, performer, role, optionEntry);
    }

    @VisibleForTesting
    void dispatchOperation(WhitelistOperation whitelistOperation, AuthenticatedUser performer, RoleResource role, Map.Entry<String, String> optionEntry)
    {
        if (whitelistOperation == WhitelistOperation.GRANT)
        {
            addToWhitelist(performer, role, optionEntry);
        }
        else if (whitelistOperation == WhitelistOperation.REVOKE)
        {
            removeFromWhitelist(performer, role, optionEntry);
        }
        else
        {
            throw new InvalidRequestException(String.format("Illegal whitelist option [%s]", whitelistOperation));
        }
    }

    private void addToWhitelist(AuthenticatedUser performer, RoleResource role, Map.Entry<String, String> optionEntry)
    {
        IResource resource = whitelistOptionParser.parseResource(optionEntry.getValue());
        Set<Permission> operations = whitelistOptionParser.parseTargetOperation(optionEntry.getKey(), resource);

        whitelistContract.verify(operations, resource);
        checkPermissionToWhitelist(performer, resource);

        whitelistDataAccess.addToWhitelist(role, resource, operations);
    }

    private void removeFromWhitelist(AuthenticatedUser performer, RoleResource role, Map.Entry<String, String> optionEntry)
    {
        IResource resource = whitelistOptionParser.parseResource(optionEntry.getValue());
        Set<Permission> operations = whitelistOptionParser.parseTargetOperation(optionEntry.getKey(), resource);

        whitelistContract.verify(operations, resource);
        checkPermissionToWhitelist(performer, resource);

        whitelistDataAccess.removeFromWhitelist(role, resource, operations);
    }

    Map<String, String> getRoleWhitelist(RoleResource role)
    {
        Map<IResource, Set<Permission>> whitelist = whitelistDataAccess.getWhitelist(role);
        return whitelist.entrySet()
                        .stream()
                        .collect(Collectors.toMap(e -> ResourceFactory.toPrintableName(e.getKey()), e -> OperationFactory.toOperationNameCsv(e.getValue())));
    }

    void dropRoleWhitelist(RoleResource role)
    {
        whitelistDataAccess.deleteWhitelist(role);
    }

    private static void checkPermissionToWhitelist(AuthenticatedUser performer, IResource resource)
    {
        if (!performer.isSuper())
        {
            IResource actualResource = resource instanceof GrantResource
                                       ? ((GrantResource) resource).getWrappedResource()
                                       : resource;

            if (actualResource == null || !performer.getPermissions(actualResource).contains(Permission.AUTHORIZE))
            {
                throw new UnauthorizedException(String.format("User %s is not authorized to whitelist access to %s",
                                                              performer.getName(), resource));
            }
        }
    }
}
