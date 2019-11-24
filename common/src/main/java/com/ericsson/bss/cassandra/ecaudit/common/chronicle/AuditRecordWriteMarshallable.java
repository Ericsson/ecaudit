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

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.FieldSelector.Field;
import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import net.openhft.chronicle.wire.WireOut;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.jetbrains.annotations.NotNull;

public class AuditRecordWriteMarshallable implements WriteMarshallable
{
    private final AuditRecord auditRecord;
    private final FieldSelector actualFields;

    public AuditRecordWriteMarshallable(AuditRecord auditRecord, FieldSelector configuredFields)
    {
        this.auditRecord = auditRecord;
        this.actualFields = FieldFilterFlavorAdapter.getFieldsAvailableInRecord(auditRecord, configuredFields);
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wire)
    {
        // Mandatory fields
        wire.write(WireTags.KEY_VERSION).int16(WireTags.VALUE_VERSION_CURRENT);
        wire.write(WireTags.KEY_TYPE).text(WireTags.VALUE_TYPE_AUDIT);
        wire.write(WireTags.KEY_FIELDS).int32(actualFields.getBitmap());
        // Configurable fields
        actualFields.ifSelectedRun(Field.TIMESTAMP, () -> wire.write(WireTags.KEY_TIMESTAMP).int64(auditRecord.getTimestamp()));
        actualFields.ifSelectedRun(Field.CLIENT_IP, () -> wire.write(WireTags.KEY_CLIENT_IP).bytes(auditRecord.getClientAddress().getAddress().getAddress()));
        actualFields.ifSelectedRun(Field.CLIENT_PORT, () -> wire.write(WireTags.KEY_CLIENT_PORT).int32(auditRecord.getClientAddress().getPort()));
        actualFields.ifSelectedRun(Field.COORDINATOR_IP, () -> wire.write(WireTags.KEY_COORDINATOR_IP).bytes(auditRecord.getCoordinatorAddress().getAddress()));
        actualFields.ifSelectedRun(Field.USER, () -> wire.write(WireTags.KEY_USER).text(auditRecord.getUser()));
        actualFields.ifSelectedRun(Field.BATCH_ID, () -> wire.write(WireTags.KEY_BATCH_ID).uuid(auditRecord.getBatchId().get()));
        actualFields.ifSelectedRun(Field.STATUS, () -> wire.write(WireTags.KEY_STATUS).text(auditRecord.getStatus().name()));
        actualFields.ifSelectedRun(Field.OPERATION, () -> wire.write(WireTags.KEY_OPERATION).text(auditRecord.getOperation().getOperationString()));
        actualFields.ifSelectedRun(Field.OPERATION_NAKED, () -> wire.write(WireTags.KEY_NAKED_OPERATION).text(auditRecord.getOperation().getNakedOperationString()));
    }
}
