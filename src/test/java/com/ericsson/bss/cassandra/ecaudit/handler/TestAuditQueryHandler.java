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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.BatchQueryOptions;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.cql3.statements.ParsedStatement;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.UnavailableException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage.Prepared;
import org.apache.cassandra.utils.MD5Digest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.entry.Status;

@RunWith(MockitoJUnitRunner.class)
public class TestAuditQueryHandler
{
    private static final Map<String, ByteBuffer> customPayload = Collections.emptyMap();

    @Mock
    BatchQueryOptions mockBatchOptions;

    @Mock
    BatchStatement mockBatchStatement;

    @Mock
    CQLStatement mockStatement;

    @Mock
    QueryOptions mockOptions;

    @Mock
    QueryState mockQueryState;

    @Mock
    ClientState mockClientState;

    @Mock
    QueryHandler mockHandler;

    @Mock
    AuditAdapter mockAdapter;

    AuditQueryHandler queryHandler;

    static IPartitioner oldPartitionerToRestore;

    @BeforeClass
    public static void beforeAll()
    {
        Config.setClientMode(true);
        oldPartitionerToRestore = DatabaseDescriptor.setPartitionerUnsafe(Mockito.mock(IPartitioner.class));
    }

    @AfterClass
    public static void afterAll()
    {
        DatabaseDescriptor.setPartitionerUnsafe(oldPartitionerToRestore);
        Config.setClientMode(false);
    }

