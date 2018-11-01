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
package com.ericsson.bss.cassandra.ecaudit.entry.factory;

import java.util.List;

import org.apache.commons.lang3.reflect.FieldUtils;

import com.ericsson.bss.cassandra.ecaudit.facade.CassandraAuditException;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.FunctionResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.cql3.CFName;
import org.apache.cassandra.cql3.CQL3Type;
import org.apache.cassandra.cql3.functions.FunctionName;
import org.apache.cassandra.cql3.statements.AlterViewStatement;
import org.apache.cassandra.cql3.statements.AuthenticationStatement;
import org.apache.cassandra.cql3.statements.AuthorizationStatement;
import org.apache.cassandra.cql3.statements.CreateAggregateStatement;
import org.apache.cassandra.cql3.statements.CreateFunctionStatement;
import org.apache.cassandra.cql3.statements.CreateViewStatement;
import org.apache.cassandra.cql3.statements.DropAggregateStatement;
import org.apache.cassandra.cql3.statements.DropFunctionStatement;
import org.apache.cassandra.cql3.statements.DropViewStatement;
import org.apache.cassandra.cql3.statements.PermissionsManagementStatement;
import org.apache.cassandra.cql3.statements.UseStatement;
import org.apache.cassandra.db.view.View;

public class StatementResourceAdapter
{
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
            throw new CassandraAuditException("Failed to resolve resource", e);
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
            throw new CassandraAuditException("Failed to resolve resource", e);
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
            throw new CassandraAuditException("Failed to resolve resource", e);
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
            throw new CassandraAuditException("Failed to resolve resource", e);
        }
    }

    DataResource resolveBaseTableResource(CreateViewStatement statement)
    {
        try
        {
            CFName baseName  = (CFName) FieldUtils.readField(statement, "baseName", true);
            return DataResource.table(statement.keyspace(), baseName.getColumnFamily());
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of view " + statement.keyspace() + "." + statement.columnFamily(), e);
        }
    }

    DataResource resolveBaseTableResource(AlterViewStatement statement)
    {
        CFMetaData baseTable = View.findBaseTable(statement.keyspace(), statement.columnFamily());
        return DataResource.table(statement.keyspace(), baseTable.cfName);
    }

    DataResource resolveBaseTableResource(DropViewStatement statement)
    {
        CFMetaData baseTable = View.findBaseTable(statement.keyspace(), statement.columnFamily());
        return DataResource.table(statement.keyspace(), baseTable.cfName);
    }

    FunctionResource resolveFunctionKeyspaceResource(CreateFunctionStatement statement)
    {
        try
        {
            FunctionName functionName = (FunctionName)  FieldUtils.readField(statement, "functionName", true);
            return FunctionResource.keyspace(functionName.keyspace);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base keyspace of function" + statement.keyspace() + "." + statement.columnFamily(), e);
        }
    }

    FunctionResource resolveFunctionResource(DropFunctionStatement statement)
    {
        try
        {
            FunctionName functionName = (FunctionName)  FieldUtils.readField(statement, "functionName", true);
            List<CQL3Type.Raw> argRawTypes = (List<CQL3Type.Raw>) FieldUtils.readField(statement, "argRawTypes", true);
            return FunctionResource.functionFromCql(functionName.keyspace, functionName.name, argRawTypes);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of function " + statement.keyspace() + "." + statement.columnFamily(), e);
        }
    }

    FunctionResource resolveAggregateKeyspaceResource(CreateAggregateStatement statement)
    {
        try
        {
            FunctionName functionName = (FunctionName)  FieldUtils.readField(statement, "functionName", true);
            return FunctionResource.keyspace(functionName.keyspace);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base keyspace of aggregate" + statement.keyspace() + "." + statement.columnFamily(), e);
        }
    }

    FunctionResource resolveAggregateResource(DropAggregateStatement statement)
    {
        try
        {
            FunctionName functionName = (FunctionName)  FieldUtils.readField(statement, "functionName", true);
            List<CQL3Type.Raw> argRawTypes = (List<CQL3Type.Raw>) FieldUtils.readField(statement, "argRawTypes", true);
            return FunctionResource.functionFromCql(functionName.keyspace, functionName.name, argRawTypes);
        }
        catch (IllegalAccessException e)
        {
            throw new CassandraAuditException("Failed to resolve base table of function " + statement.keyspace() + "." + statement.columnFamily(), e);
        }
    }
}
