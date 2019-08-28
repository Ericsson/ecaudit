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
package com.ericsson.bss.cassandra.ecaudit.utils.c2_2;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.AsciiType;
import org.apache.cassandra.db.marshal.TimestampType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.serializers.TimestampSerializer;

/**
 * This class is only needed in the c2.2 branch.
 * Contains logic for printing column data, which is missing in C* 2.2
 */
public final class ColumnDataPrinter
{
    private static final DateFormat DATE_FORMAT = createDateFormat();

    private ColumnDataPrinter()
    {
        // Utility class
    }

    public static String toCQLLiteral(ByteBuffer serializedValue, ColumnSpecification column)
    {
        String value = null;
        if (column.type instanceof UTF8Type || column.type instanceof AsciiType)
        {
            value = "'" + column.type.getString(serializedValue) + "'";
        }
        else
        {
            if (serializedValue.hasRemaining())
            {
                if (column.type instanceof TimestampType)
                {
                    value = DATE_FORMAT.format(TimestampSerializer.instance.deserialize(serializedValue));
                }
                else
                {
                    value = column.type.getString(serializedValue);
                }
            }
        }
        return value;
    }

    private static DateFormat createDateFormat()
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault(Locale.Category.FORMAT));
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return  dateFormat;
    }
}
