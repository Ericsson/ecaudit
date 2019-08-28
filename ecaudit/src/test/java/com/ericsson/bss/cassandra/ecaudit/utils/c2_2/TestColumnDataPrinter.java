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

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.AsciiType;
import org.apache.cassandra.db.marshal.BooleanType;
import org.apache.cassandra.db.marshal.TimestampType;
import org.apache.cassandra.db.marshal.UTF8Type;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link ColumnDataPrinter} class.
 */
@RunWith(JUnitParamsRunner.class)
public class TestColumnDataPrinter
{
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0);

    @Test
    @Parameters(method = "testToCQLLiteral_parameters")
    public void testToCQLLiteral(AbstractType type, ByteBuffer value, String expected)
    {
        // Given
        ColumnSpecification col = new ColumnSpecification("ks", "cf", null, type);
        // When
        String literal = ColumnDataPrinter.toCQLLiteral(value, col);
        // Then
        assertThat(literal).isEqualTo(expected);
    }

    public Object[][] testToCQLLiteral_parameters()
    {
        return new Object[][]{
            { UTF8Type.instance, UTF8Type.instance.fromString("Kalle"), "'Kalle'" },
            { AsciiType.instance, AsciiType.instance.fromString("Anka"), "'Anka'" },
            { TimestampType.instance, TimestampType.instance.fromTimeInMillis(42), "1970-01-01T00:00:00.042Z" }, // 42 ms after EPOCH
            { BooleanType.instance, BooleanType.instance.fromString("True"), "true" },
            { UTF8Type.instance, EMPTY_BUFFER, "''" },
            { TimestampType.instance, EMPTY_BUFFER, null },
        };
    }
}
