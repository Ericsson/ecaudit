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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.cql3.statements.DeleteStatement;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.cql3.statements.UpdateStatement;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.marshal.SetType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.Keyspaces.KeyspacesDiff;
import org.apache.cassandra.schema.Schema;
import org.apache.cassandra.schema.SchemaConstants;
import org.apache.cassandra.schema.SchemaTransformation.SchemaTransformationResult;
import org.apache.cassandra.schema.SchemaTransformations;
import org.apache.cassandra.serializers.SetSerializer;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.utils.ByteBufferUtil;

/**
 * This DAO provides an interface for updating and retrieving role specific audit white-lists.
 */
public class WhitelistDataAccess
{
    private static final Logger LOG = LoggerFactory.getLogger(WhitelistDataAccess.class);

    private static final long SCHEMA_ALIGNMENT_DELAY_MS = Long.getLong("ecaudit.schema_alignment_delay_ms", 120_000L);
    private static final SetSerializer<String> SET_SERIALIZER = SetType.getInstance(UTF8Type.instance, true).getSerializer();

    private boolean setupCompleted = false;

    private static final String DEFAULT_SUPERUSER_NAME = "cassandra";

    // Step the schema version if schema is updated
	private static final long ECAUDIT_SCHEMA_VERSION = 1;

    private DeleteStatement deleteWhitelistStatement;
    private SelectStatement loadWhitelistStatement;
    private UpdateStatement addToWhitelistStatement;
    private UpdateStatement removeFromWhitelistStatement;

    private WhitelistDataAccess()
    {
    }

    public static WhitelistDataAccess getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder
    {
        private static final WhitelistDataAccess INSTANCE = new WhitelistDataAccess();
    }

    public synchronized void setup()
    {
        if (setupCompleted)
        {
            return;
        }

        maybeCreateTable();

        loadWhitelistStatement = (SelectStatement) prepare(
                "SELECT resource, operations from %s.%s WHERE role = ?",
                SchemaConstants.AUTH_KEYSPACE_NAME,
                AuditAuthKeyspace.WHITELIST_TABLE_NAME_V2);

        deleteWhitelistStatement = (DeleteStatement) prepare(
                "DELETE FROM %s.%s WHERE role = ?",
                SchemaConstants.AUTH_KEYSPACE_NAME,
                AuditAuthKeyspace.WHITELIST_TABLE_NAME_V2);

        addToWhitelistStatement = (UpdateStatement) prepare(
                "UPDATE %s.%s SET operations = operations + ? WHERE role = ? AND resource = ?",
                SchemaConstants.AUTH_KEYSPACE_NAME,
                AuditAuthKeyspace.WHITELIST_TABLE_NAME_V2);

        removeFromWhitelistStatement = (UpdateStatement) prepare(
                "UPDATE %s.%s SET operations = operations - ? WHERE role = ? AND resource = ?",
                SchemaConstants.AUTH_KEYSPACE_NAME,
                AuditAuthKeyspace.WHITELIST_TABLE_NAME_V2);

        setupCompleted = true;
    }

    void addToWhitelist(RoleResource role, IResource whitelistResource, Set<Permission> whitelistOperations)
    {
        List<ByteBuffer> values = getSerializedUpdateValues(role.getRoleName(), whitelistResource.getName(), whitelistOperations);

        addToWhitelistStatement.execute(QueryState.forInternalCalls(),
                                        QueryOptions.forInternalCalls(consistencyForRole(role),
                                                                      values),
                                        System.nanoTime());
    }

    static List<ByteBuffer> getSerializedUpdateValues(String role, String resource, Set<Permission> whitelistOperations)
    {
        Set<String> operations = whitelistOperations.stream()
                                                    .map(Enum::name)
                                                    .collect(Collectors.toSet());

        List<ByteBuffer> values = new ArrayList<>(3);
        values.add(SET_SERIALIZER.serialize(operations));
        values.add(ByteBufferUtil.bytes(role));
        values.add(ByteBufferUtil.bytes(resource));
        return values;
    }

    void removeFromWhitelist(RoleResource role, IResource whitelistResource, Set<Permission> whitelistOperations)
    {
        List<ByteBuffer> values = getSerializedUpdateValues(role.getRoleName(), whitelistResource.getName(), whitelistOperations);

        removeFromWhitelistStatement.execute(QueryState.forInternalCalls(),
                                             QueryOptions.forInternalCalls(consistencyForRole(role),
                                                                           values),
                                             System.nanoTime());
    }

    public Map<IResource, Set<Permission>> getWhitelist(RoleResource role)
    {
        ResultMessage.Rows rows = loadWhitelistStatement.execute(
                QueryState.forInternalCalls(),
                QueryOptions.forInternalCalls(
                        consistencyForRole(role),
                        Collections.singletonList(ByteBufferUtil.bytes(role.getRoleName()))),
                System.nanoTime());

        if (rows.result.isEmpty())
        {
            return Collections.emptyMap();
        }

        return StreamSupport
               .stream(UntypedResultSet.create(rows.result).spliterator(), false)
               .filter(this::isValidEntry)
               .collect(Collectors.toMap(this::extractResource,
                                         this::extractOperationSet));
    }

    private boolean isValidEntry(UntypedResultSet.Row untypedRow)
    {
        try
        {
            extractResource(untypedRow);
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }

        try
        {
            extractOperationSet(untypedRow);
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }

        return true;
    }

    private IResource extractResource(UntypedResultSet.Row untypedRow)
    {
        String resourceName = untypedRow.getString("resource");
        return ResourceFactory.toResource(resourceName);
    }

    private Set<Permission> extractOperationSet(UntypedResultSet.Row untypedRow)
    {
        Set<String> operationNames = untypedRow.getSet("operations", UTF8Type.instance);
        return OperationFactory.toOperationSet(operationNames);
    }

    void deleteWhitelist(RoleResource role)
    {
        deleteWhitelistStatement.execute(
                QueryState.forInternalCalls(),
                QueryOptions.forInternalCalls(
                        consistencyForRole(role),
                        Collections.singletonList(ByteBufferUtil.bytes(role.getRoleName()))),
                System.nanoTime());
    }

    private synchronized void maybeCreateTable()
    {
        KeyspaceMetadata expected = AuditAuthKeyspace.metadata();

        SchemaTransformationResult result = Schema.instance.transform(SchemaTransformations.updateSystemKeyspace(expected, ECAUDIT_SCHEMA_VERSION));

        if (result.diff != KeyspacesDiff.NONE)
        {
            SchemaHelper schemaHelper = new SchemaHelper();
            if (!schemaHelper.areSchemasAligned(SCHEMA_ALIGNMENT_DELAY_MS))
            {
                LOG.warn("Schema alignment timeout - continuing startup");
            }
        }
    }

    private CQLStatement prepare(String template, String keyspace, String table)
    {
        try
        {
            return QueryProcessor.parseStatement(String.format(template, keyspace, table)).prepare(ClientState.forInternalCalls());
        }
        catch (RequestValidationException e)
        {
            throw new AssertionError(e);
        }
    }

    private ConsistencyLevel consistencyForRole(RoleResource role)
    {
        String roleName = role.getRoleName();
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
