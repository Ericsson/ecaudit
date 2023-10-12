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
package com.ericsson.bss.cassandra.ecaudit.handler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.BatchQueryOptions;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.cql3.statements.ParsedStatement;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.transport.messages.ResultMessage.Prepared;
import org.apache.cassandra.utils.MD5Digest;
import org.apache.cassandra.utils.UUIDGen;

/**
 * An implementation of {@link QueryHandler} that performs audit logging on queries.
 *
 * It can be used as a stand-alone query handler, or wrapped inside another query handler if configuration is needed.
 */
public class AuditQueryHandler implements QueryHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditQueryHandler.class);

    private final QueryHandler wrappedQueryHandler;
    private final AuditAdapter auditAdapter;

    // This ThreadLocal is populated on calls to getPrepared() in order to build context for
    // prepared statements. It is used for prepared single and batch statements.
    private final ThreadLocal<List<String>> preparedRawCqlStatements = ThreadLocal.withInitial(ArrayList::new);

    /**
     * Create a stand-alone instance of {@link AuditQueryHandler} that uses a default configuration for audit logging
     * and wraps the default {@link QueryProcessor}.
     */
    public AuditQueryHandler()
    {
        this(QueryProcessor.instance);
    }

    /**
     * Creates an instance of {@link AuditQueryHandler} that uses the default audit logger configuration and wraps the
     * given query handler.
     *
     * The signature of this constructor must remain unchanged as it is used by other frameworks to create layers of
     * decorators on top of each other.
     *
     * @param queryHandler the query handler to wrap.
     */
    public AuditQueryHandler(QueryHandler queryHandler)
    {
        this(queryHandler, AuditAdapter.getInstance());
    }

    /**
     * Test constructor.
     */
    @VisibleForTesting
    AuditQueryHandler(QueryHandler queryHandler, AuditAdapter auditAdapter)
    {
        LOG.info("Auditing enabled on queries");
        if (DatabaseDescriptor.startRpc())
        {
            LOG.warn("Auditing will not be performed on prepared requests on the RPC (Thrift) interface. " // NOPMD
                     + "Disable the RPC server to remove this message.");
        }

        this.wrappedQueryHandler = queryHandler;
        this.auditAdapter = auditAdapter;
    }

    @Override
    public ResultMessage process(String query, QueryState state, QueryOptions options,
                                 Map<String, ByteBuffer> customPayload, long queryStartNanoTime)
    throws RequestExecutionException, RequestValidationException
    {
        long timestamp = System.currentTimeMillis();
        auditAdapter.auditRegular(query, state.getClientState(), Status.ATTEMPT, timestamp);
        try
        {
            ResultMessage result = wrappedQueryHandler.process(query, state, options, customPayload, queryStartNanoTime);
            auditAdapter.auditRegular(query, state.getClientState(), Status.SUCCEEDED, timestamp);
            return result;
        }
        catch (RuntimeException e)
        {
            auditAdapter.auditRegular(query, state.getClientState(), Status.FAILED, timestamp);
            throw e;
        }
    }

    @Override
    public ResultMessage processPrepared(CQLStatement statement, QueryState state, QueryOptions options,
                                         Map<String, ByteBuffer> customPayload, long queryStartNanoTime)
    throws RequestExecutionException, RequestValidationException
    {
        try
        {
            List<String> rawCqlStatementList = preparedRawCqlStatements.get();
            if (rawCqlStatementList.isEmpty())
            {
                // There is no raw CQL statement in the list if call is coming on the Thrift interface
                return wrappedQueryHandler.processPrepared(statement, state, options, customPayload,
                                                           queryStartNanoTime);
            }

            String rawCqlStatement = rawCqlStatementList.get(0);
            return processPreparedWithAudit(statement, rawCqlStatement, state, options, customPayload,
                                            queryStartNanoTime);
        }
        finally
        {
            preparedRawCqlStatements.remove();
        }
    }

    private ResultMessage processPreparedWithAudit(CQLStatement statement, String rawCqlStatement, QueryState state,
                                                   QueryOptions options, Map<String, ByteBuffer> customPayload, long queryStartNanoTime)
    throws RequestExecutionException, RequestValidationException
    {
        long timestamp = System.currentTimeMillis();
        auditAdapter.auditPrepared(rawCqlStatement, statement, state.getClientState(), options, Status.ATTEMPT, timestamp);
        try
        {
            ResultMessage result = wrappedQueryHandler.processPrepared(statement, state, options, customPayload, queryStartNanoTime);
            auditAdapter.auditPrepared(rawCqlStatement, statement, state.getClientState(), options, Status.SUCCEEDED, timestamp);
            return result;
        }
        catch (RuntimeException e)
        {
            auditAdapter.auditPrepared(rawCqlStatement, statement, state.getClientState(), options, Status.FAILED, timestamp);
            throw e;
        }
    }

    @Override
    public ResultMessage processBatch(BatchStatement statement, QueryState state, BatchQueryOptions options,
                                      Map<String, ByteBuffer> customPayload, long queryStartNanoTime)
    throws RequestExecutionException, RequestValidationException
    {
        try
        {
            List<String> rawCqlStatementList = preparedRawCqlStatements.get();
            return processBatchWithAudit(statement, rawCqlStatementList, state, options, customPayload, queryStartNanoTime);
        }
        finally
        {
            preparedRawCqlStatements.remove();
        }
    }

    private ResultMessage processBatchWithAudit(BatchStatement statement, List<String> rawCqlStatements,
                                                QueryState state, BatchQueryOptions options, Map<String, ByteBuffer> customPayload, long queryStartNanoTime)
    throws RequestExecutionException, RequestValidationException
    {
        UUID uuid = UUIDGen.getTimeUUID();
        long timestamp = System.currentTimeMillis();
        auditAdapter.auditBatch(statement, rawCqlStatements, uuid, state.getClientState(), options, Status.ATTEMPT, timestamp);
        try
        {
            ResultMessage result = wrappedQueryHandler.processBatch(statement, state, options, customPayload, queryStartNanoTime);
            auditAdapter.auditBatch(statement, rawCqlStatements, uuid, state.getClientState(), options, Status.SUCCEEDED, timestamp);
            return result;
        }
        catch (RuntimeException e)
        {
            auditAdapter.auditBatch(statement, rawCqlStatements, uuid, state.getClientState(), options, Status.FAILED, timestamp);
            throw e;
        }
    }

    @Override
    public Prepared prepare(String query, QueryState state, Map<String, ByteBuffer> customPayload)
    throws RequestValidationException
    {
        long timestamp = System.currentTimeMillis();
        auditAdapter.auditPrepare(query, state.getClientState(),  Status.ATTEMPT, timestamp);
        ResultMessage.Prepared preparedStatement;
        try
        {
            preparedStatement = wrappedQueryHandler.prepare(query, state, customPayload);
        }
        catch (RuntimeException e)
        {
            auditAdapter.auditPrepare(query, state.getClientState(),  Status.FAILED, timestamp);
            throw e;
        }

        return preparedStatement;
    }
    @Override
    public ParsedStatement.Prepared getPrepared(MD5Digest id)
    {
        ParsedStatement.Prepared prepared = wrappedQueryHandler.getPrepared(id);
        if (prepared == null)
        {
            preparedRawCqlStatements.remove();
            return null; // Return null to client, will trigger a new attempt
        }

        // When prepared statements are cached during startup the raw CQL statement can be missing
        if ("".equals(prepared.rawCQLStatement))
        {
            QueryProcessor.instance.evictPrepared(id);
            return null; // Return null to client, will trigger a re-prepare
        }

        preparedRawCqlStatements.get().add(prepared.rawCQLStatement);

        return prepared;
    }

    @Override
    public ParsedStatement.Prepared getPreparedForThrift(Integer id)
    {
        // Not possible to update preparedRawCqlStatements here as we don't have a usable id
        // Also no point in clearing preparedRawCqlStatements as it should already be empty.
        return wrappedQueryHandler.getPreparedForThrift(id);
    }
}
