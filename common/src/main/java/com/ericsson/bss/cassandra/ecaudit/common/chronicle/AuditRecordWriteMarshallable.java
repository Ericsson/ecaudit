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

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import net.openhft.chronicle.wire.WireOut;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.jetbrains.annotations.NotNull;

public class AuditRecordWriteMarshallable implements WriteMarshallable
{
    private final AuditRecord auditRecord;

    public AuditRecordWriteMarshallable(AuditRecord auditRecord)
    {
        this.auditRecord = auditRecord;
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wire)
    {
        boolean batchRecord = auditRecord.getBatchId().isPresent();
        String type = batchRecord ? WireTags.VALUE_TYPE_BATCH_ENTRY : WireTags.VALUE_TYPE_SINGLE_ENTRY;

        wire.write(WireTags.KEY_VERSION).int16(WireTags.VALUE_VERSION_CURRENT);
        wire.write(WireTags.KEY_TYPE).text(type);
        wire.write(WireTags.KEY_TIMESTAMP).int64(auditRecord.getTimestamp());
        wire.write(WireTags.KEY_CLIENT).bytes(auditRecord.getClientAddress().getAddress());
        wire.write(WireTags.KEY_COORDINATOR).bytes(auditRecord.getCoordinatorAddress().getAddress());
        wire.write(WireTags.KEY_USER).text(auditRecord.getUser());
        if (batchRecord)
        {
            wire.write(WireTags.KEY_BATCH_ID).uuid(auditRecord.getBatchId().get());
        }
        wire.write(WireTags.KEY_STATUS).text(auditRecord.getStatus().name());
        wire.write(WireTags.KEY_OPERATION).text(auditRecord.getOperation().getOperationString());
    }
}
