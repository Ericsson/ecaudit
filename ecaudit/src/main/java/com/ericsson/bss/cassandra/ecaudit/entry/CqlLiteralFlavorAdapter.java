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

import org.apache.cassandra.cql3.ColumnSpecification;

/**
 * This logic is extracted to a flavor specific adapter.
 *
 * ecAudit comes in different flavors, one for each supported Cassandra version.
 * Flavor adapters encapsulates differences between flavors and simplifies maintenance.
 */
final class CqlLiteralFlavorAdapter
{
    private CqlLiteralFlavorAdapter()
    {
        // Utility class
    }

    static String toCQLLiteral(ByteBuffer serializedValue, ColumnSpecification column)
    {
        return column.type.asCQL3Type().toCQLLiteral(serializedValue);
    }
}
