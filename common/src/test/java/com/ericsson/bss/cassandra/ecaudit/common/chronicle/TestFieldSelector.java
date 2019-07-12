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

import java.util.Collections;

import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.FieldSelector.Field;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests the {@link FieldSelector} class.
 */
public class TestFieldSelector
{
    @Test
    public void testFieldBitOrder()
    {
        assertThat(Field.CLIENT_IP.getBit()).isEqualTo(1);
        assertThat(Field.CLIENT_PORT.getBit()).isEqualTo(2);
        assertThat(Field.COORDINATOR_IP.getBit()).isEqualTo(4);
        assertThat(Field.USER.getBit()).isEqualTo(8);
        assertThat(Field.BATCH_ID.getBit()).isEqualTo(16);
        assertThat(Field.STATUS.getBit()).isEqualTo(32);
        assertThat(Field.OPERATION.getBit()).isEqualTo(64);
        assertThat(Field.OPERATION_NAKED.getBit()).isEqualTo(128);
        assertThat(Field.TIMESTAMP.getBit()).isEqualTo(256);
    }

    @Test
    public void testSomeFieldsSelected()
    {
        FieldSelector fields = FieldSelector.fromFields(asList("CLIENT_IP", "STATUS"));

        assertThat(fields.isSelected(Field.CLIENT_IP)).isTrue();
        assertThat(fields.isSelected(Field.CLIENT_PORT)).isFalse();
        assertThat(fields.isSelected(Field.COORDINATOR_IP)).isFalse();
        assertThat(fields.isSelected(Field.USER)).isFalse();
        assertThat(fields.isSelected(Field.BATCH_ID)).isFalse();
        assertThat(fields.isSelected(Field.STATUS)).isTrue();
        assertThat(fields.isSelected(Field.OPERATION)).isFalse();
        assertThat(fields.isSelected(Field.OPERATION_NAKED)).isFalse();
        assertThat(fields.isSelected(Field.TIMESTAMP)).isFalse();

        assertThat(fields.getBitmap()).isEqualTo(33);
    }

    @Test
    public void testNoFieldsSelected()
    {
        FieldSelector fields = FieldSelector.fromFields(Collections.emptyList());
        assertThat(fields.isSelected(Field.CLIENT_IP)).isFalse();
        assertThat(fields.isSelected(Field.CLIENT_PORT)).isFalse();
        assertThat(fields.isSelected(Field.COORDINATOR_IP)).isFalse();
        assertThat(fields.isSelected(Field.USER)).isFalse();
        assertThat(fields.isSelected(Field.BATCH_ID)).isFalse();
        assertThat(fields.isSelected(Field.STATUS)).isFalse();
        assertThat(fields.isSelected(Field.OPERATION)).isFalse();
        assertThat(fields.isSelected(Field.OPERATION_NAKED)).isFalse();
        assertThat(fields.isSelected(Field.TIMESTAMP)).isFalse();

        assertThat(fields.getBitmap()).isEqualTo(0)
                                      .isEqualTo(FieldSelector.NO_FIELDS.getBitmap());
    }

    @Test
    public void testAllFieldsSelected()
    {
        FieldSelector fields = FieldSelector.fromFields(asList("TIMESTAMP", "CLIENT_IP", "CLIENT_PORT", "COORDINATOR_IP", "USER", "BATCH_ID", "STATUS", "OPERATION", "OPERATION_NAKED"));

        assertThat(fields.isSelected(Field.CLIENT_IP)).isTrue();
        assertThat(fields.isSelected(Field.CLIENT_PORT)).isTrue();
        assertThat(fields.isSelected(Field.COORDINATOR_IP)).isTrue();
        assertThat(fields.isSelected(Field.USER)).isTrue();
        assertThat(fields.isSelected(Field.BATCH_ID)).isTrue();
        assertThat(fields.isSelected(Field.STATUS)).isTrue();
        assertThat(fields.isSelected(Field.OPERATION)).isTrue();
        assertThat(fields.isSelected(Field.OPERATION_NAKED)).isTrue();
        assertThat(fields.isSelected(Field.TIMESTAMP)).isTrue();

        assertThat(fields.getBitmap()).isEqualTo(511)
                                      .isEqualTo(FieldSelector.ALL_FIELDS.getBitmap());
    }

