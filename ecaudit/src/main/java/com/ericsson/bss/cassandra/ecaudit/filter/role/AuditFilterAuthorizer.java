/*
 * Copyright 2020 Telefonaktiebolaget LM Ericsson
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.ericsson.bss.cassandra.ecaudit.auth.AuditAuthorizer;
import com.ericsson.bss.cassandra.ecaudit.utils.AuthenticatedUserUtil;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.SystemKeyspace;
import org.apache.cassandra.schema.SchemaConstants;

public class AuditFilterAuthorizer
{
    // Matching Apache Cassandra operations defined in ClientState
    private static final Set<IResource> READABLE_SYSTEM_RESOURCES = new HashSet<>();

    // From SchemaKeyspace, will cause initialization errors if accessed directly
    private static final ImmutableList<String> ALL_SCHEMA_TABLES = ImmutableList.of("columns", "dropped_columns", "triggers", "types", "functions", "aggregates", "indexes", "tables", "views", "keyspaces");
    private static final ImmutableList<String> ALL_VIRTUAL_SCHEMA_TABLES = ImmutableList.of("columns", "tables", "keyspaces");

    // peers is read by the driver as well
    private static final String PEERS = "peers";

    static
    {
        for (String cf : Arrays.asList(SystemKeyspace.LOCAL, SystemKeyspace.PEERS_V2, PEERS))
        {
            READABLE_SYSTEM_RESOURCES.add(DataResource.table(SchemaConstants.SYSTEM_KEYSPACE_NAME, cf));
        }

        ALL_SCHEMA_TABLES.forEach(table -> READABLE_SYSTEM_RESOURCES.add(DataResource.table(SchemaConstants.SCHEMA_KEYSPACE_NAME, table)));
        ALL_VIRTUAL_SCHEMA_TABLES.forEach(table -> READABLE_SYSTEM_RESOURCES.add(DataResource.table(SchemaConstants.VIRTUAL_SCHEMA, table)));
    }

    private IAuthorizer authorizer; // lazy initialization

    public boolean isOperationAuthorizedForUser(Permission operation, String user, List<? extends IResource> resourceChain)
    {
        if (isReadingSystemResource(operation, resourceChain))
        {
            return true;
        }

        AuthenticatedUser authUser = AuthenticatedUserUtil.createFromString(user);
        IAuthorizer authorizer = getAuthorizer();
        return resourceChain.stream()
                     .map(resource -> authorizer.authorize(authUser, resource))
                     .anyMatch(permissions -> permissions.contains(operation));
    }

    @VisibleForTesting
    void setAuthorizer(IAuthorizer authorizer)
    {
        this.authorizer = authorizer;
    }

    IAuthorizer getAuthorizer()
    {
        if (authorizer == null)
        {
            resolveAuthorizerSync();
        }
        return authorizer;
    }

    private static boolean isReadingSystemResource(Permission operation, List<? extends IResource> resourceChain)
    {
        if (operation != Permission.SELECT)
        {
            return false;
        }

        return resourceChain.stream().anyMatch(READABLE_SYSTEM_RESOURCES::contains);
    }

    private synchronized void resolveAuthorizerSync()
    {
        if (authorizer == null)
        {
            IAuthorizer currentAuthorizer = DatabaseDescriptor.getAuthorizer();
            if (currentAuthorizer instanceof AuditAuthorizer)
            {
                // AuditAuthorizer adds the ALTER permission, so we must use the wrapped Authorizer instead
                currentAuthorizer = ((AuditAuthorizer) currentAuthorizer).getWrappedAuthorizer();
            }
            authorizer = currentAuthorizer;
        }
    }
}
