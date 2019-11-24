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
package com.ericsson.bss.cassandra.ecaudit.entry;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.cassandra.cql3.CQL3Type;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.AsciiType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.serializers.TimestampSerializer;

/**
 * This logic is extracted to a flavor specific adapter.
 *
 * ecAudit comes in different flavors, one for each supported Cassandra version.
 * Flavor adapters encapsulates differences between flavors and simplifies maintenance.
 */
final class CqlLiteralFlavorAdapter
{
    private static final DateFormat DATE_FORMAT = createDateFormat();

    private CqlLiteralFlavorAdapter()
    {
        // Utility class
    }

    static String toCQLLiteral(ByteBuffer serializedValue, ColumnSpecification column)
    {
        if (!serializedValue.hasRemaining())
        {
            return noValueString(column);
        }
        if (column.type.asCQL3Type() instanceof CQL3Type.Native)
        {
            switch ((CQL3Type.Native) column.type.asCQL3Type())
            {
                case ASCII:
                case VARCHAR:
                case TEXT:
                    return "'" + column.type.getString(serializedValue) + "'";
                case TIMESTAMP:
                    return DATE_FORMAT.format(TimestampSerializer.instance.deserialize(serializedValue));
                case BLOB:
                    return "0x" + column.type.getString(serializedValue);
                default:
                    break;
            }
        }
        return column.type.getString(serializedValue);
    }

    private static String noValueString(ColumnSpecification column)
    {
        return isStringType(column) ? "''" : "null";
    }

    private static boolean isStringType(ColumnSpecification column)
    {
        return column.type instanceof UTF8Type || column.type instanceof AsciiType;
    }

    private static DateFormat createDateFormat()
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault(Locale.Category.FORMAT));
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat;
    }
}
