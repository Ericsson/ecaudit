//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.entry.PreparedAuditOperation;
import com.google.common.collect.ImmutableList;

@RunWith(MockitoJUnitRunner.class)
public class TestPreparedAuditOperation
{
    @Mock
    QueryOptions mockOptions;

    @Test
    public void testThatValuesAreBound()
    {
        String preparedStatement = "select value1, value2 from ks.cf where pk = ? and ck = ?";
        String expectedStatement = "select value1, value2 from ks.cf where pk = ? and ck = ?['text', 'text']";

        List<ByteBuffer> values = createValues("text", "text");
        ImmutableList<ColumnSpecification> columns = createTextColumns("text", "text");

        when(mockOptions.hasColumnSpecifications()).thenReturn(true);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.getValues()).thenReturn(values);

        PreparedAuditOperation auditOperation;
        auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions);

        assertThat(auditOperation.getOperationString()).isEqualTo(expectedStatement);
    }

    @Test
    public void testThatValuesAreBoundWithFixedValues()
    {
        String preparedStatement = "select value1, value2 from ks.cf where pk = ? and ck = 'text'";
        String expectedStatement = "select value1, value2 from ks.cf where pk = ? and ck = 'text'['text']";

        List<ByteBuffer> values = createValues("text");
        ImmutableList<ColumnSpecification> columns = createTextColumns("text");

        when(mockOptions.hasColumnSpecifications()).thenReturn(true);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.getValues()).thenReturn(values);

        PreparedAuditOperation auditOperation;
        auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions);

        assertThat(auditOperation.getOperationString()).isEqualTo(expectedStatement);
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
        auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions);

        assertThat(auditOperation.getOperationString()).isEqualTo(expectedStatement);
    }

    @Test
    public void testFallbackWhenColumnSpecIsMissing()
    {
        String preparedStatement = "select value1, value2 from ks.cf where pk = ? and ck = ?";

        when(mockOptions.hasColumnSpecifications()).thenReturn(false);
        when(mockOptions.getColumnSpecifications()).thenThrow(new UnsupportedOperationException());

        PreparedAuditOperation auditOperation;
        auditOperation = new PreparedAuditOperation(preparedStatement, mockOptions);

        assertThat(auditOperation.getOperationString()).isEqualTo(preparedStatement);

        verify(mockOptions, times(1)).hasColumnSpecifications();
        verify(mockOptions, times(0)).getColumnSpecifications();
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
