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
package com.ericsson.bss.cassandra.ecaudit.handler;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.AuditAdapterFactory;
import com.ericsson.bss.cassandra.ecaudit.entry.Status;

/**
 * An implementation of {@link QueryHandler} that performs audit logging on queries.
 *
 * It can be used as a stand-alone query handler, or wrapped inside another query handler if configuration is needed.
 */
public class AuditQueryHandler implements QueryHandler
{
    public static final Logger LOG = LoggerFactory.getLogger(AuditQueryHandler.class);

    private final QueryHandler wrappedQueryHandler;
    private final AuditAdapter auditAdapter;

    private final ThreadLocal<MD5Digest> preparedId = new ThreadLocal<>();

    /**
     * Create a stand-alone instance of {@link AuditQueryHandler} that uses a default configuration for audit logging
     * and wraps the default {@link QueryProcessor}.
     */
    public AuditQueryHandler()
    {
        this(QueryProcessor.instance, createDefault());
    }

    /**
     * Creates an instance of {@link AuditQueryHandler} that uses the default audit logger configuration and wraps the
     * given query handler.
     *
     * @param queryHandler
     *            the query handler to wrap.
     */
    public AuditQueryHandler(QueryHandler queryHandler)
    {
        this(queryHandler, createDefault());
    }

    /**
     * Test constructor.
     */
    AuditQueryHandler(QueryHandler queryHandler, AuditAdapter auditAdapter)
    {
        LOG.info("Auditing enabled on queries");
        if (DatabaseDescriptor.startRpc())
        {
            LOG.warn("Auditing will not be performed on prepared requests on the RPC (Thrift) interface. "
                    + "Disable the RPC server to remove this message.");
        }

        this.wrappedQueryHandler = queryHandler;
        this.auditAdapter = auditAdapter;
    }

    @Override
    public ResultMessage process(String query, QueryState state, QueryOptions options,
            Map<String, ByteBuffer> customPayload) throws RequestExecutionException, RequestValidationException
    {
        auditAdapter.auditRegular(query, state.getClientState(), Status.ATTEMPT);
        try
        {
            return wrappedQueryHandler.process(query, state, options, customPayload);
        }
        catch (RuntimeException e)
        {
            auditAdapter.auditRegular(query, state.getClientState(), Status.FAILED);
            throw e;
        }
    }

    @Override
    public ResultMessage processPrepared(CQLStatement statement, QueryState state, QueryOptions options,
            Map<String, ByteBuffer> customPayload) throws RequestExecutionException, RequestValidationException
    {
        MD5Digest id = preparedId.get();
        if (id == null)
        {
            // There is no id if call is coming on the Thrift interface
            return wrappedQueryHandler.processPrepared(statement, state, options, customPayload);
        }

        return processPreparedWithAudit(statement, id, state, options, customPayload);
    }

    private ResultMessage processPreparedWithAudit(CQLStatement statement, MD5Digest id, QueryState state,
            QueryOptions options, Map<String, ByteBuffer> customPayload)
            throws RequestExecutionException, RequestValidationException
    {
        auditAdapter.auditPrepared(id, statement, state.getClientState(), options, Status.ATTEMPT);
        try
        {
            return wrappedQueryHandler.processPrepared(statement, state, options, customPayload);
        }
        catch (RuntimeException e)
        {
            auditAdapter.auditPrepared(id, statement, state.getClientState(), options, Status.FAILED);
            throw e;
        }
    }

    @Override
    public ResultMessage processBatch(BatchStatement statement, QueryState state, BatchQueryOptions options,
            Map<String, ByteBuffer> customPayload) throws RequestExecutionException, RequestValidationException
    {
        UUID uuid = UUID.randomUUID();
        auditAdapter.auditBatch(statement, uuid, state.getClientState(), options, Status.ATTEMPT);
        try
        {
            return wrappedQueryHandler.processBatch(statement, state, options, customPayload);
        }
        catch (RuntimeException e)
        {
            auditAdapter.auditBatch(statement, uuid, state.getClientState(), options, Status.FAILED);
            throw e;
        }
    }

    @Override
    public Prepared prepare(String query, QueryState state, Map<String, ByteBuffer> customPayload)
            throws RequestValidationException
    {
        Prepared prepared = wrappedQueryHandler.prepare(query, state, customPayload);
        auditAdapter.mapIdToQuery(prepared.statementId, query);

        return prepared;
    }

    @Override
    public ParsedStatement.Prepared getPrepared(MD5Digest id)
    {
        ParsedStatement.Prepared prepared = wrappedQueryHandler.getPrepared(id);
        if (prepared == null)
        {
            return null; // Return null to client, will trigger a new attempt
        }

        preparedId.set(id);

        return prepared;
    }

    @Override
    public ParsedStatement.Prepared getPreparedForThrift(Integer id)
    {
        preparedId.set(null);
        return wrappedQueryHandler.getPreparedForThrift(id);
    }

    /**
     * Construct an AuditAdapter instance by reading configuration from the system properties.
     *
     * @return an instance of {@link AuditAdapter}
     */
    private static AuditAdapter createDefault()
    {
        AuditAdapterFactory factory = new AuditAdapterFactory();
        return factory.getInstance();
    }
}
