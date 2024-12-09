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
package com.ericsson.bss.cassandra.ecaudit.entry.factory; //NOPMD

import java.util.List;

import org.apache.commons.lang3.reflect.FieldUtils;

import com.ericsson.bss.cassandra.ecaudit.facade.CassandraAuditException;

import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.FunctionResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.cql3.CQL3Type;
import org.apache.cassandra.cql3.QualifiedName;
import org.apache.cassandra.cql3.functions.FunctionName;
import org.apache.cassandra.cql3.statements.AuthenticationStatement;
import org.apache.cassandra.cql3.statements.AuthorizationStatement;
import org.apache.cassandra.cql3.statements.PermissionsManagementStatement;
import org.apache.cassandra.cql3.statements.UseStatement;
import org.apache.cassandra.cql3.statements.schema.AlterViewStatement;
import org.apache.cassandra.cql3.statements.schema.CreateAggregateStatement;
import org.apache.cassandra.cql3.statements.schema.CreateFunctionStatement;
import org.apache.cassandra.cql3.statements.schema.CreateIndexStatement;
import org.apache.cassandra.cql3.statements.schema.CreateViewStatement;
import org.apache.cassandra.cql3.statements.schema.DropAggregateStatement;
import org.apache.cassandra.cql3.statements.schema.DropFunctionStatement;
import org.apache.cassandra.cql3.statements.schema.DropIndexStatement;
import org.apache.cassandra.cql3.statements.schema.DropViewStatement;
import org.apache.cassandra.db.view.View;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.Schema;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.schema.TableMetadataRef;

class StatementResourceAdapter //NOPMD
{
    private static final String UNCHECKED = "unchecked";
    private static final String ARGUMENTS = "arguments";
    private static final String FAILED_TO_RESOLVE_BASE_TABLE_OF_FUNCTION = "Failed to resolve base table of function";
    private static final String NAME = "name";
    private static final String TABLE_NAME = "tableName";
    private static final String FAILED_TO_RESOLVE_RESOURCE = "Failed to resolve resource";

    public static final String FUNCTION_NAME = "functionName";