    @Before
    public void before()
    {
        queryHandler = new AuditQueryHandler(mockHandler, mockAdapter);
        when(mockQueryState.getClientState()).thenReturn(mockClientState);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockHandler, mockAdapter);
    }

    @Test
    public void testPrepareAndGetPrepared()
    {
        String query = "select id from ks.ts where id = ?";
        MD5Digest statementId = MD5Digest.compute(query);

        ParsedStatement.Prepared parsedPrepared = new ParsedStatement.Prepared(mockStatement);
        Prepared prepared = new Prepared(statementId, parsedPrepared);

        when(mockHandler.getPrepared(statementId)).thenReturn(parsedPrepared);
        when(mockHandler.prepare(query, mockQueryState, customPayload)).thenReturn(prepared);

        Prepared resultPrepared = queryHandler.prepare(query, mockQueryState, customPayload);
        assertThat(resultPrepared).isSameAs(prepared);

        CQLStatement stmt = queryHandler.getPrepared(resultPrepared.statementId).statement;
        assertThat(stmt).isSameAs(mockStatement);

        verify(mockHandler, times(1)).prepare(eq(query), eq(mockQueryState), eq(customPayload));
        verify(mockHandler, times(1)).getPrepared(eq(statementId));
        verify(mockAdapter, times(1)).mapIdToQuery(eq(statementId), eq(query));
    }

    @Test
    public void testProcessSuccessful()
    {
        String query = "select * from ks.ts";

        queryHandler.process(query, mockQueryState, mockOptions, customPayload);
        verify(mockAdapter, times(1)).auditRegular(eq(query), eq(mockClientState), eq(Status.ATTEMPT));
        verify(mockHandler, times(1)).process(eq(query), eq(mockQueryState), eq(mockOptions), eq(customPayload));
    }

    @Test
    public void testProcessFailed()
    {
        String query = "select * from ks.ts";
        whenProcessThrowUnavailable(query);

        assertThatExceptionOfType(RequestExecutionException.class)
                .isThrownBy(() -> queryHandler.process(query, mockQueryState, mockOptions, customPayload));

        verify(mockAdapter, times(1)).auditRegular(eq(query), eq(mockClientState), eq(Status.ATTEMPT));
        verify(mockHandler, times(1)).process(eq(query), eq(mockQueryState), eq(mockOptions), eq(customPayload));
        verify(mockAdapter, times(1)).auditRegular(eq(query), eq(mockClientState), eq(Status.FAILED));
    }

    @Test
    public void testProcessPreparedSuccessful()
    {
        String query = "select id from ks.ts where id = ?";
        MD5Digest statementId = MD5Digest.compute(query);
        ParsedStatement.Prepared parsedPrepared = new ParsedStatement.Prepared(mockStatement);

        when(mockHandler.getPrepared(statementId)).thenReturn(parsedPrepared);

        CQLStatement stmt = queryHandler.getPrepared(statementId).statement;
        queryHandler.processPrepared(stmt, mockQueryState, mockOptions, customPayload);

        verify(mockHandler, times(1)).getPrepared(eq(statementId));
        verify(mockAdapter, times(1)).auditPrepared(eq(statementId), eq(mockStatement), eq(mockClientState), eq(mockOptions), eq(Status.ATTEMPT));
        verify(mockHandler, times(1)).processPrepared(eq(mockStatement), eq(mockQueryState), eq(mockOptions), eq(customPayload));
    }

    @Test
    public void testProcessPreparedFailed()
    {
        String query = "select id from ks.ts where id = ?";
        MD5Digest statementId = MD5Digest.compute(query);
        ParsedStatement.Prepared parsedPrepared = new ParsedStatement.Prepared(mockStatement);

        when(mockHandler.getPrepared(statementId)).thenReturn(parsedPrepared);
        whenProcessPreparedThrowUnavailable();

        CQLStatement stmt = queryHandler.getPrepared(statementId).statement;
        assertThatExceptionOfType(UnavailableException.class)
                .isThrownBy(() -> queryHandler.processPrepared(stmt, mockQueryState, mockOptions, customPayload));

        verify(mockHandler, times(1)).getPrepared(eq(statementId));
        verify(mockAdapter, times(1)).auditPrepared(eq(statementId), eq(mockStatement), eq(mockClientState), eq(mockOptions), eq(Status.ATTEMPT));
        verify(mockHandler, times(1)).processPrepared(eq(mockStatement), eq(mockQueryState), eq(mockOptions), eq(customPayload));
        verify(mockAdapter, times(1)).auditPrepared(eq(statementId), eq(mockStatement), eq(mockClientState), eq(mockOptions), eq(Status.FAILED));
    }

    @Test
    public void testProcessPreparedForThriftSuccessful()
    {
        int thriftItemId = 345;
        ParsedStatement.Prepared parsedPrepared = new ParsedStatement.Prepared(mockStatement);

        when(mockHandler.getPreparedForThrift(eq(thriftItemId))).thenReturn(parsedPrepared);

        CQLStatement stmt = queryHandler.getPreparedForThrift(thriftItemId).statement;
        queryHandler.processPrepared(stmt, mockQueryState, mockOptions, customPayload);

        verify(mockHandler, times(1)).getPreparedForThrift(eq(thriftItemId));
        verify(mockHandler, times(1)).processPrepared(eq(mockStatement), eq(mockQueryState), eq(mockOptions), eq(customPayload));
    }

    @Test
    public void testProcessPreparedForThriftFailed()
    {
        int thriftItemId = 345;
        ParsedStatement.Prepared parsedPrepared = new ParsedStatement.Prepared(mockStatement);

        when(mockHandler.getPreparedForThrift(eq(thriftItemId))).thenReturn(parsedPrepared);
        whenProcessPreparedThrowUnavailable();

        CQLStatement stmt = queryHandler.getPreparedForThrift(thriftItemId).statement;
        assertThatExceptionOfType(UnavailableException.class)
                .isThrownBy(() -> queryHandler.processPrepared(stmt, mockQueryState, mockOptions, customPayload));

        verify(mockHandler, times(1)).getPreparedForThrift(eq(thriftItemId));
        verify(mockHandler, times(1)).processPrepared(eq(mockStatement), eq(mockQueryState), eq(mockOptions), eq(customPayload));
    }

    @Test
    public void testProcessBatchSuccessful()
    {
        queryHandler.processBatch(mockBatchStatement, mockQueryState, mockBatchOptions, customPayload);
        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.ATTEMPT));
        verify(mockHandler, times(1)).processBatch(eq(mockBatchStatement), eq(mockQueryState), eq(mockBatchOptions), eq(customPayload));
    }

    @Test
    public void testProcessBatchFailed()
    {
        whenProcessBatchThrowUnavailable();

        assertThatExceptionOfType(RequestExecutionException.class)
                .isThrownBy(() -> queryHandler.processBatch(mockBatchStatement, mockQueryState, mockBatchOptions, customPayload));

        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.ATTEMPT));
        verify(mockHandler, times(1)).processBatch(eq(mockBatchStatement), eq(mockQueryState), eq(mockBatchOptions), eq(customPayload));
        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.FAILED));
    }

    @Test
    public void testGetPreparedStatementReturnsNullAlsoReturnsNull()
    {
        String query = "select id from ks.ts where id = ?";
        MD5Digest statementId = MD5Digest.compute(query);

        when(mockHandler.getPrepared(statementId)).thenReturn(null);

        ParsedStatement.Prepared prepared = queryHandler.getPrepared(statementId);
        assertThat(prepared).isNull();
        verify(mockHandler, times(1)).getPrepared(statementId);
    }

    @SuppressWarnings("unchecked")
    private void whenProcessThrowUnavailable(String query) {
        when(mockHandler.process(eq(query), eq(mockQueryState), eq(mockOptions), eq(customPayload)))
                .thenThrow(UnavailableException.class);
    }

    @SuppressWarnings("unchecked")
    private void whenProcessPreparedThrowUnavailable() {
        when(mockHandler.processPrepared(eq(mockStatement), eq(mockQueryState), eq(mockOptions), eq(customPayload)))
                .thenThrow(UnavailableException.class);
    }

    @SuppressWarnings("unchecked")
    private void whenProcessBatchThrowUnavailable() {
        when(mockHandler.processBatch(eq(mockBatchStatement), eq(mockQueryState), eq(mockBatchOptions), eq(customPayload)))
                .thenThrow(UnavailableException.class);
    }
}
