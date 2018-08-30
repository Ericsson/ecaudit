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
package com.ericsson.bss.cassandra.ecaudit.entry;

import java.net.InetAddress;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

/**
 * The domain object which contains audit record information to be logged.
 */
public class AuditEntry
{
    private final InetAddress clientAddress;
    private final Set<Permission> permissions;
    private final IResource resource;
    private final AuditOperation operation;
    private final String user;
    private final UUID batchId;
    private final Status status;

    /**
     * @see #newBuilder()
     */
    private AuditEntry(Builder builder)
    {
        this.clientAddress = builder.client;
        this.permissions = builder.permissions;
        this.resource = builder.resource;
        this.operation = builder.operation;
        this.user = builder.user;
        this.batchId = builder.batchId;
        this.status = builder.status;
    }

    /**
     * Gets the client address in this value object.
     * @return the audited client address
     */
    public InetAddress getClientAddress()
    {
        return clientAddress;
    }

    /**
     * Gets the permissions of this value object.
     *
     * This getter provides access to the permission set of this value object.
     * Don't change the content of the set.
     *
     * @return the permissions
     */
    public Set<Permission> getPermissions()
    {
        return permissions; // NOSONAR
    }

    /**
     * Gets the resource of this value object.
     * @return the resource
     */
    public IResource getResource()
    {
        return resource;
    }

    /**
     * Gets the operation in this value object.
     * @return the audit operation
     */
    public AuditOperation getOperation()
    {
        return operation;
    }

    /**
     * Gets the user in this value object.
     * @return the user name
     */
    public String getUser()
    {
        return user;
    }

    /**
     * Gets the batch id in this value object.
     * @return the batch id (optional)
     */
    public Optional<UUID> getBatchId()
    {
        return Optional.ofNullable(batchId);
    }

    /**
     * Gets the status of the operation in this value object.
     * @return the status of the operation
     */
    public Status getStatus()
    {
        return status;
    }

    /**
     * Create a new {@link Builder} instance.
     * @return a new instance of {@link Builder}.
     */
    public static AuditEntry.Builder newBuilder()
    {
        return new Builder();
    }

    /**
     * Implements a builder of {@link AuditEntry}'s.
     */
    public static class Builder
    {
        private InetAddress client;
        private Set<Permission> permissions;
        private IResource resource;
        private AuditOperation operation;
        private String user;
        private UUID batchId;
        private Status status;

        /**
         * Set the client address to be logged
         * @param address the address of the audited client
         * @return this instance, for method chaining
         */
        public Builder client(InetAddress address)
        {
            this.client = address;
            return this;
        }

        /**
         * Set the list of permissions associated with the operation to be logged
         *
         * The builder and its resulting value object will not make a deep copy of the permission set.
         * It is assumed that users always pass in immutable permission sets.
         *
         * @param permissions the permissions associated with the operation
         * @return this instance, for method chaining
         */
        public Builder permissions(Set<Permission> permissions)
        {
            this.permissions = permissions; // NOSONAR
            return this;
        }

        /**
         * Set the resource associated with the operation to be logged
         * @param resource the resource of the operation
         * @return this instance, for method chaining
         */
        public Builder resource(IResource resource)
        {
            this.resource = resource;
            return this;
        }

        /**
         * Set the auidt operation that is to be logged
         * @param operation the audit operation
         * @return this instance, for method chaining
         */
        public Builder operation(AuditOperation operation)
        {
            this.operation = operation;
            return this;
        }

        /**
         * Set the username that is to be logged
         * @param user the user name
         * @return this instance, for method chaining
         */
        public Builder user(String user)
        {
            this.user = user;
            return this;
        }

        /**
         * Set the batch identifer (optional)
         * @param uuid the batch id to use
         * @return this instance, for method chaining
         */
        public Builder batch(UUID uuid)
        {
            this.batchId = uuid;
            return this;
        }

        /**
         * Set the state of the operation
         * @param status
         *            the operation status
         * @return this instance, for method chaining
         */
        public Builder status(Status status)
        {
            this.status = status;
            return this;
        }

        /**
         * Configure this builder from an existing {@link AuditEntry}
         * instance.
         * @param entry the instance to get values from
         * @return this instance, for method chaining
         */
        public Builder basedOn(AuditEntry entry)
        {
            this.client = entry.getClientAddress();
            this.permissions = entry.getPermissions();
            this.resource = entry.getResource();
            this.operation = entry.getOperation();
            this.user = entry.getUser();
            this.batchId = entry.getBatchId().orElse(null);
            this.status = entry.getStatus();
            return this;
        }

        /**
         * Build a {@link AuditEntry} instance as configured by this builder.
         * @return an {@link AuditEntry} instance
         */
        public AuditEntry build()
        {
            return new AuditEntry(this);
        }

    }
}
