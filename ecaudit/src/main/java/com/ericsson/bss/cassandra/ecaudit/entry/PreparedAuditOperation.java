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
import java.util.LinkedList;
import java.util.Queue;

import com.ericsson.bss.cassandra.ecaudit.common.record.AuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.BoundValueSuppressor;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.QueryOptions;

/**
 * Wraps a prepared statement and the {@link QueryOptions} of an operation.
 *
 * This implementation provides lazy binding of parameters to prepared statement until the operation string is requested
 * the first time. The effective operation/statement will be cached and used on subsequent calls to
 * {@link #getOperationString()}.
 *
 * This implementation is not thread safe.
 */
public class PreparedAuditOperation implements AuditOperation
{
    private final String preparedStatement;
    private final QueryOptions options;
    private String effectiveStatement; // lazy initialization
    private final BoundValueSuppressor boundValueSuppressor;

    /**
     * Construct a new prepared audit operation based on the prepared statement and options.
     *
     * @param preparedStatement
     *            the prepared statement
     * @param options
     *            the query options of an operation
     * @param boundValueSuppressor
     *            the suppressor to process bound values
     */
    public PreparedAuditOperation(String preparedStatement, QueryOptions options, BoundValueSuppressor boundValueSuppressor)
    {
        this.preparedStatement = preparedStatement;
        this.options = options;
        this.boundValueSuppressor = boundValueSuppressor;
    }

    @Override
    public String getOperationString()
    {
        if (effectiveStatement == null)
        {
            effectiveStatement = bindValues();
        }

        return effectiveStatement;
    }

    /**
     * Bind marked values in the given prepared statement.
     *
     * @return the resulting query string with bound values
     */
    private String bindValues()
    {
        if (!options.hasColumnSpecifications() || options.getColumnSpecifications().size() == 0)
        {
            return preparedStatement;
        }

        return preparedWithValues();
    }

    private String preparedWithValues()
    {
        StringBuilder fullStatement = new StringBuilder(preparedStatement);

        fullStatement.append('[');

        if (options.getValues().isEmpty())
        {
            fullStatement.append(']');
        }
        else
        {
            Queue<ByteBuffer> values = new LinkedList<>(options.getValues());
            for (ColumnSpecification column : options.getColumnSpecifications())
            {
                ByteBuffer value = values.remove();
                String valueString = boundValueSuppressor.suppress(column, value)
                                                         .orElseGet(() -> CqlLiteralFlavorAdapter.toCQLLiteral(value, column));
                fullStatement.append(valueString).append(", ");
            }

            fullStatement.setLength(fullStatement.length() - 1);
            fullStatement.setCharAt(fullStatement.length() - 1, ']');
        }

        return fullStatement.toString();
    }

    @Override
    public String getNakedOperationString()
    {
        return preparedStatement;
    }
}
