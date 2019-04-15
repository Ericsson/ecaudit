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

public class SimpleAuditRecord implements AuditRecord
{
    private final InetAddress clientAddress;
    private final String user;
    private final UUID batchId;
    private final Status status;
    private final AuditOperation operation;
    private final long timestamp;

    private SimpleAuditRecord(Builder builder)
    {
        this.clientAddress = builder.clientAddress;
        this.user = builder.user;
        this.batchId = builder.batchId;
        this.status = builder.status;
        this.operation = builder.operation;
        this.timestamp = builder.timestamp;
    }

    @Override
    public Long getTimestamp()
    {
        return timestamp;
    }

    @Override
    public InetAddress getClientAddress()
    {
        return clientAddress;
    }

    @Override
    public String getUser()
    {
        return user;
    }

    @Override
    public Optional<UUID> getBatchId()
    {
        return Optional.ofNullable(batchId);
    }

    @Override
    public Status getStatus()
    {
        return status;
    }

    @Override
    public AuditOperation getOperation()
    {
        return operation;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private InetAddress clientAddress;
        private String user;
        private UUID batchId;
        private Status status;
        private AuditOperation operation;
        private long timestamp;

        public Builder withClientAddress(InetAddress clientAddress)
        {
            this.clientAddress = clientAddress;
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

        public Builder withOperation(AuditOperation operation)
        {
            this.operation = operation;
            return this;
        }

        public Builder withTimestamp(long timestamp)
        {
            this.timestamp = timestamp;
            return this;
        }

        public AuditRecord build()
        {
            return new SimpleAuditRecord(this);
        }
    }
}
