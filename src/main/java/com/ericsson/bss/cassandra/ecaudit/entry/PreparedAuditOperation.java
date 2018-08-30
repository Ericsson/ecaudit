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

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.transport.Server;

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
    private String effectiveStatement;

    /**
     * Construct a new prepared audit operation based on the prepared statement and options.
     *
     * @param preparedStatement
     *            the prepared statement
     * @param options
     *            the query options of an operation
     */
    public PreparedAuditOperation(String preparedStatement, QueryOptions options)
    {
        this.preparedStatement = preparedStatement;
        this.options = options;
        this.effectiveStatement = null;
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

    @Override
    public String toString()
    {
        return "Prepared audit operation: "  + getOperationString();
    }

    /**
     * Bind marked values in the given prepared statement.
     *
     * @return the resulting query string with bound values
     */
    private String bindValues()
    {
        if (!options.hasColumnSpecifications())
        {
            return preparedStatement;
        }

        return preparedWithValues();
    }

    private String preparedWithValues()
    {
        StringBuilder fullStatement = new StringBuilder(preparedStatement);

        fullStatement.append('[');

        Queue<ByteBuffer> values = new LinkedList<>(options.getValues());
        for (ColumnSpecification column : options.getColumnSpecifications())
        {
            String value = column.type.asCQL3Type().toCQLLiteral(values.remove(), Server.CURRENT_VERSION);

            fullStatement.append(value).append(", ");
        }

        fullStatement.setLength(fullStatement.length() - 1);
        fullStatement.setCharAt(fullStatement.length() - 1, ']');

        return fullStatement.toString();
    }
}
