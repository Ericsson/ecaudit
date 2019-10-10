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
package com.ericsson.bss.cassandra.ecaudit.entry.suppressor;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.BytesType;
import org.apache.cassandra.db.marshal.ListType;
import org.apache.cassandra.db.marshal.MapType;
import org.apache.cassandra.db.marshal.SetType;
import org.apache.cassandra.db.marshal.TupleType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the {@link SuppressBlobs} class.
 */
@RunWith(JUnitParamsRunner.class)
public class TestSuppressBlobs
{
    private static final ColumnSpecification BLOB_COLUMN = createColumn(BytesType.instance);
    private static final ColumnSpecification BLOB_LIST_COLUMN = createColumn(listOf(BytesType.instance));
    private static final ColumnSpecification BLOB_LIST_LIST_COLUMN = createColumn(listOf(listOf(BytesType.instance)));
    private static final ColumnSpecification BLOB_SET_COLUMN = createColumn(setOf(BytesType.instance));
    private static final ColumnSpecification BLOB_TEXT_MAP_COLUMN = createColumn(mapOf(BytesType.instance, UTF8Type.instance));
    private static final ColumnSpecification TEXT_BLOB_MAP_COLUMN = createColumn(mapOf(UTF8Type.instance, BytesType.instance));
    private static final ColumnSpecification TEXT_BLOB_TUPLE_COLUMN = createColumn(tupleOf(UTF8Type.instance, BytesType.instance));

    private static final ColumnSpecification TEXT_COLUMN = createColumn(UTF8Type.instance);
    private static final ColumnSpecification TEXT_LIST_COLUMN = createColumn(listOf(UTF8Type.instance));
    private static final ColumnSpecification TEXT_SET_COLUMN = createColumn(setOf(UTF8Type.instance));
    private static final ColumnSpecification TEXT_MAP_COLUMN = createColumn(mapOf(UTF8Type.instance, UTF8Type.instance));
    private static final ColumnSpecification TEXT_TUPLE_COLUMN = createColumn(tupleOf(UTF8Type.instance, UTF8Type.instance));

    @Test
    @Parameters(method = "testBlobsAreHidden_parameters")
    public void testBlobsAreHidden(ColumnSpecification column, String expectedString)
    {
        // Given
        ByteBuffer valueMock = Mockito.mock(ByteBuffer.class);
        BoundValueSuppressor suppressor = new SuppressBlobs();
        // When
        Optional<String> result = suppressor.suppress(column, valueMock);
        // Then
        assertThat(result).contains(expectedString);
        verifyZeroInteractions(valueMock);
    }

    public Object[][] testBlobsAreHidden_parameters()
    {
        return new Object[][]{
        { BLOB_COLUMN, "<blob>" },
        { BLOB_LIST_COLUMN, "<list<blob>>" },
        { BLOB_LIST_LIST_COLUMN, "<list<list<blob>>>" },
        { BLOB_SET_COLUMN, "<set<blob>>" },
        { BLOB_TEXT_MAP_COLUMN, "<map<blob, text>>" },
        { TEXT_BLOB_MAP_COLUMN, "<map<text, blob>>" },
        { TEXT_BLOB_TUPLE_COLUMN, "<tuple<text, blob>>" },
        };
    }

    @Test
    @Parameters(method = "testNonBlobsColumns_parameters")
    public void testNonBlobsColumns(ColumnSpecification column)
    {
        // Given
        ByteBuffer valueMock = Mockito.mock(ByteBuffer.class);
        BoundValueSuppressor suppressor = new SuppressBlobs();
        // When
        Optional<String> result = suppressor.suppress(column, valueMock);
        // Then
        assertThat(result).isEmpty();
        verifyZeroInteractions(valueMock);
    }

    public Object[][] testNonBlobsColumns_parameters()
    {
        return new Object[][]{
        { TEXT_COLUMN },
        { TEXT_LIST_COLUMN },
        { TEXT_SET_COLUMN },
        { TEXT_MAP_COLUMN },
        { TEXT_TUPLE_COLUMN },
        };
    }

    private static ColumnSpecification createColumn(AbstractType type)
    {
        return new ColumnSpecification("ks", "cf", null, type);
    }

    private static <T> ListType<T> listOf(AbstractType<T> type)
    {
        return ListType.getInstance(type, true);
    }

    private static <T> SetType<T> setOf(AbstractType<T> type)
    {
        return SetType.getInstance(type, true);
    }

    private static <K, V> MapType<K, V> mapOf(AbstractType<K> keyType, AbstractType<V> valueType)
    {
        return MapType.getInstance(keyType, valueType, true);
    }

    private static TupleType tupleOf(AbstractType<?> ...types)
    {
        return new TupleType(Arrays.asList(types));
    }
}
