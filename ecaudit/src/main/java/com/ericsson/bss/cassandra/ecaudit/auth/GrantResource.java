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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Objects;
import java.util.Set;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * IResource implementation representing a grant on the wrapped resource.
 * <p>
 * The resource {@code grants/data/myKeyspace/myTable} represents a <i>grant</i> on the <i>wrapped resource</i>
 * {@code data/myKeyspace/myTable}
 * <p>
 * The "root-level" grant is returned by the {@link GrantResource#root()} method.
 */
public final class GrantResource implements IResource
{
    private static final GrantResource ROOT_LEVEL = new GrantResource(null);
    private static final String ROOT_NAME = "grants";

    private final IResource wrappedResource;

    private GrantResource(IResource wrappedResource)
    {
        this.wrappedResource = wrappedResource;
    }

    /**
     * @param resource the resource to wrap, must not be {@code null}
     * @return a new grant resource with the provided resource wrapped inside
     */
    public static GrantResource fromResource(IResource resource)
    {
        return new GrantResource(checkNotNull(resource));
    }

    IResource getWrappedResource()
    {
        return wrappedResource;
    }

    /**
     * @return the root-level resource.
     */
    public static GrantResource root()
    {
        return ROOT_LEVEL;
    }

    @Override
    public String getName()
    {
        return wrappedResource == null
               ? ROOT_NAME
               : ROOT_NAME + "/" + wrappedResource.getName();
    }

    /**
     * This method should not be used to travers the resource hierarchy. Use {@link #getWrappedResource()} instead.
     */
    @Override
    public IResource getParent()
    {
        throw new IllegalStateException("Root-level resource can't have a parent");
    }

    /**
     * This method should not be used to travers the resource hierarchy. Use {@link #getWrappedResource()} instead.
     */
    @Override
    public boolean hasParent()
    {
        return false;
    }

    @Override
    public boolean exists()
    {
        return wrappedResource != null && wrappedResource.exists();
    }

    @Override
    public Set<Permission> applicablePermissions()
    {
        return wrappedResource == null
               ? Permission.ALL
               : wrappedResource.applicablePermissions();
    }

    @Override
    public String toString()
    {
        return wrappedResource == null
               ? "<grants [all resources]>"
               : String.format("<grants [%s]>", wrappedResource);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        GrantResource that = (GrantResource) o;
        return Objects.equals(wrappedResource, that.wrappedResource);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(wrappedResource);
    }
}
