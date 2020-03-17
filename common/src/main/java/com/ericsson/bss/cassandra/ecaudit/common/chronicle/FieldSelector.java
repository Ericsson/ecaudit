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

import java.util.List;

public final class FieldSelector
{
    /**
     * Default fields selected: TIMESTAMP, CLIENT_IP, CLIENT_PORT, COORDINATOR_IP, USER, BATCH_ID, STATUS, OPERATION
     */
    public static final FieldSelector DEFAULT_FIELDS = FieldSelector.fromBitmap(383);
    public static final FieldSelector NO_FIELDS = FieldSelector.fromBitmap(0);
    public static final FieldSelector ALL_FIELDS = FieldSelector.fromBitmap((1 << FieldSelector.Field.values().length) - 1);

    public enum Field
    {
        CLIENT_IP(1),
        CLIENT_PORT(1 << 1),
        COORDINATOR_IP(1 << 2),
        USER(1 << 3),
        BATCH_ID(1 << 4),
        STATUS(1 << 5),
        OPERATION(1 << 6),
        OPERATION_NAKED(1 << 7),
        TIMESTAMP(1 << 8),
        SUBJECT(1 << 9);

        private final int bit;

        Field(int bit)
        {
            this.bit = bit;
        }

        int getBit()
        {
            return bit;
        }
    }

    private final int bitmap;

    private FieldSelector(int bitmap)
    {
        this.bitmap = bitmap;
    }

    /**
     * @return the bitmap representation of this field selector
     */
    public int getBitmap()
    {
        return bitmap;
    }

    /**
     * Execute the provided runnable if the provided field is selected.
     *
     * @param field    the field to check
     * @param runnable the runnable to execute
     */
    public void ifSelectedRun(Field field, Runnable runnable)
    {
        if (isSelected(field))
        {
            runnable.run();
        }
    }

    /**
     * @param field the field to check
     * @return {@code true} if the given field is selected, otherwise {@code false}
     */
    public boolean isSelected(Field field)
    {
        return (bitmap & field.getBit()) > 0;
    }

    /**
     * Toggle a field as selected
     * @param field the field to select
     * @return a new field selector with the field selected
     */
    public FieldSelector withField(Field field)
    {
        return new FieldSelector(bitmap | field.getBit());
    }

    /**
     * Copies this field selector, but without the provided field selected.
     *
     * @param field the field to un-select
     * @return a new field selector without the provided field selected
     */
    public FieldSelector withoutField(Field field)
    {
        return new FieldSelector(bitmap & ~field.getBit());
    }

    /**
     * Creates a field selector from the provided bitmap.
     *
     * @param bitmap the bitmap representing the selected fields
     * @return a new field selector
     * @throws IllegalArgumentException if the bitmap is out of range
     */
    public static FieldSelector fromBitmap(int bitmap)
    {
        if (bitmap < 0 || bitmap >= (1 << Field.values().length))
        {
            throw new IllegalArgumentException("Bitmap value is out of bounds");
        }
        return new FieldSelector(bitmap);
    }

    /**
     * Creates a field selector from the provided list of fields.
     * The provided fields name must match (case insensitive) a {@link Field} constant.
     * Duplicates will be ignored.
     *
     * @param fields the selected fields, case insensitive.
     * @return a new field selector
     * @throws IllegalArgumentException if a field name is invalid
     */
    public static FieldSelector fromFields(List<String> fields)
    {
        int bitmap = fields.stream()
                           .map(String::toUpperCase)
                           .map(Field::valueOf)
                           .mapToInt(Field::getBit)
                           .distinct()
                           .sum();
        return new FieldSelector(bitmap);
    }
}
