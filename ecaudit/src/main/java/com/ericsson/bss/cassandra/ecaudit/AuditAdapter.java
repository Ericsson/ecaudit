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
package com.ericsson.bss.cassandra.ecaudit;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.google.common.annotations.VisibleForTesting;

import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.PreparedAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.factory.AuditEntryBuilderFactory;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.BoundValueSuppressor;
import com.ericsson.bss.cassandra.ecaudit.entry.suppressor.PrepareAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.facade.Auditor;
import com.ericsson.bss.cassandra.ecaudit.utils.Exceptions;
import org.apache.cassandra.cql3.BatchQueryOptions;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.MD5Digest;

/**
 * This class will be responsible for populating {@link AuditEntry} instance and passing that to {@link Auditor} instance
 */
public class AuditAdapter
{
    // Batch
    private final static String BATCH_FAILURE = "Apply batch failed: %s";

    private final Auditor auditor;
    private final AuditEntryBuilderFactory entryBuilderFactory;
    private BoundValueSuppressor boundValueSuppressor;

    /**
     * Constructor, see {@link AuditAdapterFactory#createAuditAdapter()}
     *
     * @param auditor             the auditor to use
     * @param entryBuilderFactory the audit entry builder factory to use
     * @param boundValueSuppressor the bound value suppressor
     */
    AuditAdapter(Auditor auditor, AuditEntryBuilderFactory entryBuilderFactory, BoundValueSuppressor boundValueSuppressor)
    {
        this.auditor = auditor;
        this.entryBuilderFactory = entryBuilderFactory;
        this.boundValueSuppressor = boundValueSuppressor;
    }

    public static AuditAdapter getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder
    {
        private static final AuditAdapter INSTANCE = AuditAdapterFactory.createAuditAdapter();
    }

    public void setup()
    {
        auditor.setup();
    }

    /**
     * Audit a regular CQL statement.
     *
     * @param operation the CQL statement to audit
     * @param state     the client state accompanying the statement
     * @param status    the statement operation status
     * @param timestamp the system timestamp for the request
     */
    public void auditRegular(String operation, ClientState state, Status status, long timestamp)
    {
        if (auditor.shouldLogForStatus(status))
        {
            AuditEntry logEntry = entryBuilderFactory.createEntryBuilder(operation, state)
                                                     .client(state.getRemoteAddress())
                                                     .coordinator(FBUtilities.getJustBroadcastAddress())
                                                     .user(state.getUser().getName())
                                                     .operation(new SimpleAuditOperation(operation))
                                                     .status(status)
                                                     .timestamp(timestamp)
                                                     .build();

            auditor.audit(logEntry);
        }
    }
    /**
     * Audit a regular CQL statement.
     *
     * @param operation the CQL statement to audit
     * @param state     the client state accompanying the statement
     * @param status    the statement operation status
     * @param timestamp the system timestamp for the request
     */
    public void auditPrepare(String operation, ClientState state, Status status, long timestamp)
    {
        if (auditor.shouldLogForStatus(status) && auditor.shouldLogPrepareStatements())
        {
            AuditEntry logEntry = entryBuilderFactory.createEntryBuilder(operation, state)
                                                     .client(state.getRemoteAddress())
                                                     .coordinator(FBUtilities.getJustBroadcastAddress())
                                                     .user(state.getUser().getName())
                                                     .operation(new PrepareAuditOperation(operation))
                                                     .status(status)
                                                     .timestamp(timestamp)
                                                     .build();

            auditor.audit(logEntry);
        }
    }
    /**
     * Audit a prepared statement.
     *
     * @param rawStatement the raw prepared statement string
     * @param statement    the statement to audit
     * @param state        the client state accompanying the statement
     * @param options      the options accompanying the statement
     * @param status       the statement operation status
     * @param timestamp    the system timestamp for the request
     */
    public void auditPrepared(String rawStatement, CQLStatement statement, ClientState state, QueryOptions options, Status status, long timestamp)
    {
        if (auditor.shouldLogForStatus(status))
        {
            AuditEntry logEntry = entryBuilderFactory.createEntryBuilder(statement)
                                                     .client(state.getRemoteAddress())
                                                     .coordinator(FBUtilities.getJustBroadcastAddress())
                                                     .user(state.getUser().getName())
                                                     .operation(new PreparedAuditOperation(rawStatement, options, boundValueSuppressor))
                                                     .status(status)
                                                     .timestamp(timestamp)
                                                     .build();

            auditor.audit(logEntry);
        }
    }

