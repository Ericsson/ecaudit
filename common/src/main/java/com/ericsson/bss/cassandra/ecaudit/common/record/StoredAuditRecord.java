/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.common.record;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;

/**
 * Audit record that have been re-read into memory from storage.
 * Since it is configurable which fields to store, all fields in this record are optional.
 */
public class StoredAuditRecord
{
    private final InetAddress clientAddress;
    private final Integer clientPort;
    private final InetAddress coordinatorAddress;
    private final String user;
    private final UUID batchId;
    private final Status status;
    private final String operation;
    private final String nakedOperation;
    private final Long timestamp;

    private StoredAuditRecord(Builder builder)
    {
        this.clientAddress = builder.clientAddress;
        this.clientPort = builder.clientPort;
        this.coordinatorAddress = builder.coordinatorAddress;
        this.user = builder.user;
        this.batchId = builder.batchId;
        this.status = builder.status;
        this.operation = builder.operation;
        this.nakedOperation = builder.nakedOperation;
        this.timestamp = builder.timestamp;
    }

    public Optional<Long> getTimestamp()
    {
        return Optional.ofNullable(timestamp);
    }

    public Optional<InetAddress> getClientAddress()
    {
        return Optional.ofNullable(clientAddress);
    }

    public Optional<Integer> getClientPort()
    {
        return Optional.ofNullable(clientPort);
    }

    public Optional<InetAddress> getCoordinatorAddress()
    {
        return Optional.ofNullable(coordinatorAddress);
    }

    public Optional<String> getUser()
    {
        return Optional.ofNullable(user);
    }

    public Optional<UUID> getBatchId()
    {
        return Optional.ofNullable(batchId);
    }

    public Optional<Status> getStatus()
    {
        return Optional.ofNullable(status);
    }

    public Optional<String> getOperation()
    {
        return Optional.ofNullable(operation);
    }

    public Optional<String> getNakedOperation()
    {
        return Optional.ofNullable(nakedOperation);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private InetAddress clientAddress;
        private Integer clientPort;
        private InetAddress coordinatorAddress;
        private String user;
        private UUID batchId;
        private Status status;
        private String operation;
        private String nakedOperation;
        private Long timestamp;

        public Builder withClientAddress(InetAddress clientAddress)
        {
            this.clientAddress = clientAddress;
            return this;
        }

        public Builder withClientPort(int clientPort)
        {
            this.clientPort = clientPort;
            return this;
        }

        public Builder withCoordinatorAddress(InetAddress coordinatorAddress)
        {
            this.coordinatorAddress = coordinatorAddress;
            return this;
        }

        public Builder withUser(String user)
        {
            this.user = user;
            return this;
        }

        public Builder withBatchId(UUID batchId)
        {
            this.batchId = batchId;
            return this;
        }

        public Builder withStatus(Status status)
        {
            this.status = status;
            return this;
        }

        public Builder withOperation(String operation)
        {
            this.operation = operation;
            return this;
        }

        public Builder withNakedOperation(String nakedOperation)
        {
            this.nakedOperation = nakedOperation;
            return this;
        }

        public Builder withTimestamp(long timestamp)
        {
            this.timestamp = timestamp;
            return this;
        }

        public StoredAuditRecord build()
        {
            return new StoredAuditRecord(this);
        }
    }
}
