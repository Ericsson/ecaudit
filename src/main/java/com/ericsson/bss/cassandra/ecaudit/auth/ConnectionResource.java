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

import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

/**
 * IResource implementation representing database connections.
 *
 * The root level "connections" resource represents the collection of all Connection types.
 *
 * "connections" - the root level collection resource
 *
 * Currently only root level resources are represented, but this could be extended to separate between native and JMX
 * connections.
 */
public class ConnectionResource implements IResource, Comparable<ConnectionResource>
{
    enum Level
    {
        ROOT
    }

    // permissions which may be granted on the root level resource where EXECUTE represent "connect"
    private static final Set<Permission> ROOT_LEVEL_PERMISSIONS = Sets.immutableEnumSet(Permission.AUTHORIZE,
            Permission.EXECUTE);

    private static final String ROOT_NAME = "connections";
    private static final ConnectionResource ROOT_RESOURCE = new ConnectionResource();

    private final Level level;
    private final String name;

    private ConnectionResource()
    {
        level = Level.ROOT;
        name = null;
    }

    /**
     * @return the root-level resource.
     */
    public static ConnectionResource root()
    {
        return ROOT_RESOURCE;
    }

    /**
     * Parses a connection resource name into a ConnectionResource instance.
     *
     * @param name
     *            Name of the connection resource.
     * @return ConnectionResource instance matching the name.
     */
    public static ConnectionResource fromName(String name)
    {
        String[] parts = StringUtils.split(name, '/');

        if (!parts[0].equals(ROOT_NAME) || parts.length > 1)
        {
            throw new IllegalArgumentException(String.format("%s is not a valid connection resource name", name));
        }

        return root();
    }

    @Override
    public String getName()
    {
        return level == Level.ROOT ? ROOT_NAME : String.format("%s/%s", ROOT_NAME, name);
    }

    @Override
    public IResource getParent()
    {
        throw new IllegalStateException("Root-level resource can't have a parent");
    }

    @Override
    public boolean hasParent()
    {
        return level != Level.ROOT;
    }

    @Override
    public boolean exists()
    {
        return level == Level.ROOT;
    }

    @Override
    public Set<Permission> applicablePermissions()
    {
        return ROOT_LEVEL_PERMISSIONS;
    }

    @Override
    public int compareTo(ConnectionResource o)
    {
        return this.getName().compareTo(o.getName());
    }

    @Override
    public String toString()
    {
        return level == Level.ROOT ? "<all connections>" : String.format("<connection %s>", name);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null)
        {
            return false;
        }

        if (this == o)
        {
            return true;
        }

        if (o.getClass() != getClass())
        {
            return false;
        }

        ConnectionResource cs = (ConnectionResource) o;

        return Objects.equal(level, cs.level) && Objects.equal(name, cs.name);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(level, name);
    }
}
