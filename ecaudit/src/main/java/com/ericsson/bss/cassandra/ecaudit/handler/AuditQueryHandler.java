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

import org.apache.cassandra.cql3.BatchQueryOptions;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;
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

    // This ThreadLocal is populated on calls to getPrepared() and parse() in order to build context for statements.
    private final ThreadLocal<List<String>> rawCqlStatements = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<List<Long>> localTimestamp = ThreadLocal.withInitial(ArrayList::new);

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

        this.wrappedQueryHandler = queryHandler;
        this.auditAdapter = auditAdapter;
    }

    @Override
    public ResultMessage process(CQLStatement statement, QueryState state, QueryOptions options,
                                 Map<String, ByteBuffer> customPayload, long queryStartNanoTime)
    throws RequestExecutionException, RequestValidationException
    {
        try
        {
            String rawCqlStatement = rawCqlStatements.get().get(0);
            Long timestamp = localTimestamp.get().get(0);

            try
            {
                ResultMessage result = wrappedQueryHandler.process(statement, state, options, customPayload, queryStartNanoTime);
                auditAdapter.auditRegular(rawCqlStatement, state.getClientState(), Status.SUCCEEDED, timestamp);
                return result;
            }
            catch (RuntimeException e)
            {
                auditAdapter.auditRegular(rawCqlStatement, state.getClientState(), Status.FAILED, timestamp);
                throw e;
            }
        }
        finally
        {
            rawCqlStatements.remove();
            localTimestamp.remove();
        }
    }

    @Override
    public ResultMessage processPrepared(CQLStatement statement, QueryState state, QueryOptions options,
                                         Map<String, ByteBuffer> customPayload, long queryStartNanoTime)
    throws RequestExecutionException, RequestValidationException
    {
        try
        {
            String rawCqlStatement = rawCqlStatements.get().get(0);
            return processPreparedWithAudit(statement, rawCqlStatement, state, options, customPayload,
                                            queryStartNanoTime);
        }
        finally
        {
            rawCqlStatements.remove();
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
            List<String> rawCqlStatementList = rawCqlStatements.get();
            return processBatchWithAudit(statement, rawCqlStatementList, state, options, customPayload, queryStartNanoTime);
        }
        finally
        {
            rawCqlStatements.remove();
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
    public ResultMessage.Prepared prepare(String query, ClientState state, Map<String, ByteBuffer> customPayload)
    throws RequestValidationException
    {
        long timestamp = System.currentTimeMillis();
        auditAdapter.auditPrepare(query, state,  Status.ATTEMPT, timestamp);
        ResultMessage.Prepared preparedStatement;
        try
        {
            preparedStatement = wrappedQueryHandler.prepare(query, state, customPayload);
        }
        catch (RuntimeException e)
        {
            auditAdapter.auditPrepare(query, state,  Status.FAILED, timestamp);
            throw e;
        }

        return preparedStatement;
    }

    @Override
    public QueryHandler.Prepared getPrepared(MD5Digest id)
    {
        QueryHandler.Prepared prepared = wrappedQueryHandler.getPrepared(id);
        if (prepared == null)
        {
            rawCqlStatements.remove();
            return null; // Return null to client, will trigger a new attempt
        }

        rawCqlStatements.get().add(prepared.rawCQLStatement);

        return prepared;
    }

    @Override
    public CQLStatement parse(String queryString, QueryState queryState, QueryOptions options)
    {
        long timestamp = System.currentTimeMillis();
        localTimestamp.get().add(timestamp);
        auditAdapter.auditRegular(queryString, queryState.getClientState(), Status.ATTEMPT, timestamp);

        CQLStatement statement;
        try
        {
            statement = wrappedQueryHandler.parse(queryString, queryState, options);
        }
        catch (Exception e)
        {
            auditAdapter.auditRegular(queryString, queryState.getClientState(), Status.FAILED, timestamp);
            localTimestamp.remove();
            throw e;
        }

        rawCqlStatements.get().add(queryString);

        return statement;
    }
}
