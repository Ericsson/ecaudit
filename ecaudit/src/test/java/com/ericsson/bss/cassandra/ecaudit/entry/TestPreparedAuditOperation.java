/*
 * Copyright 2018 Telefonaktiebolaget LM Ericsson
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.ColumnSuppressor;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.ShowAllSuppressor;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestPreparedAuditOperation
{
    private static final ColumnSuppressor OBFUSCATOR = new ShowAllSuppressor();
    @Mock
    private QueryOptions mockOptions;
    @Mock
    private ColumnSuppressor mockObfuscator;

    @Test
    public void testThatValuesAreBound()
    {
        String preparedStatement = "select value1, value2 from ks.cf where pk = ? and ck = ?";
        String expectedStatement = "select value1, value2 from ks.cf where pk = ? and ck = ?['text1', 'text2']";

        List<ByteBuffer> values = createValues("text1", "text2");
        ImmutableList<ColumnSpecification> columns = createTextColumns("col1", "col2");

        when(mockOptions.hasColumnSpecifications()).thenReturn(true);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.getValues()).thenReturn(values);

        PreparedAuditOperation auditOperation;
        auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions, OBFUSCATOR);

        assertThat(auditOperation.getOperationString()).isEqualTo(expectedStatement);
        assertThat(auditOperation.getNakedOperationString()).isEqualTo(preparedStatement);
    }

    @Test
    public void testThatValuesAreBoundWithFixedValues()
    {
        String preparedStatement = "select value1, value2 from ks.cf where pk = ? and ck = 'text'";
        String expectedStatement = "select value1, value2 from ks.cf where pk = ? and ck = 'text'['text1']";

        List<ByteBuffer> values = createValues("text1");
        ImmutableList<ColumnSpecification> columns = createTextColumns("col1");

        when(mockOptions.hasColumnSpecifications()).thenReturn(true);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.getValues()).thenReturn(values);

        PreparedAuditOperation auditOperation;
        auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions, OBFUSCATOR);

        assertThat(auditOperation.getOperationString()).isEqualTo(expectedStatement);
        assertThat(auditOperation.getNakedOperationString()).isEqualTo(preparedStatement);
    }

    @Test
    public void testThatAdditionalValuesAreLogged()
    {
        String preparedStatement = "select value1, value2 from ks.cf where pk = ? and ck = ?";
        String expectedStatement = "select value1, value2 from ks.cf where pk = ? and ck = ?['text', 'text', 'unexpected value']";

        List<ByteBuffer> values = createValues("text", "text", "unexpected value");
        ImmutableList<ColumnSpecification> columns = createTextColumns("text", "text", "unexpected value");

        when(mockOptions.hasColumnSpecifications()).thenReturn(true);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.getValues()).thenReturn(values);

        PreparedAuditOperation auditOperation;
        auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions, OBFUSCATOR);

        assertThat(auditOperation.getOperationString()).isEqualTo(expectedStatement);
        assertThat(auditOperation.getNakedOperationString()).isEqualTo(preparedStatement);
    }

    @Test
    public void testFallbackWhenColumnSpecIsMissing()
    {
        String preparedStatement = "select value1, value2 from ks.cf where pk = ? and ck = ?";

        when(mockOptions.hasColumnSpecifications()).thenReturn(false);

        PreparedAuditOperation auditOperation;
        auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions, OBFUSCATOR);

        assertThat(auditOperation.getOperationString()).isEqualTo(preparedStatement);
        assertThat(auditOperation.getNakedOperationString()).isEqualTo(preparedStatement);

        verify(mockOptions, times(1)).hasColumnSpecifications();
        verify(mockOptions, times(0)).getColumnSpecifications();
    }

    @Test
    public void testObfuscator()
    {
        String preparedStatement = "insert into ks1.t1 (k1, k2, k3) values (?, ?, ?)";
        String expectedStatement = "insert into ks1.t1 (k1, k2, k3) values (?, ?, ?)[<ob1>, 'text2', <ob3>]";

        List<ByteBuffer> values = createValues("text1", "text2", "text3");
        ImmutableList<ColumnSpecification> columns = createTextColumns("col1", "col2", "col3");

        when(mockOptions.hasColumnSpecifications()).thenReturn(true);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.getValues()).thenReturn(values);

        when(mockObfuscator.suppress(eq(columns.get(0)), eq(values.get(0)))).thenReturn(Optional.of("<ob1>"));
        when(mockObfuscator.suppress(eq(columns.get(1)), eq(values.get(1)))).thenReturn(Optional.empty());
        when(mockObfuscator.suppress(eq(columns.get(2)), eq(values.get(2)))).thenReturn(Optional.of("<ob3>"));

        PreparedAuditOperation auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions, mockObfuscator);

        assertThat(auditOperation.getOperationString()).isEqualTo(expectedStatement);
    }

    private List<ByteBuffer> createValues(String... values)
    {
        List<ByteBuffer> rawValues = new ArrayList<>();

        for (String value : values)
        {
            ByteBuffer rawValue = ByteBuffer.wrap(value.getBytes());
            rawValues.add(rawValue);
        }

        return rawValues;
    }

    private ImmutableList<ColumnSpecification> createTextColumns(String... columns)
    {
        ImmutableList.Builder<ColumnSpecification> builder = ImmutableList.builder();

        for (String column : columns)
        {
            ColumnIdentifier id = new ColumnIdentifier(ByteBuffer.wrap(column.getBytes()), UTF8Type.instance);
            ColumnSpecification columnSpecification = new ColumnSpecification("ks", "cf", id, UTF8Type.instance);
            builder.add(columnSpecification);
        }

        return builder.build();
    }
}
