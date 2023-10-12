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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.BatchQueryOptions;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.UnavailableException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage.Prepared;
import org.apache.cassandra.utils.MD5Digest;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditQueryHandler
{
    private static final Map<String, ByteBuffer> customPayload = Collections.emptyMap();

    @Mock
    private BatchQueryOptions mockBatchOptions;

    @Mock
    private BatchStatement mockBatchStatement;

    @Mock
    private CQLStatement mockStatement;

    @Mock
    private QueryOptions mockOptions;

    @Mock
    private QueryState mockQueryState;

    @Mock
    private ClientState mockClientState;

    @Mock
    private QueryHandler mockHandler;

    @Mock
    private AuditAdapter mockAdapter;

    @Captor
    private ArgumentCaptor<UUID> uuidCaptor;

    private AuditQueryHandler queryHandler;

    private static IPartitioner oldPartitionerToRestore;

    @BeforeClass
    public static void beforeAll()
    {
        ClientInitializer.beforeClass();
        oldPartitionerToRestore = DatabaseDescriptor.setPartitionerUnsafe(Mockito.mock(IPartitioner.class));
    }

    @AfterClass
    public static void afterAll()
    {
        DatabaseDescriptor.setPartitionerUnsafe(oldPartitionerToRestore);
        ClientInitializer.afterClass();
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

        QueryHandler.Prepared parsedPrepared = new QueryHandler.Prepared(mockStatement, query, false, "ks");
        Prepared prepared = new Prepared(statementId, null, null, null);

        when(mockHandler.getPrepared(statementId)).thenReturn(parsedPrepared);
        when(mockHandler.prepare(query, mockClientState, customPayload)).thenReturn(prepared);

        Prepared resultPrepared = queryHandler.prepare(query, mockClientState, customPayload);
        assertThat(resultPrepared).isSameAs(prepared);

        CQLStatement stmt = queryHandler.getPrepared(resultPrepared.statementId).statement;
        assertThat(stmt).isSameAs(mockStatement);

        verify(mockHandler, times(1)).prepare(eq(query), eq(mockClientState), eq(customPayload));
        verify(mockHandler, times(1)).getPrepared(eq(statementId));
        verify(mockAdapter, times(1)).auditPrepare(eq(query), eq(mockClientState), eq(Status.ATTEMPT), longThat(isCloseToNow()));



    }

    @Test
    public void testProcessSuccessful()
    {
        String query = "select * from ks.ts";

        CQLStatement statement = queryHandler.parse(query, mockQueryState, mockOptions);

        queryHandler.process(statement, mockQueryState, mockOptions, customPayload, System.nanoTime());
        verify(mockHandler, times(1)).parse(eq(query), eq(mockQueryState), eq(mockOptions));
        verify(mockAdapter, times(1)).auditRegular(eq(query), eq(mockClientState), eq(Status.ATTEMPT), longThat(isCloseToNow()));
        verify(mockAdapter, times(1)).auditRegular(eq(query), eq(mockClientState), eq(Status.SUCCEEDED), longThat(isCloseToNow()));
        verify(mockHandler, times(1)).process(eq(statement), eq(mockQueryState), eq(mockOptions), eq(customPayload), anyLong());
    }

    @Test
    public void testProcessFailed()
    {
        String query = "select * from ks.ts";

        CQLStatement statement = queryHandler.parse(query, mockQueryState, mockOptions);
        whenProcessThrowUnavailable(statement);

        assertThatExceptionOfType(RequestExecutionException.class)
                .isThrownBy(() -> queryHandler.process(statement, mockQueryState, mockOptions, customPayload, System.nanoTime()));

        verify(mockHandler, times(1)).parse(eq(query), eq(mockQueryState), eq(mockOptions));
        verify(mockAdapter, times(1)).auditRegular(eq(query), eq(mockClientState), eq(Status.ATTEMPT), longThat(isCloseToNow()));
        verify(mockHandler, times(1)).process(eq(statement), eq(mockQueryState), eq(mockOptions), eq(customPayload), anyLong());
        verify(mockAdapter, times(1)).auditRegular(eq(query), eq(mockClientState), eq(Status.FAILED), longThat(isCloseToNow()));
    }

    @Test
    public void testProcessPreparedSuccessful()
    {
        String query = "select id from ks.ts where id = ?";
        MD5Digest statementId = MD5Digest.compute(query);
        QueryHandler.Prepared parsedPrepared = new QueryHandler.Prepared(mockStatement, query, false, "ks");

        when(mockHandler.getPrepared(statementId)).thenReturn(parsedPrepared);

        CQLStatement stmt = queryHandler.getPrepared(statementId).statement;
        queryHandler.processPrepared(stmt, mockQueryState, mockOptions, customPayload, System.nanoTime());

        verify(mockHandler, times(1)).getPrepared(eq(statementId));
        verify(mockAdapter, times(1)).auditPrepared(eq(query), eq(mockStatement), eq(mockClientState), eq(mockOptions), eq(Status.ATTEMPT), longThat(isCloseToNow()));
        verify(mockAdapter, times(1)).auditPrepared(eq(query), eq(mockStatement), eq(mockClientState), eq(mockOptions), eq(Status.SUCCEEDED), longThat(isCloseToNow()));
        verify(mockHandler, times(1)).processPrepared(eq(mockStatement), eq(mockQueryState), eq(mockOptions), eq(customPayload), anyLong());
    }

    @Test
    public void testProcessPreparedFailed()
    {
        String query = "select id from ks.ts where id = ?";
        MD5Digest statementId = MD5Digest.compute(query);
        QueryHandler.Prepared parsedPrepared = new QueryHandler.Prepared(mockStatement, query, false, "ks");

        when(mockHandler.getPrepared(statementId)).thenReturn(parsedPrepared);
        whenProcessPreparedThrowUnavailable();

        CQLStatement stmt = queryHandler.getPrepared(statementId).statement;
        assertThatExceptionOfType(UnavailableException.class)
                .isThrownBy(() -> queryHandler.processPrepared(stmt, mockQueryState, mockOptions, customPayload, System.nanoTime()));

        verify(mockHandler, times(1)).getPrepared(eq(statementId));
        verify(mockAdapter, times(1)).auditPrepared(eq(query), eq(mockStatement), eq(mockClientState), eq(mockOptions), eq(Status.ATTEMPT), longThat(isCloseToNow()));
        verify(mockHandler, times(1)).processPrepared(eq(mockStatement), eq(mockQueryState), eq(mockOptions), eq(customPayload), anyLong());
        verify(mockAdapter, times(1)).auditPrepared(eq(query), eq(mockStatement), eq(mockClientState), eq(mockOptions), eq(Status.FAILED), longThat(isCloseToNow()));
    }

    @Test
    public void testProcessBatchSuccessful()
    {
        String query = "INSERT INTO ks.ts (id, value) VALUES (?, 'abc')";
        MD5Digest statementId = MD5Digest.compute(query);
        QueryHandler.Prepared parsedPrepared = new QueryHandler.Prepared(mockStatement, query, false, "ks");

        givenBatchOfTwoStatementsArePrepared(statementId, parsedPrepared);

        queryHandler.processBatch(mockBatchStatement, mockQueryState, mockBatchOptions, customPayload, System.nanoTime());

        verify(mockHandler, times(2)).getPrepared(eq(statementId));
        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), eq(Arrays.asList(query, query)), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.ATTEMPT), longThat(isCloseToNow()));
        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), eq(Arrays.asList(query, query)), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.SUCCEEDED), longThat(isCloseToNow()));
        verify(mockHandler, times(1)).processBatch(eq(mockBatchStatement), eq(mockQueryState), eq(mockBatchOptions), eq(customPayload), anyLong());
    }

    @Test
    public void testProcessBatchRecoverFromUnprepared()
    {
        String query1 = "INSERT INTO ks.ts (id, value) VALUES (?, ?)";
        MD5Digest statementId1 = MD5Digest.compute(query1);
        QueryHandler.Prepared parsedPrepared1 = new QueryHandler.Prepared(mockStatement, query1, false, "ks");

        String query2 = "INSERT INTO ks.ts (id, temperature) VALUES (?, ?)";
        MD5Digest statementId2 = MD5Digest.compute(query2);
        QueryHandler.Prepared parsedPrepared2 = new QueryHandler.Prepared(mockStatement, query2, false, "ks");

        givenBatchOfTwoStatementsAreNotPrepared(statementId1, parsedPrepared1);
        givenBatchOfTwoStatementsArePrepared(statementId2, parsedPrepared2);

        queryHandler.processBatch(mockBatchStatement, mockQueryState, mockBatchOptions, customPayload, System.nanoTime());

        verify(mockHandler, times(2)).getPrepared(eq(statementId1));
        verify(mockHandler, times(2)).getPrepared(eq(statementId2));
        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), eq(Arrays.asList(query2, query2)), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.ATTEMPT), longThat(isCloseToNow()));
        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), eq(Arrays.asList(query2, query2)), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.SUCCEEDED), longThat(isCloseToNow()));
        verify(mockHandler, times(1)).processBatch(eq(mockBatchStatement), eq(mockQueryState), eq(mockBatchOptions), eq(customPayload), anyLong());
    }

    @Test
    public void testProcessBatchFailed()
    {
        String query = "INSERT INTO ks.ts (id, value) VALUES (?, 'abc')";
        MD5Digest statementId = MD5Digest.compute(query);
        QueryHandler.Prepared parsedPrepared = new QueryHandler.Prepared(mockStatement, query, false, "ks");

        givenBatchOfTwoStatementsArePrepared(statementId, parsedPrepared);
        whenProcessBatchThrowUnavailable();

        assertThatExceptionOfType(RequestExecutionException.class)
                .isThrownBy(() -> queryHandler.processBatch(mockBatchStatement, mockQueryState, mockBatchOptions, customPayload, System.nanoTime()));

        verify(mockHandler, times(2)).getPrepared(eq(statementId));
        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), eq(Arrays.asList(query, query)), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.ATTEMPT), longThat(isCloseToNow()));
        verify(mockHandler, times(1)).processBatch(eq(mockBatchStatement), eq(mockQueryState), eq(mockBatchOptions), eq(customPayload), anyLong());
        verify(mockAdapter, times(1)).auditBatch(eq(mockBatchStatement), eq(Arrays.asList(query, query)), any(UUID.class), eq(mockClientState), eq(mockBatchOptions), eq(Status.FAILED), longThat(isCloseToNow()));
    }

    @Test
    public void testTimeBaseUuidIsCreatedForBatchId()
    {
        queryHandler.processBatch(mockBatchStatement, mockQueryState, mockBatchOptions, customPayload, System.nanoTime());
        verify(mockAdapter).auditBatch(any(), any(), uuidCaptor.capture(), any(), any(), eq(Status.ATTEMPT), anyLong());
        reset(mockAdapter, mockHandler);
        assertThat(uuidCaptor.getValue().version()).as("UUID version should be time-based").isEqualTo(1);
    }

    @Test
    public void testGetPreparedStatementReturnsNullAlsoReturnsNull()
    {
        String query = "select id from ks.ts where id = ?";
        MD5Digest statementId = MD5Digest.compute(query);

        when(mockHandler.getPrepared(statementId)).thenReturn(null);

        QueryHandler.Prepared prepared = queryHandler.getPrepared(statementId);
        assertThat(prepared).isNull();
        verify(mockHandler, times(1)).getPrepared(statementId);
    }

    private void givenBatchOfTwoStatementsArePrepared(MD5Digest statementId, QueryHandler.Prepared parsedPrepared)
    {
        when(mockHandler.getPrepared(eq(statementId))).thenReturn(parsedPrepared);
        queryHandler.getPrepared(statementId);
        queryHandler.getPrepared(statementId);
    }

    private void givenBatchOfTwoStatementsAreNotPrepared(MD5Digest statementId, QueryHandler.Prepared parsedPrepared) {
        when(mockHandler.getPrepared(eq(statementId))).thenReturn(parsedPrepared).thenReturn(null);
        queryHandler.getPrepared(statementId);
        queryHandler.getPrepared(statementId);
    }

    @SuppressWarnings("unchecked")
    private void whenProcessThrowUnavailable(CQLStatement statement) {
        when(mockHandler.process(eq(statement), eq(mockQueryState), eq(mockOptions), eq(customPayload), anyLong()))
                .thenThrow(UnavailableException.class);
    }

    @SuppressWarnings("unchecked")
    private void whenProcessPreparedThrowUnavailable() {
        when(mockHandler.processPrepared(eq(mockStatement), eq(mockQueryState), eq(mockOptions), eq(customPayload), anyLong()))
                .thenThrow(UnavailableException.class);
    }

    @SuppressWarnings("unchecked")
    private void whenProcessBatchThrowUnavailable() {
        when(mockHandler.processBatch(eq(mockBatchStatement), eq(mockQueryState), eq(mockBatchOptions), eq(customPayload), anyLong()))
                .thenThrow(UnavailableException.class);
    }

    public static ArgumentMatcher<Long> isCloseToNow()
    {
        long now = System.currentTimeMillis();
        return value -> Math.abs(now - value) < 1000; // Within second
    }
}
