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

import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditRecord;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditRecord;

import static org.assertj.core.api.Assertions.assertThat;

public class TestFieldFilterFlavorAdapter
{
    @Test
    public void testGetFieldsAvailableInRecord()
    {
        AuditRecord recordWithoutClientIPAndBatchId = SimpleAuditRecord.builder().build();

        FieldSelector fields = FieldFilterFlavorAdapter.getFieldsAvailableInRecord(recordWithoutClientIPAndBatchId, FieldSelector.ALL_FIELDS);

        assertThat(fields.isSelected(FieldSelector.Field.CLIENT_IP)).isFalse();
        assertThat(fields.isSelected(FieldSelector.Field.CLIENT_PORT)).isFalse();
        assertThat(fields.isSelected(FieldSelector.Field.BATCH_ID)).isFalse();
    }
}
