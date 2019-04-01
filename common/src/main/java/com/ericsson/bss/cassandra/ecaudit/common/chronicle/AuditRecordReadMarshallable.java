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
package com.ericsson.bss.cassandra.ecaudit.common.chronicle;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.wire.ReadMarshallable;
import net.openhft.chronicle.wire.WireIn;
import org.jetbrains.annotations.NotNull;

public class AuditRecordReadMarshallable implements ReadMarshallable
{
    private AuditRecord auditRecord;

    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IORuntimeException
    {
        if (auditRecord != null)
        {
            throw new IORuntimeException("Tried to read from wire with used marshallable");
        }

        short version = wire.read(WireTags.KEY_VERSION).int16();

        if (version != WireTags.VALUE_VERSION_CURRENT)
        {
            throw new IORuntimeException("Unsupported record version: " + version);
        }

        String type = wire.read(WireTags.KEY_TYPE).text();

        SimpleAuditRecord.Builder builder = SimpleAuditRecord.builder();

        builder.withTimestamp(wire.read(WireTags.KEY_TIMESTAMP).int64());

        try
        {
            builder.withClientAddress(InetAddress.getByAddress(wire.read(WireTags.KEY_CLIENT).bytes()));
        }
        catch (UnknownHostException e)
        {
            throw new IORuntimeException("Corrupt client IP address field", e);
        }

        builder.withUser(wire.read(WireTags.KEY_USER).text());

        if (WireTags.VALUE_TYPE_BATCH_ENTRY.equals(type))
        {
            builder.withBatchId(wire.read(WireTags.KEY_BATCH_ID).uuid());
        }
        else if (!WireTags.VALUE_TYPE_SINGLE_ENTRY.equals(type))
        {
            throw new IORuntimeException("Corrupt record type field: " + type);
        }

        try
        {
            builder.withStatus(Status.valueOf(wire.read(WireTags.KEY_STATUS).text()));
        }
        catch (IllegalArgumentException e)
        {
            throw new IORuntimeException("Corrupt record status field", e);
        }

        builder.withOperation(new SimpleAuditOperation(wire.read(WireTags.KEY_OPERATION).text()));

        auditRecord = builder.build();
    }

    public AuditRecord getAuditRecord()
    {
        if (auditRecord == null)
        {
            throw new IllegalStateException("No record has been red from the wire");
        }

        return auditRecord;
    }
}