    @Test
    public void testDefaultFieldsSelected()
    {
        FieldSelector fields = FieldSelector.fromFields(asList("TIMESTAMP", "CLIENT_IP", "CLIENT_PORT", "COORDINATOR_IP", "USER", "BATCH_ID", "STATUS", "OPERATION"));

        assertThat(fields.isSelected(Field.CLIENT_IP)).isTrue();
        assertThat(fields.isSelected(Field.CLIENT_PORT)).isTrue();
        assertThat(fields.isSelected(Field.COORDINATOR_IP)).isTrue();
        assertThat(fields.isSelected(Field.USER)).isTrue();
        assertThat(fields.isSelected(Field.BATCH_ID)).isTrue();
        assertThat(fields.isSelected(Field.STATUS)).isTrue();
        assertThat(fields.isSelected(Field.OPERATION)).isTrue();
        assertThat(fields.isSelected(Field.OPERATION_NAKED)).isFalse();
        assertThat(fields.isSelected(Field.TIMESTAMP)).isTrue();

        assertThat(fields.getBitmap()).isEqualTo(383)
                                      .isEqualTo(FieldSelector.DEFAULT_FIELDS.getBitmap());
    }

    @Test
    public void testThatDuplicateFieldsOnlyCountedOnce()
    {
        FieldSelector fields = FieldSelector.fromFields(asList("CLIENT_IP", "CLIENT_IP"));
        assertThat(fields.getBitmap()).isEqualTo(1);
    }

    @Test
    public void testLowerCaseFieldNames()
    {
        FieldSelector fields = FieldSelector.fromFields(asList("Client_Ip"));
        assertThat(fields.getBitmap()).isEqualTo(1);
    }

    @Test
    public void testWithoutField()
    {
        FieldSelector fields = FieldSelector.fromFields(asList("CLIENT_IP", "CLIENT_PORT", "STATUS"));

        FieldSelector fieldsWithoutPort = fields.withoutField(Field.CLIENT_PORT);
        FieldSelector fieldsWithoutPort2 = fieldsWithoutPort.withoutField(Field.CLIENT_PORT);

        assertThat(fields.getBitmap()).isEqualTo(35);
        assertThat(fieldsWithoutPort.getBitmap()).isEqualTo(33);
        assertThat(fieldsWithoutPort2.getBitmap()).isEqualTo(33);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFieldName()
    {
        FieldSelector.fromFields(asList("Non existing field"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBitmapLowerRange()
    {
        FieldSelector.fromBitmap(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBitmapUpperRange()
    {
        FieldSelector.fromBitmap(512); // Only 9 fields available == Max 9 bits => max bitmap value 2^9 - 1
    }

    @Test
    public void testFromBitmap()
    {
        FieldSelector fields = FieldSelector.fromBitmap(42);
        assertThat(fields.getBitmap()).isEqualTo(42);
    }

    @Test
    public void testThatNoMoreThan31FieldsShouldBeAdded()
    {
        assertThat(Field.values().length)
        .as("Refactor needed if more than 31 fields added.")
        .isLessThan(32);
    }

    @Test
    public void testIfPresentRun()
    {
        FieldSelector fields = FieldSelector.fromFields(asList("STATUS"));
        Runnable runMock1 = mock(Runnable.class);
        Runnable runMock2 = mock(Runnable.class);

        fields.ifSelectedRun(Field.STATUS, runMock1);
        fields.ifSelectedRun(Field.CLIENT_IP, runMock2);

        verify(runMock1).run();
        verify(runMock2, never()).run();
    }
}
