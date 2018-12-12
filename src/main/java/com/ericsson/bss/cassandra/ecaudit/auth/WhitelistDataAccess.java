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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.auth.AuthKeyspace;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.concurrent.ScheduledExecutors;
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
import org.apache.cassandra.serializers.SetSerializer;
import org.apache.cassandra.serializers.UTF8Serializer;
import org.apache.cassandra.service.MigrationManager;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.utils.ByteBufferUtil;

/**
 * This DAO provides an interface for updating and retrieving role specific audit white-lists.
 */
public class WhitelistDataAccess
{
    private static final Logger LOG = LoggerFactory.getLogger(WhitelistDataAccess.class);

    private boolean setupCompleted = false;

    private static final String DEFAULT_SUPERUSER_NAME = "cassandra";

    private DeleteStatement deleteWhitelistStatement;
    private SelectStatement loadWhitelistStatement;

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
                AuthKeyspace.NAME,
                AuditAuthKeyspace.WHITELIST_TABLE_NAME_V2);

        deleteWhitelistStatement = (DeleteStatement) prepare(
                "DELETE FROM %s.%s WHERE role = ?",
                AuthKeyspace.NAME,
                AuditAuthKeyspace.WHITELIST_TABLE_NAME_V2);

        maybeMigrateTableData();

        setupCompleted = true;
    }

    void addToWhitelist(RoleResource role, IResource whitelistResource, Set<Permission> whitelistOperations)
    {
        updateWhitelist(role, whitelistResource, whitelistOperations,
                        "UPDATE %s.%s SET operations = operations + {%s} WHERE role = '%s' AND resource = '%s'");
    }

    void removeFromWhitelist(RoleResource role, IResource whitelistResource, Set<Permission> whitelistOperations)
    {
        updateWhitelist(role, whitelistResource, whitelistOperations,
                        "UPDATE %s.%s SET operations = operations - {%s} WHERE role = '%s' AND resource = '%s'");
    }

    private void updateWhitelist(RoleResource role, IResource whitelistResource, Set<Permission> whitelistOperations, String statementTemplate)
    {
        List<String> quotedWhitelistOperations = whitelistOperations
                                                 .stream()
                                                 .map(Enum::name)
                                                 .map(p -> "'" + p + "'")
                                                 .collect(Collectors.toList());

        String statement = String.format(
        statementTemplate,
        AuthKeyspace.NAME,
        AuditAuthKeyspace.WHITELIST_TABLE_NAME_V2,
        StringUtils.join(quotedWhitelistOperations, ','),
        escape(role.getRoleName()),
        whitelistResource.getName());

        QueryProcessor.process(statement, consistencyForRole(role));
    }

    public Map<IResource, Set<Permission>> getWhitelist(RoleResource role)
    {
        ResultMessage.Rows rows = loadWhitelistStatement.execute(
                QueryState.forInternalCalls(),
                QueryOptions.forInternalCalls(
                        consistencyForRole(role),
                        Collections.singletonList(ByteBufferUtil.bytes(role.getRoleName()))));

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
                        Collections.singletonList(ByteBufferUtil.bytes(role.getRoleName()))));
    }

    private synchronized void maybeCreateTable()
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

    private synchronized void maybeMigrateTableData()
    {
        // The delay is to give the node a chance to see its peers before attempting the conversion
        if (Schema.instance.getCFMetaData(AuthKeyspace.NAME, AuditAuthKeyspace.WHITELIST_TABLE_NAME_V1) != null)
        {
            ScheduledExecutors.optionalTasks.schedule(this::migrateTableData, AuthKeyspace.SUPERUSER_SETUP_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private void migrateTableData()
    {
        try
        {
            LOG.info("Converting legacy audit whitelist data");

            UntypedResultSet whitelists = QueryProcessor.process(
            String.format("SELECT role, resources FROM %s.%s",
                          AuthKeyspace.NAME, AuditAuthKeyspace.WHITELIST_TABLE_NAME_V1),
            ConsistencyLevel.LOCAL_ONE);

            for (UntypedResultSet.Row row : whitelists)
            {
                SetSerializer<String> serializer = SetSerializer.getInstance(UTF8Serializer.instance, UTF8Type.instance);
                Set<String> resourceNames = serializer.deserialize(row.getBytes("resources"));
                for (String resourceName : resourceNames)
                {
                    RoleResource role = RoleResource.role(row.getString("role"));
                    IResource resource = ResourceFactory.toResource(resourceName);
                    addToWhitelist(role, resource, resource.applicablePermissions());
                }
            }

            LOG.info("Whitelist data conversion completed. To remove this message - " +
                     "as a super user perform ALTER ROLE statement on yourself with OPTIONS set to { 'drop_legacy_audit_whitelist_table' : 'now' }");
        }
        catch (Exception e)
        {
            LOG.warn("Unable to complete conversion of legacy whitelist data (perhaps not enough nodes are upgraded yet). " +
                     "Conversion should not be considered complete", e);
        }
    }

    void dropLegacyWhitelistTable()
    {
        LOG.info("Dropping legacy (v1) audit whitelist data");
        MigrationManager.announceColumnFamilyDrop(AuthKeyspace.NAME, AuditAuthKeyspace.WHITELIST_TABLE_NAME_V1);
    }

    private String escape(String name)
    {
        return StringUtils.replace(name, "'", "''");
    }

    private CQLStatement prepare(String template, String keyspace, String table)
    {
        try
        {
            return QueryProcessor.parseStatement(String.format(template, keyspace, table)).prepare().statement;
        }
        catch (RequestValidationException e)
        {
            throw new AssertionError(e);
        }
    }

    private ConsistencyLevel consistencyForRole(RoleResource role)
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