    /**
     * Extract the {@link RoleResource} from the {@link AuthenticationStatement}.
     * <p>
     * The abstract type {@link AuthenticationStatement} itself does not contain any member named "role".
     * But all known implementation does have a member with correct name and type.
     *
     * @param statement the statement
     * @return the RoleResource
     */
    RoleResource resolveRoleResource(AuthenticationStatement statement)
    {
        try
        {
            return (RoleResource) FieldUtils.readField(statement, "role", true);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_RESOURCE, e);
        }
    }

    IResource resolveManagedResource(PermissionsManagementStatement statement)
    {
        try
        {
            return (IResource) FieldUtils.readField(statement, "resource", true);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_RESOURCE, e);
        }
    }

    RoleResource resolveGranteeResource(AuthorizationStatement statement)
    {
        try
        {
            RoleResource resource = (RoleResource) FieldUtils.readField(statement, "grantee", true);
            if (resource == null)
            {
                resource = RoleResource.root();
            }
            return resource;
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_RESOURCE, e);
        }
    }

    DataResource resolveKeyspaceResource(UseStatement statement)
    {
        try
        {
            String keyspace = (String) FieldUtils.readField(statement, "keyspace", true);
            return DataResource.keyspace(keyspace);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_RESOURCE, e);
        }
    }

    DataResource resolveBaseTableResource(CreateViewStatement statement)
    {
        try
        {
            String baseName  = (String) FieldUtils.readField(statement, TABLE_NAME, true);
            return DataResource.table(statement.getAuditLogContext().keyspace, baseName);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of view " + statement.getAuditLogContext().keyspace + "." + statement.getAuditLogContext().scope, e);
        }
    }

    DataResource resolveBaseTableResource(CreateViewStatement.Raw statement)
    {
        try
        {
            QualifiedName baseName  = (QualifiedName) FieldUtils.readField(statement, TABLE_NAME, true);
            return DataResource.table(baseName.getKeyspace(), baseName.getName());
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of view", e);
        }
    }

    DataResource resolveBaseTableResource(AlterViewStatement statement)
    {
        TableMetadataRef baseTable = View.findBaseTable(statement.getAuditLogContext().keyspace, statement.getAuditLogContext().scope);
        if (baseTable == null)
        {
            return DataResource.keyspace(statement.getAuditLogContext().keyspace);
        }
        else
        {
            return DataResource.table(statement.getAuditLogContext().keyspace, baseTable.name);
        }
    }

    DataResource resolveBaseTableResource(AlterViewStatement.Raw statement)
    {
        try
        {
            QualifiedName name  = (QualifiedName) FieldUtils.readField(statement, NAME, true);
            TableMetadataRef baseTable = View.findBaseTable(name.getKeyspace(), name.getName());
            if (baseTable == null)
            {
                return DataResource.keyspace(name.getName());
            }
            else
            {
                return DataResource.table(name.getKeyspace(), baseTable.name);
            }
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of view", e);
        }
    }

    DataResource resolveBaseTableResource(DropViewStatement statement)
    {
        TableMetadataRef baseTable = View.findBaseTable(statement.getAuditLogContext().keyspace, statement.getAuditLogContext().scope);
        if (baseTable == null)
        {
            return DataResource.keyspace(statement.getAuditLogContext().keyspace);
        }
        else
        {
            return DataResource.table(statement.getAuditLogContext().keyspace, baseTable.name);
        }
    }

    DataResource resolveBaseTableResource(DropViewStatement.Raw statement)
    {
        try
        {
            QualifiedName name  = (QualifiedName) FieldUtils.readField(statement, NAME, true);
            TableMetadataRef baseTable = View.findBaseTable(name.getKeyspace(), name.getName());
            if (baseTable == null)
            {
                return DataResource.keyspace(name.getName());
            }
            else
            {
                return DataResource.table(name.getKeyspace(), baseTable.name);
            }
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of view", e);
        }
    }

    DataResource resolveBaseTableResource(CreateIndexStatement statement)
    {
        try
        {
             String baseTable = (String) FieldUtils.readField(statement, TABLE_NAME, true);

             return DataResource.table(statement.getAuditLogContext().keyspace, baseTable);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_RESOURCE, e);
        }
    }

    DataResource resolveBaseTableResource(CreateIndexStatement.Raw statement)
    {
        try
        {
            QualifiedName baseTable = (QualifiedName) FieldUtils.readField(statement, TABLE_NAME, true);

            return DataResource.table(baseTable.getKeyspace(), baseTable.getName());
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_RESOURCE, e);
        }
    }

    DataResource resolveBaseTableResource(DropIndexStatement statement)
    {
        KeyspaceMetadata keyspace = Schema.instance.getKeyspaceMetadata(statement.getAuditLogContext().keyspace);

        TableMetadata baseTable = null == keyspace
                ? null
                : keyspace.findIndexedTable(statement.getAuditLogContext().scope).orElse(null);

        if (baseTable == null)
        {
            return DataResource.keyspace(statement.getAuditLogContext().keyspace);
        }
        else
        {
            return DataResource.table(statement.getAuditLogContext().keyspace, baseTable.name);
        }
    }

    DataResource resolveBaseTableResource(DropIndexStatement.Raw statement)
    {
        try
        {
            QualifiedName name = (QualifiedName) FieldUtils.readField(statement, NAME, true);

            KeyspaceMetadata keyspace = Schema.instance.getKeyspaceMetadata(name.getKeyspace());

            TableMetadata baseTable = null == keyspace
                    ? null
                    : keyspace.findIndexedTable(name.getName()).orElse(null);

            if (baseTable == null)
            {
                return DataResource.keyspace(name.getKeyspace());
            }
            else
            {
                return DataResource.table(name.getKeyspace(), baseTable.name);
            }
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_RESOURCE, e);
        }
    }

    FunctionResource resolveFunctionKeyspaceResource(CreateFunctionStatement statement)
    {
        return FunctionResource.keyspace(statement.getAuditLogContext().keyspace);
    }

    FunctionResource resolveFunctionKeyspaceResource(CreateFunctionStatement.Raw statement)
    {
        try
        {
            FunctionName name  = (FunctionName) FieldUtils.readField(statement, NAME, true);
            return FunctionResource.keyspace(name.keyspace);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_BASE_TABLE_OF_FUNCTION, e);
        }
    }

    FunctionResource resolveFunctionResource(DropFunctionStatement statement)
    {
        try
        {
            @SuppressWarnings(UNCHECKED)
            List<CQL3Type.Raw> argRawTypes = (List<CQL3Type.Raw>) FieldUtils.readField(statement, ARGUMENTS, true);
            return FunctionResource.functionFromCql(statement.getAuditLogContext().keyspace, statement.getAuditLogContext().scope, argRawTypes);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of function " + statement.getAuditLogContext().keyspace + "." + statement.getAuditLogContext().scope, e);
        }
    }

    FunctionResource resolveFunctionResource(DropFunctionStatement.Raw statement)
    {
        try
        {
            FunctionName name  = (FunctionName) FieldUtils.readField(statement, NAME, true);
            @SuppressWarnings(UNCHECKED)
            List<CQL3Type.Raw> argRawTypes = (List<CQL3Type.Raw>) FieldUtils.readField(statement, ARGUMENTS, true);
            return FunctionResource.functionFromCql(name.keyspace, name.name, argRawTypes);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_BASE_TABLE_OF_FUNCTION, e);
        }
    }

    FunctionResource resolveAggregateKeyspaceResource(CreateAggregateStatement statement)
    {
        return FunctionResource.keyspace(statement.getAuditLogContext().keyspace);
    }

    FunctionResource resolveAggregateKeyspaceResource(CreateAggregateStatement.Raw statement)
    {
        try
        {
            FunctionName name  = (FunctionName) FieldUtils.readField(statement, NAME, true);
            return FunctionResource.keyspace(name.keyspace);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_BASE_TABLE_OF_FUNCTION, e);
        }
    }

    FunctionResource resolveAggregateResource(DropAggregateStatement statement)
    {
        try
        {
            @SuppressWarnings(UNCHECKED)
            List<CQL3Type.Raw> argRawTypes = (List<CQL3Type.Raw>) FieldUtils.readField(statement, ARGUMENTS, true);
            return FunctionResource.functionFromCql(statement.getAuditLogContext().keyspace, statement.getAuditLogContext().scope, argRawTypes);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of function " + statement.getAuditLogContext().keyspace + "." + statement.getAuditLogContext().scope, e);
        }
    }

    FunctionResource resolveAggregateResource(DropAggregateStatement.Raw statement)
    {
        try
        {
            FunctionName name  = (FunctionName) FieldUtils.readField(statement, NAME, true);
            @SuppressWarnings(UNCHECKED)
            List<CQL3Type.Raw> argRawTypes = (List<CQL3Type.Raw>) FieldUtils.readField(statement, ARGUMENTS, true);
            return FunctionResource.functionFromCql(name.keyspace, name.name, argRawTypes);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException(FAILED_TO_RESOLVE_BASE_TABLE_OF_FUNCTION, e);
        }
    }
}