    /**
     * Audit a batch statement.
     *
     * @param statement     the batch statement to audit
     * @param rawStatements an ordered list of raw statements associated with the statements in the batch
     * @param uuid          to identify the batch
     * @param state         the client state accompanying the statement
     * @param options       the batch options accompanying the statement
     * @param status        the status of the operation
     * @param timestamp     the system timestamp for the request
     */
    public void auditBatch(BatchStatement statement, List<String> rawStatements, UUID uuid, ClientState state, BatchQueryOptions options, Status status, long timestamp)
    {
        if (auditor.shouldLogForStatus(status))
        {
            AuditEntry.Builder builder = entryBuilderFactory.createBatchEntryBuilder()
                                                            .client(state.getRemoteAddress())
                                                            .coordinator(FBUtilities.getJustBroadcastAddress())
                                                            .user(state.getUser().getName())
                                                            .batch(uuid)
                                                            .status(status)
                                                            .timestamp(timestamp);

            if (status == Status.FAILED && auditor.shouldLogFailedBatchSummary())
            {
                String failedBatchStatement = String.format(BATCH_FAILURE, uuid.toString());
                auditor.audit(builder.operation(new SimpleAuditOperation(failedBatchStatement)).build());
            }
            else
            {
                for (AuditEntry entry : getBatchOperations(builder, statement, rawStatements, state, options))
                {
                    auditor.audit(entry);
                }
            }
        }
    }

    /**
     * Audit an authentication attempt.
     *
     * @param username  the user to authenticate
     * @param clientIp  the address of the client that tries to authenticate
     * @param status    the status of the operation
     * @param timestamp the system timestamp for the request
     * @throws AuthenticationException if the audit operation could not be performed
     */
    public void auditAuth(String username, InetAddress clientIp, Status status, long timestamp) throws AuthenticationException
    {
        auditAuth(username, clientIp, null, status, timestamp);
    }

    /**
     * Audit an authentication attempt with a subject.
     *
     * @param userName  the user to authenticate
     * @param clientIp  the address of the client that tries to authenticate
     * @param subject   the subject to authenticate
     * @param status    the status of the operation
     * @param timestamp the system timestamp for the request
     * @throws AuthenticationException if the audit operation could not be performed
     */
    public void auditAuth(String userName, InetAddress clientIp, String subject, Status status, long timestamp)
    {
        if (auditor.shouldLogForStatus(status))
        {
            AuditEntry logEntry = entryBuilderFactory.createAuthenticationEntryBuilder()
                                                     .client(new InetSocketAddress(clientIp, AuditEntry.UNKNOWN_PORT))
                                                     .coordinator(FBUtilities.getJustBroadcastAddress())
                                                     .user(userName)
                                                     .subject(subject)
                                                     .status(status)
                                                     .operation(statusToAuthenticationOperation(status))
                                                     .timestamp(timestamp)
                                                     .build();

            try
            {
                auditor.audit(logEntry);
            }
            catch (RequestExecutionException e)
            {
                throw Exceptions.appendCause(new AuthenticationException(e.toString()), e);
            }
        }
    }

    static SimpleAuditOperation statusToAuthenticationOperation(Status status)
    {
        return new SimpleAuditOperation("Authentication " + status.getDisplayName());
    }

    /**
     * Get all the audit entries for a batch
     *
     * @param builder        the prepared audit entry builder
     * @param batchStatement the batch statement
     * @param state          the client state accompanying the statement
     * @param options        the options to get the operations from
     * @return a collection of operations, as strings
     */
    private Collection<AuditEntry> getBatchOperations(AuditEntry.Builder builder, BatchStatement batchStatement, List<String> rawStatements, ClientState state, BatchQueryOptions options)
    {
        List<AuditEntry> batchOperations = new ArrayList<>();

        // Statements and raw-statements are listed in the same order,
        // but raw-statement list only contain entries for prepared statements.
        int statementIndex = 0;
        int rawStatementIndex = 0;
        for (Object queryOrId : options.getQueryOrIdList())
        {
            if (queryOrId instanceof MD5Digest)
            {
                entryBuilderFactory.updateBatchEntryBuilder(builder, batchStatement.getStatements().get(statementIndex));
                builder.operation(new PreparedAuditOperation(rawStatements.get(rawStatementIndex++), options.forStatement(statementIndex), boundValueSuppressor));
                batchOperations.add(builder.build());
            }
            else
            {
                entryBuilderFactory.updateBatchEntryBuilder(builder, queryOrId.toString(), state);
                builder.operation(new SimpleAuditOperation(queryOrId.toString()));
                batchOperations.add(builder.build());
            }
            statementIndex++;
        }

        return batchOperations;
    }

    public Auditor getAuditor()
    {
        return auditor;
    }

    @VisibleForTesting
    public void setBoundValueSuppressor(BoundValueSuppressor suppressor)
    {
        this.boundValueSuppressor = suppressor;
    }

    @VisibleForTesting
    public BoundValueSuppressor getBoundValueSuppressor()
    {
        return boundValueSuppressor;
    }
}
