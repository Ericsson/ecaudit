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
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.apache.cassandra.auth.AuthKeyspace;
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
                "SELECT resources from %s.%s WHERE role = ? AND operation = ?",
                AuthKeyspace.NAME,
                AuditAuthKeyspace.ROLE_AUDIT_WHITELISTS);

        deleteWhitelistStatement = (DeleteStatement) prepare(
                "DELETE FROM %s.%s WHERE role = ?",
                AuthKeyspace.NAME,
                AuditAuthKeyspace.ROLE_AUDIT_WHITELISTS);
    }

    public void addToWhitelist(String rolename, String whitelistOperation, Set<String> whitelistResources)
    {
        updateWhitelist(rolename, whitelistOperation, whitelistResources,
                "UPDATE %s.%s SET resources = resources + {%s} WHERE role = '%s' AND operation = '%s'");
    }

    public void removeFromWhitelist(String rolename, String whitelistOperation, Set<String> whitelistResources)
    {
        updateWhitelist(rolename, whitelistOperation, whitelistResources,
                "UPDATE %s.%s SET resources = resources - {%s} WHERE role = '%s' AND operation = '%s'");
    }

    private void updateWhitelist(String rolename, String whitelistOperation, Set<String> whitelistResources,
            String statementTemplate)
    {
        List<String> quotedWhitelistResources = whitelistResources
                .stream()
                .map(r -> "'" + r + "'")
                .collect(Collectors.toList());

        String statement = String.format(
                statementTemplate,
                AuthKeyspace.NAME,
                AuditAuthKeyspace.ROLE_AUDIT_WHITELISTS,
                StringUtils.join(quotedWhitelistResources, ','),
                escape(rolename),
                whitelistOperation);

        QueryProcessor.process(statement, consistencyForRole(rolename));
    }

    public Set<String> getWhitelist(String rolename, String whitelistOperation)
    {
        ResultMessage.Rows rows = loadWhitelistStatement.execute(
                QueryState.forInternalCalls(),
                QueryOptions.forInternalCalls(
                        consistencyForRole(rolename),
                        Arrays.asList(ByteBufferUtil.bytes(rolename), ByteBufferUtil.bytes(whitelistOperation))));

        if (rows.result.isEmpty())
        {
            return Collections.emptySet();
        }

        UntypedResultSet.Row untypedRow = UntypedResultSet.create(rows.result).one();
        return untypedRow.getSet("resources", UTF8Type.instance);

    }

    public void deleteWhitelist(String rolename)
    {
        deleteWhitelistStatement.execute(
                QueryState.forInternalCalls(),
                QueryOptions.forInternalCalls(
                        consistencyForRole(rolename),
                        Arrays.asList(ByteBufferUtil.bytes(rolename))));
    }

    private static void maybeCreateTable()
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
    private static ConsistencyLevel consistencyForRole(String role)
    {
        if (role.equals(DEFAULT_SUPERUSER_NAME))
        {
            return ConsistencyLevel.QUORUM;
        }
        else
        {
            return ConsistencyLevel.LOCAL_ONE;
        }
    }
}
