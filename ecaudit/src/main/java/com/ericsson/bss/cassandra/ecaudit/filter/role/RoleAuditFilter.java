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
package com.ericsson.bss.cassandra.ecaudit.filter.role;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.auth.WhitelistDataAccess;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.utils.Exceptions;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.Resources;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.auth.Roles;
import org.apache.cassandra.exceptions.UnavailableException;

/**
 * A role based white-list filter that exempts users based on custom options on roles in Cassandra.
 * e.g. the resource "data/myKeyspace/myTable" can be white-listed for SELECT operations for a specific role,
 * then no SELECT operations by the that user/role on that table will be audited.
 * <p>
 * Grants-based white-lists can be used in a similar way.
 * e.g. the grant resource "grants/data/aKeyspace" can be white-listed for SELECT operations,
 * then no SELECT operations on tables in "aKeyspace" will we audited IF the user/role have permissions to perform that
 * operation.
 */
public class RoleAuditFilter implements AuditFilter
{
    private static final Logger LOG = LoggerFactory.getLogger(RoleAuditFilter.class);

    private final Function<RoleResource, Set<RoleResource>> getRolesFunction;
    private final RoleAuditFilterCache filterCache;
    private final WhitelistDataAccess whitelistDataAccess;
    private final AuditFilterAuthorizer auditFilterAuthorizer;

    public RoleAuditFilter()
    {
        this(Roles::getRoles, WhitelistDataAccess.getInstance(), new AuditFilterAuthorizer());
    }

    @VisibleForTesting
    RoleAuditFilter(Function<RoleResource, Set<RoleResource>> getRolesFunction, WhitelistDataAccess whitelistDataAccess, AuditFilterAuthorizer auditFilterAuthorizer)
    {
        this.getRolesFunction = getRolesFunction;
        this.filterCache = new RoleAuditFilterCache(this::isWhitelistedUnchecked);
        this.whitelistDataAccess = whitelistDataAccess;
        this.auditFilterAuthorizer = auditFilterAuthorizer;
    }

    @Override
    public void setup()
    {
        whitelistDataAccess.setup();
    }

    /**
     * Returns true if the supplied log entry's role or any other role granted to it (directly or indirectly) is
     * white-listed for the log entry's specified operations and resource.
     *
     * If several operations are specified by the log entry (which typically happens on CAS operations), then all of the
     * operations must be whitelisted.
     *
     * A resource is considered to be whitelisted if it, or any of its parents are mentioned in a roles whitelist.
     *
     * A resource white-list can be either "direct" or "grants-based".
     * Grants-based white-list requires not only the resource to be white-listed, but also that the user/role have
     * authorization to perform the operation on that resource to exempt audit logging.
     *
     * @param logEntry the log entry specifying the primary role as well as operation and resource
     * @return true if the operation is white-listed, false otherwise
     */
    @Override
    public boolean isWhitelisted(AuditEntry logEntry)
    {
        try
        {
            return isWhitelistedMaybeUnavailable(logEntry);
        }
        catch (UnavailableException e)
        {
            LOG.debug("Audit entry for {} not whitelisted as filter backend is unavailable", logEntry.getUser(), e);
            return false;
        }
    }

    private boolean isWhitelistedMaybeUnavailable(AuditEntry logEntry)
    {
        RoleAuditFilterCacheKey cacheKey = new RoleAuditFilterCacheKey(logEntry.getUser(), logEntry.getResource(), logEntry.getPermissions());
        try
        {
            return filterCache.isWhitelisted(cacheKey);
        }
        catch (UncheckedExecutionException e)
        {
            throw Exceptions.tryGetCassandraExceptionCause(e);
        }
    }

    private boolean isWhitelistedUnchecked(RoleAuditFilterCacheKey cacheKey)
    {
        Set<RoleResource> roles = getRoles(cacheKey.getUser());
        List<? extends IResource> operationResourceChain = Resources.chain(cacheKey.getResource());
        return cacheKey.getPermissions().stream()
                       .allMatch(permission -> isOperationWhitelistedOnResourceByRoles(permission, operationResourceChain, roles, cacheKey.getUser()));
    }

    private Set<RoleResource> getRoles(String username)
    {
        RoleResource primaryRole = RoleResource.role(username);
        return getRolesFunction.apply(primaryRole);
    }

    private boolean isOperationWhitelistedOnResourceByRoles(Permission operation, List<? extends IResource> operationResourceChain, Set<RoleResource> roles, String user)
    {
        return roles.stream()
                    .anyMatch(role -> isOperationWhitelistedOnResourceByRole(operation, operationResourceChain, role, user));
    }

    private boolean isOperationWhitelistedOnResourceByRole(Permission operation, List<? extends IResource> operationResourceChain, RoleResource role, String user)
    {
        RoleWhitelistChecker whitelistChecker = new RoleWhitelistChecker(operation, operationResourceChain, whitelistDataAccess.getWhitelist(role));

        return whitelistChecker.isWhitelisted()
               || whitelistChecker.isGrantWhitelisted()
                  && auditFilterAuthorizer.isOperationAuthorizedForUser(operation, user, operationResourceChain);
    }
}
