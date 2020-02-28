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
package com.ericsson.bss.cassandra.ecaudit.entry;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;

/**
 * The domain object which contains audit record information to be logged.
 *
 * Instances are immutable an may only be created using the {@link AuditEntry.Builder}.
 */
public class AuditEntry implements AuditRecord
{
    private final Map<String, Object> fields;

    private static final String CLIENT = "clientAddress";
    private static final String COORDINATOR = "coordinatorAddress";
    private static final String PERMISSIONS = "permissions";
    private static final String RESOURCE = "resource";
    private static final String OPERATION = "operation";
    private static final String USER = "user";
    private static final String BATCH_ID = "batchId";
    private static final String STATUS = "status";
    private static final String TIMESTAMP = "timestamp";

    @VisibleForTesting
    final static ImmutableSet<String> RESERVED_KEYS = ImmutableSet.<String>builder()
            .add(CLIENT, COORDINATOR, PERMISSIONS, RESOURCE, OPERATION, USER, BATCH_ID, STATUS, TIMESTAMP)
            .build();

    public static final int UNKNOWN_PORT = 0;

    /**
     * @see #newBuilder()
     */
    private AuditEntry(Builder builder)
    {
        fields = new HashMap<>(builder.fields);
    }

    @Override
    public InetSocketAddress getClientAddress()
    {
        return safeCast(fields.get(CLIENT), InetSocketAddress.class);
    }

    @Override
    public InetAddress getCoordinatorAddress()
    {
        return safeCast(fields.get(COORDINATOR), InetAddress.class);
    }

    /**
     * Gets the permissions of this value object.
     *
     * This getter provides access to the immutable permission Set of this value object.
     *
     * @return the permissions
     */
    public Set<Permission> getPermissions()
    {
        return safeCast(fields.get(PERMISSIONS), Set.class);
    }

    public IResource getResource()
    {
        return safeCast(fields.get(RESOURCE), IResource.class);
    }

    /**
     * Gets the operation in this value object.
     *
     * As the operation string may be relatively expensive to produce it may be calculated lazily
     * depending on the concrete implementation used.
     *
     * @return the audit operation
     */
    @Override
    public AuditOperation getOperation()
    {
        return safeCast(fields.get(OPERATION), AuditOperation.class);
    }

    @Override
    public String getUser()
    {
        return safeCast(fields.get(USER), String.class);
    }

    /**
     * Gets the optional batch id in this value object.
     *
     * @return the batch id
     */
    @Override
    public Optional<UUID> getBatchId()
    {
        return Optional.ofNullable(safeCast(fields.get(BATCH_ID), UUID.class));
    }

    @Override
    public Status getStatus()
    {
        return safeCast(fields.get(STATUS), Status.class);
    }

    /**
     * @return the timestamp when this entry was created. Represented by the number of milliseconds since Epoch.
     */
    @Override
    public Long getTimestamp()
    {
        return safeCast(fields.get(TIMESTAMP), Long.class);
    }

    /**
     * Get a named field of a specific type.
     * @param fieldName the name of the field
     * @param clazz the class of the field type
     * @param <T> the type of the field
     * @return the field of the wanted type, or null if it doesn't exist
     */
    public <T> T get(String fieldName, Class<T> clazz)
    {
        return safeCast(fields.get(fieldName), clazz);
    }

    /**
     * Get all custom fields in this entry
     * @return a map of all entries
     */
    public Map<String, Object> customFields()
    {
        return Collections.unmodifiableMap(this.fields.entrySet().stream()
                .filter(e -> !RESERVED_KEYS.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Create a new {@link Builder} instance.
     *
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
        private final Map<String, Object> fields = new LinkedHashMap<>();

        public Builder client(InetSocketAddress address)
        {
            fields.put(CLIENT, address);
            return this;
        }

        public Builder coordinator(InetAddress coordinator)
        {
            fields.put(COORDINATOR, coordinator);
            return this;
        }

        /**
         * Set the list of permissions associated with the operation to be logged.
         *
         * The builder and its resulting value object will not make a deep copy of the permission set.
         * It is assumed that users always pass in immutable permission sets.
         *
         * @param permissions the permissions associated with the operation
         * @return this builder instance
         */
        public Builder permissions(Set<Permission> permissions)
        {
            fields.put(PERMISSIONS, permissions);
            return this;
        }

        public Builder resource(IResource resource)
        {
            fields.put(RESOURCE, resource);
            return this;
        }

        /**
         * Set the audit operation that is to be logged.
         *
         * As the operation string may be relatively expensive to produce it may be calculated lazily
         * depending on the concrete implementation used.
         *
         * @param operation the audit operation
         * @return this builder instance
         */
        public Builder operation(AuditOperation operation)
        {
            fields.put(OPERATION, operation);
            return this;
        }

        public Builder user(String user)
        {
            fields.put(USER, user);
            return this;
        }

        /**
         * Set the optional batch identifier.
         *
         * @param uuid the batch id to use
         * @return this builder instance
         */
        public Builder batch(UUID uuid)
        {
            fields.put(BATCH_ID, uuid);
            return this;
        }

        public Builder status(Status status)
        {
            fields.put(STATUS, status);
            return this;
        }

        public Builder timestamp(Long timestamp)
        {
            fields.put(TIMESTAMP, timestamp);
            return this;
        }

        /**
         * Add a new generic field to this builder.
         *
         * Use this to add custom fields that can be serialized to a string with {@link String#toString}.
         * @param field the field name
         * @param value the field value
         * @param <T> the type of the field
         * @throws IllegalArgumentException if the field clashes with reserved keys.
         * @return this builder instance
         */
        public <T> Builder with(String field, T value) throws IllegalArgumentException
        {
            if (RESERVED_KEYS.contains(field))
            {
                throw new IllegalArgumentException(String.format("Not allowed to overwrite reserved field: %s", field));
            }
            fields.put(field, value);
            return this;
        }

        /**
         * Configure this builder from an existing {@link AuditEntry} instance.
         *
         * @param entry the instance to get values from
         * @return this builder instance
         */
        public Builder basedOn(AuditEntry entry)
        {
            this.fields.putAll(entry.fields);
            return this;
        }

        /**
         * Build a {@link AuditEntry} instance as configured by this builder.
         *
         * @return an {@link AuditEntry} instance
         */
        public AuditEntry build()
        {
            return new AuditEntry(this);
        }
    }

    private static <T> T safeCast(Object instance, Class<T> clazz)
    {
        return Optional.ofNullable(instance)
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .orElse(null);
    }
}
