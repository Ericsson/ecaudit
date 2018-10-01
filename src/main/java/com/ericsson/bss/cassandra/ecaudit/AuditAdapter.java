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
package com.ericsson.bss.cassandra.ecaudit;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.cassandra.cql3.BatchQueryOptions;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.utils.MD5Digest;

import com.ericsson.bss.cassandra.ecaudit.auth.ConnectionResource;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.PreparedAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.Status;
import com.ericsson.bss.cassandra.ecaudit.facade.Auditor;

/**
 * This class will be responsible for populating {@link AuditEntry} instance and passing that to {@link Auditor} instance
 */
public class AuditAdapter
{
    // Authentication
    private final static SimpleAuditOperation AUTHENTICATION_ATTEMPT = new SimpleAuditOperation("Authentication attempt");
    private final static SimpleAuditOperation AUTHENTICATION_FAILED = new SimpleAuditOperation("Authentication failed");

    // Batch
    private final static String BATCH_FAILURE = "Apply batch failed: %s";

    private final Auditor auditor;
    private final AuditEntryBuilderFactory entryBuilderFactory;

    /**
     * Constructor, see {@link AuditAdapterFactory#getInstance()}
     *
     * Visible for testing.
     *
     * @param auditor
     *            the auditor to use
     * @param entryBuilderFactory
     *            the audit entry builder factory to use
     */
    AuditAdapter(Auditor auditor, AuditEntryBuilderFactory entryBuilderFactory)
    {
        this.auditor = auditor;
        this.entryBuilderFactory = entryBuilderFactory;
    }

    /**
     * Audit a regular CQL statement.
     *
     * @param operation
     *            the CQL statement to audit
     * @param state
     *            the client state accompanying the statement
     * @param status
     *            the statement operation status
     */
    public void auditRegular(String operation, ClientState state, Status status)
    {
        AuditEntry logEntry = entryBuilderFactory.createEntryBuilder(operation, state)
                .client(state.getRemoteAddress().getAddress())
                .user(state.getUser().getName())
                .operation(new SimpleAuditOperation(operation))
                .status(status)
                .build();

        auditor.audit(logEntry);
    }

    /**
     * Audit a prepared statement.
     *
     * @param rawStatement
     *            the raw prepared statement string
     * @param statement
     *            the statement to audit
     * @param state
     *            the client state accompanying the statement
     * @param options
     *            the options accompanying the statement
     * @param status
     *            the statement operation status
     */
    public void auditPrepared(String rawStatement, CQLStatement statement, ClientState state, QueryOptions options, Status status)
    {
        AuditEntry logEntry = entryBuilderFactory.createEntryBuilder(statement)
                .client(state.getRemoteAddress().getAddress())
                .user(state.getUser().getName())
                .operation(new PreparedAuditOperation(rawStatement, options))
                .status(status)
                .build();

        auditor.audit(logEntry);
    }

    /**
     * Audit a batch statement.
     *
     * @param statement
     *            the batch statement to audit
     * @param rawStatements
     *            an ordered list of raw statements associated with the statements in the batch
     * @param uuid
     *            to identify the batch
     * @param state
     *            the client state accompanying the statement
     * @param options
     *            the batch options accompanying the statement
     * @param status
     *            the status of the operation
     */
    public void auditBatch(BatchStatement statement, List<String> rawStatements, UUID uuid, ClientState state, BatchQueryOptions options, Status status)
    {
        AuditEntry.Builder builder = entryBuilderFactory.createBatchEntryBuilder()
                .client(state.getRemoteAddress().getAddress())
                .user(state.getUser().getName())
                .batch(uuid)
                .status(status);

        if (status == Status.FAILED)
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

    /**
     * Audit an authentication attempt.
     *
     * @param username
     *            the user to authenticate
     * @param clientIp
     *            the address of the client that tries to authenticate
     * @param status
     *            the status of the operation
     */
    public void auditAuth(String username, InetAddress clientIp, Status status)
    {
        AuditEntry logEntry = AuditEntry.newBuilder()
                .client(clientIp)
                .user(username)
                .status(status)
                .operation(status == Status.ATTEMPT ? AUTHENTICATION_ATTEMPT : AUTHENTICATION_FAILED)
                .permissions(AuditEntryBuilderFactory.EXECUTE_PERMISSIONS)
                .resource(ConnectionResource.root())
                .build();

        auditor.audit(logEntry);
    }

    /**
     * Get all the audit entries for a batch
     *
     * @param builder
     *            the prepared audit entry builder
     * @param batchStatement
     *            the batch statement
     * @param state
     *            the client state accompanying the statement
     * @param options
     *            the options to get the operations from
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
            if(queryOrId instanceof MD5Digest)
            {
                builder = entryBuilderFactory.updateBatchEntryBuilder(builder, batchStatement.getStatements().get(statementIndex));
                builder = builder.operation(new PreparedAuditOperation(rawStatements.get(rawStatementIndex++), options.forStatement(statementIndex)));
                batchOperations.add(builder.build());
            }
            else
            {
                builder = entryBuilderFactory.updateBatchEntryBuilder(builder, queryOrId.toString(), state);
                builder.operation(new SimpleAuditOperation(queryOrId.toString()));
                batchOperations.add(builder.build());
            }
            statementIndex++;
        }

        return batchOperations;
    }
}
