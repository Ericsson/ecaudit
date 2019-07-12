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
import java.net.UnknownHostException;
import java.util.UUID;

import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.StoredAuditRecord;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link SimpleAuditRecord} class.
 */
public class TestSimpleAuditOperation
{
    @Test
    public void testEmptyRecord()
    {
        StoredAuditRecord record = StoredAuditRecord.builder().build();

        assertThat(record.getClientAddress()).isEmpty();
        assertThat(record.getClientPort()).isEmpty();
        assertThat(record.getCoordinatorAddress()).isEmpty();
        assertThat(record.getUser()).isEmpty();
        assertThat(record.getBatchId()).isEmpty();
        assertThat(record.getStatus()).isEmpty();
        assertThat(record.getOperation()).isEmpty();
        assertThat(record.getNakedOperation()).isEmpty();
        assertThat(record.getTimestamp()).isEmpty();
    }

    @Test
    public void testFullRecord() throws UnknownHostException
    {
        StoredAuditRecord record = StoredAuditRecord.builder()
                                                    .withClientAddress(InetAddress.getByName("1.2.3.4"))
                                                    .withClientPort(42)
                                                    .withCoordinatorAddress(InetAddress.getByName("5.6.7.8"))
                                                    .withUser("Bob")
                                                    .withBatchId(UUID.fromString("4910e9a6-9d26-40f8-ad8c-5c0436784999"))
                                                    .withStatus(Status.SUCCEEDED)
                                                    .withOperation("insert into user (name) values (?)")
                                                    .withNakedOperation("insert into user (name) values (?)['Bob']")
                                                    .withTimestamp(123456789L)
                                                    .build();

        assertThat(record.getClientAddress()).contains(InetAddress.getByName("1.2.3.4"));
        assertThat(record.getClientPort()).contains(42);
        assertThat(record.getCoordinatorAddress()).contains(InetAddress.getByName("5.6.7.8"));
        assertThat(record.getUser()).contains("Bob");
        assertThat(record.getBatchId()).contains(UUID.fromString("4910e9a6-9d26-40f8-ad8c-5c0436784999"));
        assertThat(record.getStatus()).contains(Status.SUCCEEDED);
        assertThat(record.getOperation()).contains("insert into user (name) values (?)");
        assertThat(record.getNakedOperation()).contains("insert into user (name) values (?)['Bob']");
        assertThat(record.getTimestamp()).contains(123456789L);
    }
}
