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

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.CassandraRoleManager;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.PasswordAuthenticator;
import org.apache.cassandra.auth.Role;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.schema.SchemaConstants;

/**
 * Implements a {@link IRoleManager} that is meant to be paired with {@link AuditPasswordAuthenticator} and
 * {@link AuditAuthorizer}.
 *
 * It provides support for audit white-lists based on role options in Cassandra. This implementation is a decorator
 * which wraps the {@link CassandraRoleManager} for generic role management.
 *
 * An explicit permission check is enforced on ALTER statements. This makes it possible to grant whitelists from one
 * role to another.
 */
public class AuditRoleManager implements IRoleManager
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditRoleManager.class);

    private final IRoleManager wrappedRoleManager;
    private final AuditWhitelistManager whitelistManager;
    private final PermissionChecker permissionChecker;
    private final AuditAdapter auditAdapter;

    private final Set<Option> supportedOptions;
    private final Set<Option> alterableOptions;

    /**
     * Default constructor.
     *
     * Using this constructor wraps the {@link CassandraRoleManager} for actual role management. But this role manager
     * is paired with the {@link AuditAuthenticator} and it adds support for audit white-list options.
     */
    public AuditRoleManager()
    {
        this(new CassandraRoleManager(),
             new AuditWhitelistManager(),
             AuditAdapter.getInstance(),
             DatabaseDescriptor.getAuthenticator());
    }

    @VisibleForTesting
    AuditRoleManager(IRoleManager wrappedRoleManager, AuditWhitelistManager whitelistManager, AuditAdapter auditAdapter, IAuthenticator authenticator)
    {
        LOG.info("Auditing enabled on role manager");

        this.wrappedRoleManager = wrappedRoleManager;
        this.whitelistManager = whitelistManager;
        this.auditAdapter = auditAdapter;
        permissionChecker = new PermissionChecker();
        supportedOptions = resolveSupportedOptions(authenticator);
        alterableOptions = resolveAlterableOptions(authenticator);
    }

    private Set<Option> resolveSupportedOptions(IAuthenticator authenticator)
    {
        Set<Option> options = EnumSet.of(Option.LOGIN, Option.SUPERUSER, Option.OPTIONS);

        if (authenticator instanceof AuditAuthenticator)
        {
            AuditAuthenticator auditAuthenticator = (AuditAuthenticator) authenticator;
            options.addAll(auditAuthenticator.supportedOptions());
        }
        else if (authenticator instanceof PasswordAuthenticator)
        {
            options.add(Option.PASSWORD);
        }

        return options;
    }

    private Set<Option> resolveAlterableOptions(IAuthenticator authenticator)
    {
        Set<Option> options = EnumSet.noneOf(Option.class);

        if (authenticator instanceof AuditAuthenticator)
        {
            AuditAuthenticator auditAuthenticator = (AuditAuthenticator) authenticator;
            options.addAll(auditAuthenticator.alterableOptions());
        }
        else if (authenticator instanceof PasswordAuthenticator)
        {
            options.add(Option.PASSWORD);
        }

        options.add(Option.OPTIONS);

        return options;
    }


    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        wrappedRoleManager.validateConfiguration();
    }

    @Override
    public void setup()
    {
        wrappedRoleManager.setup();
        whitelistManager.setup();
        auditAdapter.setup();
    }

    @Override
    public Set<? extends IResource> protectedResources()
    {
        Set<IResource> combinedSet = Sets.newHashSet(wrappedRoleManager.protectedResources());
        combinedSet.add(DataResource.table(SchemaConstants.AUTH_KEYSPACE_NAME, AuditAuthKeyspace.WHITELIST_TABLE_NAME_V1));
        combinedSet.add(DataResource.table(SchemaConstants.AUTH_KEYSPACE_NAME, AuditAuthKeyspace.WHITELIST_TABLE_NAME_V2));
        return ImmutableSet.copyOf(combinedSet);
    }

    @Override
    public Set<Option> supportedOptions()
    {
        return supportedOptions;
    }

    @Override
    public Set<Option> alterableOptions()
    {
        return alterableOptions;
    }

    @Override
    public void createRole(AuthenticatedUser performer, RoleResource role, RoleOptions options)
    throws RequestValidationException, RequestExecutionException
    {
        whitelistManager.createRoleOption(options);
        wrappedRoleManager.createRole(performer, role, options);
    }

    @Override
    public void alterRole(AuthenticatedUser performer, RoleResource role, RoleOptions options)
    {
        permissionChecker.checkAlterRoleAccess(performer, role, options);
        whitelistManager.alterRoleOption(performer, role, options);
        wrappedRoleManager.alterRole(performer, role, options);
    }

    @Override
    public Map<String, String> getCustomOptions(RoleResource role)
    {
        return whitelistManager.getRoleWhitelist(role);
    }

    @Override
    public void grantRole(AuthenticatedUser performer, RoleResource role, RoleResource grantee)
    throws RequestValidationException, RequestExecutionException
    {
        wrappedRoleManager.grantRole(performer, role, grantee);
    }

    @Override
    public void revokeRole(AuthenticatedUser performer, RoleResource role, RoleResource revokee)
    throws RequestValidationException, RequestExecutionException
    {
        wrappedRoleManager.revokeRole(performer, role, revokee);
    }

    @Override
    public void dropRole(AuthenticatedUser performer, RoleResource role)
    throws RequestValidationException, RequestExecutionException
    {
        wrappedRoleManager.dropRole(performer, role);
        whitelistManager.dropRoleWhitelist(role);
    }

    @Override
    public Set<RoleResource> getRoles(RoleResource grantee, boolean includeInherited)
    throws RequestValidationException, RequestExecutionException
    {
        return wrappedRoleManager.getRoles(grantee, includeInherited);
    }

    @Override
    public Set<Role> getRoleDetails(RoleResource grantee)
    {
        return wrappedRoleManager.getRoleDetails(grantee);
    }

    @Override
    public Set<RoleResource> getAllRoles() throws RequestValidationException, RequestExecutionException
    {
        return wrappedRoleManager.getAllRoles();
    }

    @Override
    public boolean isSuper(RoleResource role)
    {
        return wrappedRoleManager.isSuper(role);
    }

    @Override
    public boolean canLogin(RoleResource role)
    {
        return wrappedRoleManager.canLogin(role);
    }

    @Override
    public boolean isExistingRole(RoleResource role)
    {
        return wrappedRoleManager.isExistingRole(role);
    }
}
