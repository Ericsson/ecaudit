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
import java.util.UUID;

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.FieldSelector.Field;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.common.record.StoredAuditRecord;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.wire.ReadMarshallable;
import net.openhft.chronicle.wire.WireIn;
import org.jetbrains.annotations.NotNull;

public class AuditRecordReadMarshallable implements ReadMarshallable
{
    private StoredAuditRecord auditRecord;

    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IORuntimeException
    {
        if (auditRecord != null)
        {
            throw new IORuntimeException("Tried to read from wire with used marshallable");
        }

        short version = wire.read(WireTags.KEY_VERSION).int16();
        switch (version)
        {
            case WireTags.VALUE_VERSION_0:
                auditRecord = readV0(wire);
                break;
            case WireTags.VALUE_VERSION_1:
                auditRecord = readV1(wire);
                break;
            case WireTags.VALUE_VERSION_2:
                auditRecord = readV2(wire);
                break;
            default:
                throw new IORuntimeException("Unsupported record version: " + version);
        }
    }

    private StoredAuditRecord readV0(WireIn wire)
    {
        String type = readV0Type(wire);

        StoredAuditRecord.Builder builder = StoredAuditRecord
                                            .builder()
                                            .withTimestamp(wire.read(WireTags.KEY_TIMESTAMP).int64())
                                            .withClientAddress(readInetAddress(wire, WireTags.KEY_CLIENT_IP))
                                            .withClientPort(wire.read(WireTags.KEY_CLIENT_PORT).int32())
                                            .withCoordinatorAddress(readInetAddress(wire, WireTags.KEY_COORDINATOR_IP))
                                            .withUser(wire.read(WireTags.KEY_USER).text());

        if (WireTags.VALUE_TYPE_BATCH_ENTRY.equals(type))
        {
            builder.withBatchId(readBatchId(wire));
        }

        return builder.withStatus(readStatus(wire))
                      .withOperation(wire.read(WireTags.KEY_OPERATION).text())
                      .build();
    }

    private StoredAuditRecord readV1(WireIn wire)
    {
        checkV1Type(wire);
        int bitmap = wire.read(WireTags.KEY_FIELDS).int32();

        FieldSelector fieldsV1 = FieldSelector.fromBitmap(bitmap);
        return readV1WithFields(wire, fieldsV1).build();
    }

    private StoredAuditRecord readV2(WireIn wire)
    {
        checkV1Type(wire);
        int bitmap = wire.read(WireTags.KEY_FIELDS).int32();

        FieldSelector fieldsV2 = FieldSelector.fromBitmap(bitmap);

        StoredAuditRecord.Builder recordBuilder = readV1WithFields(wire, fieldsV2);
        fieldsV2.ifSelectedRun(Field.SUBJECT, () -> recordBuilder.withSubject(wire.read(WireTags.KEY_SUBJECT).text()));

        return recordBuilder.build();
    }

    private StoredAuditRecord.Builder readV1WithFields(WireIn wire, FieldSelector selectedFields)
    {
        StoredAuditRecord.Builder recordBuilder = StoredAuditRecord.builder();

        // Read configurable fields
        selectedFields.ifSelectedRun(Field.TIMESTAMP, () -> recordBuilder.withTimestamp(wire.read(WireTags.KEY_TIMESTAMP).int64()));
        selectedFields.ifSelectedRun(Field.CLIENT_IP, () -> recordBuilder.withClientAddress(readInetAddress(wire, WireTags.KEY_CLIENT_IP)));
        selectedFields.ifSelectedRun(Field.CLIENT_PORT, () -> recordBuilder.withClientPort(wire.read(WireTags.KEY_CLIENT_PORT).int32()));
        selectedFields.ifSelectedRun(Field.COORDINATOR_IP, () -> recordBuilder.withCoordinatorAddress(readInetAddress(wire, WireTags.KEY_COORDINATOR_IP)));
        selectedFields.ifSelectedRun(Field.USER, () -> recordBuilder.withUser(wire.read(WireTags.KEY_USER).text()));
        selectedFields.ifSelectedRun(Field.BATCH_ID, () -> recordBuilder.withBatchId(readBatchId(wire)));
        selectedFields.ifSelectedRun(Field.STATUS, () -> recordBuilder.withStatus(readStatus(wire)));
        selectedFields.ifSelectedRun(Field.OPERATION, () -> recordBuilder.withOperation(wire.read(WireTags.KEY_OPERATION).text()));
        selectedFields.ifSelectedRun(Field.OPERATION_NAKED, () -> recordBuilder.withNakedOperation(wire.read(WireTags.KEY_NAKED_OPERATION).text()));

        return recordBuilder;
    }

    private String readV0Type(WireIn wire) throws IORuntimeException
    {
        String type = wire.read(WireTags.KEY_TYPE).text();
        if (!WireTags.VALUE_TYPE_BATCH_ENTRY.equals(type) && !WireTags.VALUE_TYPE_SINGLE_ENTRY.equals(type))
        {
            throw new IORuntimeException("Unsupported record type field: " + type);
        }

        return type;
    }

    private void checkV1Type(WireIn wire) throws IORuntimeException
    {
        String type = wire.read(WireTags.KEY_TYPE).text();
        if (!WireTags.VALUE_TYPE_AUDIT.equals(type))
        {
            throw new IORuntimeException("Unsupported record type field: " + type);
        }
    }

    private InetAddress readInetAddress(WireIn wire, String key) throws IORuntimeException
    {
        try
        {
            return InetAddress.getByAddress(wire.read(key).bytes());
        }
        catch (UnknownHostException e)
        {
            throw new IORuntimeException("Corrupt " + key + " field", e);
        }
    }

    private UUID readBatchId(WireIn wire) throws IORuntimeException
    {
        return wire.read(WireTags.KEY_BATCH_ID).uuid();
    }

    private Status readStatus(WireIn wire) throws IORuntimeException
    {
        try
        {
            return Status.valueOf(wire.read(WireTags.KEY_STATUS).text());
        }
        catch (IllegalArgumentException e)
        {
            throw new IORuntimeException("Corrupt record status field", e);
        }
    }

    public StoredAuditRecord getAuditRecord()
    {
        if (auditRecord == null)
        {
            throw new IllegalStateException("No record has been read from the wire");
        }

        return auditRecord;
    }
}
