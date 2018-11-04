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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.CassandraRoleManager;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;

/**
 * Implements a {@link IRoleManager} that is meant to be paired with {@link AuditPasswordAuthenticator} and {@link AuditAuthorizer}.
 *
 * It provides support for audit white-lists based on role options in Cassandra. This implementation inherits the
 * {@link CassandraRoleManager} for generic role management.
 *
 * An explicit permission check is enforced on ALTER statements. This makes it possible to grant whitelists from one role to another.
 */
public class AuditRoleManager extends CassandraRoleManager
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditRoleManager.class);

    private final AuditWhitelistManager whitelistManager;
    private final PermissionChecker permissionChecker;

    private final Set<Option> supportedOptions;
    private final Set<Option> alterableOptions;

    /**
     * Default constructor.
     *
     * Using this constructor wraps the {@link CassandraRoleManager} for actual role management. But this role manager
     * is paired with the {@link AuditPasswordAuthenticator} and it adds support for audit white-list options.
     */
    public AuditRoleManager()
    {
        LOG.info("Auditing enabled on role manager");

        whitelistManager = new AuditWhitelistManager();
        permissionChecker = new PermissionChecker();

        supportedOptions = DatabaseDescriptor.getAuthenticator().getClass() == AuditPasswordAuthenticator.class
                ? ImmutableSet.of(Option.LOGIN, Option.SUPERUSER, Option.PASSWORD, Option.OPTIONS)
                : ImmutableSet.of(Option.LOGIN, Option.SUPERUSER);
        alterableOptions = DatabaseDescriptor.getAuthenticator().getClass().equals(AuditPasswordAuthenticator.class)
                ? ImmutableSet.of(Option.PASSWORD)
                : ImmutableSet.of();
    }

    @Override
    public void setup()
    {
        super.setup();

        whitelistManager.setup();
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
        whitelistManager.createRoleWhitelist(performer, role, options);
        super.createRole(performer, role, options);
    }

    @Override
    public void alterRole(AuthenticatedUser performer, RoleResource role, RoleOptions options)
    {
        permissionChecker.checkAlterRoleAccess(performer, role, options);
        whitelistManager.alterRoleWhitelist(performer, role, options);
        super.alterRole(performer, role, options);
    }

    @Override
    public Map<String, String> getCustomOptions(RoleResource role)
    {
        return whitelistManager.getRoleWhitelist(role).entrySet().stream()
                               .collect(Collectors.toMap(e -> e.getKey(), e -> ResourceFactory.toNameCsv(e.getValue())));
    }

    @Override
    public void dropRole(AuthenticatedUser performer, RoleResource role)
            throws RequestValidationException, RequestExecutionException
    {
        super.dropRole(performer, role);
        whitelistManager.dropRoleWhitelist(role);
    }
}
