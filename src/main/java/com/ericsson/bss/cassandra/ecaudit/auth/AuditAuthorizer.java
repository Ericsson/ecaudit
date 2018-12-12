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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.CassandraAuthorizer;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;

/**
 * Extend standard {@link CassandraAuthorizer} to always allow ALTER permission on {@link RoleResource}s.
 *
 * This will allow one role to grant whitelist permission to another role by changing the OPTIONS attribute of the other role.
 * Other attributes such as PASSWORD may only be ALTERed if the permission actually have been assigned.
 * This is enforced in {@link AuditRoleManager#alterRole(AuthenticatedUser, RoleResource, RoleOptions)}
 */
public class AuditAuthorizer extends CassandraAuthorizer
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditAuthorizer.class);

    public AuditAuthorizer()
    {
        LOG.info("Auditing enabled on authorizer");
    }

    @Override
    public Set<Permission> authorize(AuthenticatedUser user, IResource resource)
    {
        Set<Permission> permissions = EnumSet.copyOf(super.authorize(user, resource));
        if (resource.hasParent() && resource instanceof RoleResource)
        {
            permissions.add(Permission.ALTER);
        }

        return permissions;
    }

    Set<Permission> realAuthorize(AuthenticatedUser user, IResource resource)
    {
        return super.authorize(user, resource);
    }
}
