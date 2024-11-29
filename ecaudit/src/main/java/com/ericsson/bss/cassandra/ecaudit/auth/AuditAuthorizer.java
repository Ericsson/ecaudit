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
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.PermissionDetails;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.utils.FBUtilities;

/**
 * A decorator of a generic {@link IAuthorizer} which always allow ALTER permission on {@link RoleResource}s.
 *
 * This will allow one role to grant whitelist permission to another role by changing the OPTIONS attribute of the other role.
 * Other attributes such as PASSWORD may only be ALTERed if the permission actually have been assigned.
 * This is enforced in {@link AuditRoleManager#alterRole(AuthenticatedUser, RoleResource, RoleOptions)}
 */
public class AuditAuthorizer implements IAuthorizer
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditAuthorizer.class);

    private final IAuthorizer wrappedAuthorizer;

    public AuditAuthorizer()
    {
        this(newWrappedAuthorizer(AuditConfig.getInstance()));
    }

    @VisibleForTesting
    static IAuthorizer newWrappedAuthorizer(AuditConfig auditConfig)
    {
        String authorizerName = auditConfig.getWrappedAuthorizer();
        return FBUtilities.construct(authorizerName, "authorizer");
    }

    @VisibleForTesting
    AuditAuthorizer(IAuthorizer wrappedAuthorizer)
    {
        LOG.info("Auditing enabled on authorizer");
        this.wrappedAuthorizer = wrappedAuthorizer;
    }

    @Override
    public Set<Permission> authorize(AuthenticatedUser user, IResource resource)
    {
        if (resource.hasParent() && resource instanceof RoleResource)
        {
            Set<Permission> permissions = EnumSet.copyOf(wrappedAuthorizer.authorize(user, resource));
            permissions.add(Permission.ALTER);
            return permissions;
        }
        else
        {
            return wrappedAuthorizer.authorize(user, resource);
        }
    }

    Set<Permission> realAuthorize(AuthenticatedUser user, IResource resource)
    {
        return wrappedAuthorizer.authorize(user, resource);
    }

    @Override
    public Set<Permission> grant(AuthenticatedUser performer, Set<Permission> permissions, IResource resource, RoleResource grantee) throws RequestValidationException, RequestExecutionException
    {
        return wrappedAuthorizer.grant(performer, permissions, resource, grantee);
    }

    @Override
    public Set<Permission> revoke(AuthenticatedUser performer, Set<Permission> permissions, IResource resource, RoleResource revokee) throws RequestValidationException, RequestExecutionException
    {
        return wrappedAuthorizer.revoke(performer, permissions, resource, revokee);
    }

    @Override
    public Set<PermissionDetails> list(AuthenticatedUser performer, Set<Permission> permissionFilter, IResource resourceFilter, RoleResource roleFilter) throws RequestValidationException, RequestExecutionException
    {
        return wrappedAuthorizer.list(performer, permissionFilter, resourceFilter, roleFilter);
    }

    @Override
    public void revokeAllFrom(RoleResource revokee)
    {
        wrappedAuthorizer.revokeAllFrom(revokee);
    }

    @Override
    public void revokeAllOn(IResource droppedResource)
    {
        wrappedAuthorizer.revokeAllOn(droppedResource);
    }

    @Override
    public Set<? extends IResource> protectedResources()
    {
        return wrappedAuthorizer.protectedResources();
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        wrappedAuthorizer.validateConfiguration();
    }

    @Override
    public void setup()
    {
        wrappedAuthorizer.setup();
    }

    public IAuthorizer getWrappedAuthorizer()
    {
        return wrappedAuthorizer;
    }
}
