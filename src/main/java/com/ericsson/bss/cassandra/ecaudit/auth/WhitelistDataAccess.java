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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;

import org.apache.cassandra.auth.AuthKeyspace;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.cql3.statements.DeleteStatement;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.MigrationManager;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.utils.ByteBufferUtil;

/**
 * This DAO provides an interface for updating and retrieving role specific audit white-lists.
 */
public class WhitelistDataAccess
{
    private static final String DEFAULT_SUPERUSER_NAME = "cassandra";

    private DeleteStatement deleteWhitelistStatement;
    private SelectStatement loadWhitelistStatement;

    public void setup()
    {
        maybeCreateTable();

        loadWhitelistStatement = (SelectStatement) prepare(
                "SELECT operation, resources from %s.%s WHERE role = ?",
                AuthKeyspace.NAME,
                AuditAuthKeyspace.WHITELIST_TABLE_NAME);

        deleteWhitelistStatement = (DeleteStatement) prepare(
                "DELETE FROM %s.%s WHERE role = ?",
                AuthKeyspace.NAME,
                AuditAuthKeyspace.WHITELIST_TABLE_NAME);
    }

    public void addToWhitelist(RoleResource role, String whitelistOperation, Set<IResource> whitelistResources)
    {
        updateWhitelist(role, whitelistOperation, whitelistResources,
                        "UPDATE %s.%s SET resources = resources + {%s} WHERE role = '%s' AND operation = '%s'");
    }

    public void removeFromWhitelist(RoleResource role, String whitelistOperation, Set<IResource> whitelistResources)
    {
        updateWhitelist(role, whitelistOperation, whitelistResources,
                        "UPDATE %s.%s SET resources = resources - {%s} WHERE role = '%s' AND operation = '%s'");
    }

    private void updateWhitelist(RoleResource role, String whitelistOperation, Set<IResource> whitelistResources, String statementTemplate)
    {
        List<String> quotedWhitelistResources = whitelistResources
                                                .stream()
                                                .map(IResource::getName)
                                                .map(r -> "'" + r + "'")
                                                .collect(Collectors.toList());

        String statement = String.format(
        statementTemplate,
        AuthKeyspace.NAME,
        AuditAuthKeyspace.WHITELIST_TABLE_NAME,
        StringUtils.join(quotedWhitelistResources, ','),
        escape(role.getRoleName()),
        whitelistOperation);

        QueryProcessor.process(statement, consistencyForRole(role));
    }

    public Map<String, Set<IResource>> getWhitelist(RoleResource role)
    {
        ResultMessage.Rows rows = loadWhitelistStatement.execute(
                QueryState.forInternalCalls(),
                QueryOptions.forInternalCalls(
                        consistencyForRole(role),
                        Arrays.asList(ByteBufferUtil.bytes(role.getRoleName()))));

        if (rows.result.isEmpty())
        {
            return Collections.emptyMap();
        }

        return StreamSupport
               .stream(UntypedResultSet.create(rows.result).spliterator(), false)
               .collect(Collectors.toMap(this::extractOperation,
                                         this::extractResourceSet));
    }

    private String extractOperation(UntypedResultSet.Row untypedRow)
    {
        return untypedRow.getString("operation");
    }

    private Set<IResource> extractResourceSet(UntypedResultSet.Row untypedRow)
    {
        Set<String> resourceStrings = untypedRow.getSet("resources", UTF8Type.instance);
        return ResourceFactory.toResourceSet(resourceStrings);
    }

    public void deleteWhitelist(RoleResource role)
    {
        deleteWhitelistStatement.execute(
                QueryState.forInternalCalls(),
                QueryOptions.forInternalCalls(
                        consistencyForRole(role),
                        Collections.singletonList(ByteBufferUtil.bytes(role.getRoleName()))));
    }

    private static synchronized void maybeCreateTable()
    {
        KeyspaceMetadata expected = AuditAuthKeyspace.metadata();
        KeyspaceMetadata defined = Schema.instance.getKSMetaData(expected.name);

        for (CFMetaData expectedTable : expected.tables)
        {
            CFMetaData definedTable = defined.tables.get(expectedTable.cfName).orElse(null);
            if (definedTable == null || !definedTable.equals(expectedTable))
            {
                MigrationManager.forceAnnounceNewColumnFamily(expectedTable);
            }
        }
    }

    // Stolen from CassandraRoleManager
    private static String escape(String name)
    {
        return StringUtils.replace(name, "'", "''");
    }

    // Stolen from CassandraRoleManager
    private static CQLStatement prepare(String template, String keyspace, String table)
    {
        try
        {
            return QueryProcessor.parseStatement(String.format(template, keyspace, table)).prepare(ClientState.forInternalCalls()).statement;
        }
        catch (RequestValidationException e)
        {
            throw new AssertionError(e); // not supposed to happen
        }
    }

    // Stolen from CassandraRoleManager
    private static ConsistencyLevel consistencyForRole(RoleResource role)
    {
        String roleName =  role.getRoleName();
        if (roleName.equals(DEFAULT_SUPERUSER_NAME))
        {
            return ConsistencyLevel.QUORUM;
        }
        else
        {
            return ConsistencyLevel.LOCAL_ONE;
        }
    }
}
