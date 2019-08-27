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
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.common.chronicle.FieldSelector.Field;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests the {@link FieldSelector} class.
 */
public class TestFieldSelector
{
    private static final Field[] ALL_FIELDS = Field.values();

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

        assertOnlyGivenFieldsAreSelected(fields, Field.CLIENT_IP, Field.STATUS);

        assertThat(fields.getBitmap()).isEqualTo(33);

    }

    @Test
    public void testNoFieldsSelected()
    {
        FieldSelector fields = FieldSelector.fromFields(Collections.emptyList());

        assertNoFieldsAreSelected(fields);

        assertThat(fields.getBitmap()).isEqualTo(0)
                                      .isEqualTo(FieldSelector.NO_FIELDS.getBitmap());
    }

    @Test
    public void testAllFieldsSelected()
    {
        List<String> allFieldNames = Stream.of(ALL_FIELDS).map(Field::name).collect(toList());

        FieldSelector fields = FieldSelector.fromFields(allFieldNames);

        assertAllFieldsAreSelected(fields);

        assertThat(fields.getBitmap()).isEqualTo(511)
                                      .isEqualTo(FieldSelector.ALL_FIELDS.getBitmap());
    }

    @Test
    public void testDefaultFieldsSelected()
    {
        FieldSelector fields = FieldSelector.fromFields(asList("TIMESTAMP", "CLIENT_IP", "CLIENT_PORT", "COORDINATOR_IP", "USER", "BATCH_ID", "STATUS", "OPERATION"));

        assertOnlyGivenFieldsAreSelected(fields, Field.TIMESTAMP, Field.CLIENT_IP, Field.CLIENT_PORT, Field.COORDINATOR_IP, Field.USER, Field.BATCH_ID, Field.STATUS, Field.OPERATION);

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
    public void testMixedCaseFieldNames()
    {
        FieldSelector fields = FieldSelector.fromFields(singletonList("Client_Ip"));
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

    @Test
    public void testInvalidFieldName()
    {
        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> FieldSelector.fromFields(singletonList("KALLE")))
        .withMessageContaining("No enum")
        .withMessageContaining("KALLE");
    }

    @Test
    public void testInvalidBitmapLowerRange()
    {
        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> FieldSelector.fromBitmap(-1))
        .withMessageContaining("Bitmap value is out of bounds");
    }

    @Test
    public void testInvalidBitmapUpperRange()
    {
        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> FieldSelector.fromBitmap(512)) // Only 9 fields available == Max 9 bits => max bitmap value 2^9 - 1
        .withMessageContaining("Bitmap value is out of bounds");
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
        FieldSelector fields = FieldSelector.fromFields(singletonList("STATUS"));
        Runnable runMock1 = mock(Runnable.class);
        Runnable runMock2 = mock(Runnable.class);

        fields.ifSelectedRun(Field.STATUS, runMock1);
        fields.ifSelectedRun(Field.CLIENT_IP, runMock2);

        verify(runMock1).run();
        verify(runMock2, never()).run();
    }

    private void assertOnlyGivenFieldsAreSelected(FieldSelector fieldSelector, Field... fields)
    {
        List<Field> expectedFields = asList(fields);
        for (Field field : ALL_FIELDS)
        {
            boolean selected = fieldSelector.isSelected(field);
            boolean expected = expectedFields.contains(field);
            assertThat(selected).as("Field [%s] selected", field).isEqualTo(expected);
        }
    }

    private void assertNoFieldsAreSelected(FieldSelector fields)
    {
        assertOnlyGivenFieldsAreSelected(fields);
    }

    private void assertAllFieldsAreSelected(FieldSelector fields)
    {
        assertOnlyGivenFieldsAreSelected(fields, ALL_FIELDS);
    }
}
