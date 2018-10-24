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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.auth.ConnectionResource;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.Status;
import com.ericsson.bss.cassandra.ecaudit.facade.Auditor;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.BatchQueryOptions;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.cql3.statements.ModificationStatement;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.utils.MD5Digest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestAuditAdapter
{
    @Mock
    private AuthenticatedUser mockUser;

    @Mock
    private ClientState mockState;

    @Mock
    private CQLStatement mockStatement;

    @Mock
    private QueryOptions mockOptions;

    @Mock
    private Auditor mockAuditor;

    @Mock
    private AuditEntryBuilderFactory mockAuditEntryBuilderFactory;

    private AuditAdapter auditAdapter;

    private static IPartitioner oldPartitionerToRestore;

    @BeforeClass
    public static void beforeAll()
    {
        Config.setClientMode(true);
        oldPartitionerToRestore = DatabaseDescriptor.setPartitionerUnsafe(Mockito.mock(IPartitioner.class));
    }

    @Before
    public void before()
    {
        auditAdapter = new AuditAdapter(mockAuditor, mockAuditEntryBuilderFactory);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockAuditor);
    }

    @AfterClass
    public static void afterAll()
    {
        DatabaseDescriptor.setPartitionerUnsafe(oldPartitionerToRestore);
        Config.setClientMode(false);
    }

    @Test
    public void testProcessRegular()
    {
        String expectedStatement = "select * from ks.tbl";
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;

        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getUser()).thenReturn(mockUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);

        when(mockAuditEntryBuilderFactory.createEntryBuilder(eq(expectedStatement), eq(mockState)))
        .thenReturn(AuditEntry.newBuilder()
                              .permissions(ImmutableSet.of(Permission.SELECT))
                              .resource(DataResource.table("ks", "tbl")));

        auditAdapter.auditRegular(expectedStatement, mockState, expectedStatus);

        // Capture and perform validation
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());

        AuditEntry captured = captor.getValue();
        assertThat(captured.getClientAddress()).isEqualTo(expectedSocketAddress.getAddress());
        assertThat(captured.getOperation().getOperationString()).isEqualTo(expectedStatement);
        assertThat(captured.getUser()).isEqualTo(expectedUser);
        assertThat(captured.getStatus()).isEqualByComparingTo(expectedStatus);
        assertThat(captured.getBatchId()).isEqualTo(Optional.empty());
        assertThat(captured.getResource()).isEqualTo(DataResource.table("ks", "tbl"));
    }

    @Test
    public void testProcessRegularFailure()
    {
        String expectedStatement = "select * from ks.tbl";
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.FAILED;

        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getUser()).thenReturn(mockUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);

        when(mockAuditEntryBuilderFactory.createEntryBuilder(eq(expectedStatement), eq(mockState)))
        .thenReturn(AuditEntry.newBuilder()
                              .permissions(ImmutableSet.of(Permission.SELECT))
                              .resource(DataResource.table("ks", "tbl")));

        auditAdapter.auditRegular(expectedStatement, mockState, expectedStatus);

        // Capture and perform validation
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());

        AuditEntry captured = captor.getValue();
        assertThat(captured.getClientAddress()).isEqualTo(expectedSocketAddress.getAddress());
        assertThat(captured.getOperation().getOperationString()).isEqualTo(expectedStatement);
        assertThat(captured.getUser()).isEqualTo(expectedUser);
        assertThat(captured.getStatus()).isEqualByComparingTo(expectedStatus);
        assertThat(captured.getBatchId()).isEqualTo(Optional.empty());
        assertThat(captured.getPermissions()).isEqualTo(Sets.immutableEnumSet(Permission.SELECT));
        assertThat(captured.getResource()).isEqualTo(DataResource.table("ks", "tbl"));
    }

    @Test
    public void testProcessPreparedStatementSuccessful()
    {
        String preparedQuery = "select value1, value2 from ks.cf where pk = ? and ck = ?";
        MD5Digest statementId = MD5Digest.compute(preparedQuery);

        String expectedQuery = "select value1, value2 from ks.cf where pk = ? and ck = ?['text', 'text']";
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;

        List<ByteBuffer> values = createValues("text", "text");
        ImmutableList<ColumnSpecification> columns = createTextColumns("text", "text");

        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getUser()).thenReturn(mockUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);
        when(mockOptions.getValues()).thenReturn(values);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.hasColumnSpecifications()).thenReturn(true);

        when(mockAuditEntryBuilderFactory.createEntryBuilder(eq(mockStatement)))
        .thenReturn(AuditEntry.newBuilder()
                              .permissions(ImmutableSet.of(Permission.SELECT))
                              .resource(DataResource.table("ks", "cf")));

        auditAdapter.mapIdToQuery(statementId, preparedQuery);
        auditAdapter.auditPrepared(statementId, mockStatement, mockState, mockOptions, expectedStatus);

        // Capture and perform validation
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());
        verifyNoMoreInteractions(mockOptions);

        AuditEntry captured = captor.getValue();
        assertThat(captured.getClientAddress()).isEqualTo(expectedSocketAddress.getAddress());
        assertThat(captured.getOperation().getOperationString()).isEqualTo(expectedQuery);
        assertThat(captured.getUser()).isEqualTo(expectedUser);
        assertThat(captured.getStatus()).isEqualByComparingTo(expectedStatus);
        assertThat(captured.getBatchId()).isEqualTo(Optional.empty());
        assertThat(captured.getPermissions()).isEqualTo(Sets.immutableEnumSet(Permission.SELECT));
        assertThat(captured.getResource()).isEqualTo(DataResource.table("ks", "cf"));
    }

    @Test
    public void testProcessPreparedStatementFailure()
    {
        String preparedQuery = "select value1, value2 from ks.cf where pk = ? and ck = ?";
        MD5Digest statementId = MD5Digest.compute(preparedQuery);

        String expectedQuery = "select value1, value2 from ks.cf where pk = ? and ck = ?['text', 'text']";
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.FAILED;

        List<ByteBuffer> values = createValues("text", "text");
        ImmutableList<ColumnSpecification> columns = createTextColumns("text", "text");

        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getUser()).thenReturn(mockUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);
        when(mockOptions.getValues()).thenReturn(values);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.hasColumnSpecifications()).thenReturn(true);

        when(mockAuditEntryBuilderFactory.createEntryBuilder(eq(mockStatement)))
        .thenReturn(AuditEntry.newBuilder()
                              .permissions(ImmutableSet.of(Permission.SELECT))
                              .resource(DataResource.table("ks", "cf")));

        auditAdapter.mapIdToQuery(statementId, preparedQuery);
        auditAdapter.auditPrepared(statementId, mockStatement, mockState, mockOptions, expectedStatus);

        // Capture and perform validation
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());
        verifyNoMoreInteractions(mockOptions);

        AuditEntry captured = captor.getValue();
        assertThat(captured.getClientAddress()).isEqualTo(expectedSocketAddress.getAddress());
        assertThat(captured.getOperation().getOperationString()).isEqualTo(expectedQuery);
        assertThat(captured.getUser()).isEqualTo(expectedUser);
        assertThat(captured.getStatus()).isEqualByComparingTo(expectedStatus);
        assertThat(captured.getBatchId()).isEqualTo(Optional.empty());
        assertThat(captured.getPermissions()).isEqualTo(Sets.immutableEnumSet(Permission.SELECT));
        assertThat(captured.getResource()).isEqualTo(DataResource.table("ks", "cf"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProcessBatchFailed()
    {
        BatchStatement mockBatchStatement = mock(BatchStatement.class);
        BatchQueryOptions mockBatchOptions = mock(BatchQueryOptions.class);

        UUID expectedBatchId = UUID.randomUUID();

        String expectedQuery = String.format("Apply batch failed: %s", expectedBatchId.toString());
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.FAILED;

        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getUser()).thenReturn(mockUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);

        when(mockAuditEntryBuilderFactory.createBatchEntryBuilder())
        .thenReturn(AuditEntry
                    .newBuilder()
                    .permissions(Sets.immutableEnumSet(Permission.MODIFY, Permission.SELECT))
                    .resource(DataResource.root()));

        auditAdapter.auditBatch(mockBatchStatement, expectedBatchId, mockState, mockBatchOptions, expectedStatus);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());

        List<AuditEntry> entries = captor.getAllValues();

        assertThat(entries).extracting(AuditEntry::getClientAddress).containsOnly(expectedSocketAddress.getAddress());
        assertThat(entries).extracting(AuditEntry::getUser).containsOnly(expectedUser);
        assertThat(entries).extracting(AuditEntry::getBatchId).containsOnly(Optional.of(expectedBatchId));
        assertThat(entries).extracting(AuditEntry::getStatus).containsOnly(expectedStatus);
        assertThat(entries).extracting(AuditEntry::getOperation).extracting(AuditOperation::getOperationString).containsOnly(expectedQuery);
        assertThat(entries).extracting(AuditEntry::getPermissions).containsOnly(Sets.immutableEnumSet(Permission.MODIFY, Permission.SELECT));
        assertThat(entries).extracting(AuditEntry::getResource).containsOnly(DataResource.root());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProcessBatchRegularStatements()
    {
        BatchStatement mockBatchStatement = mock(BatchStatement.class);
        BatchQueryOptions mockBatchOptions = mock(BatchQueryOptions.class);

        UUID expectedBatchId = UUID.randomUUID();

        List<Object> expectedQueries = Arrays.asList("query1", "query2", "query3");
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;

        when(mockBatchOptions.getQueryOrIdList()).thenReturn(expectedQueries);
        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getUser()).thenReturn(mockUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);

        when(mockAuditEntryBuilderFactory.createBatchEntryBuilder())
        .thenReturn(AuditEntry
                    .newBuilder()
                    .permissions(Sets.immutableEnumSet(Permission.MODIFY))
                    .resource(DataResource.root()));
        when(mockAuditEntryBuilderFactory.updateBatchEntryBuilder(any(AuditEntry.Builder.class), any(String.class), any(ClientState.class)))
        .thenAnswer(a -> a.getArgument(0));

        auditAdapter.auditBatch(mockBatchStatement, expectedBatchId, mockState, mockBatchOptions, expectedStatus);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(3)).audit(captor.capture());

        List<AuditEntry> entries = captor.getAllValues();

        assertThat(entries).extracting(AuditEntry::getClientAddress).containsOnly(expectedSocketAddress.getAddress());
        assertThat(entries).extracting(AuditEntry::getUser).containsOnly(expectedUser);
        assertThat(entries).extracting(AuditEntry::getBatchId).containsOnly(Optional.of(expectedBatchId));
        assertThat(entries).extracting(AuditEntry::getStatus).containsOnly(expectedStatus);
        assertThat(entries).extracting(AuditEntry::getOperation).extracting(AuditOperation::getOperationString).containsExactly("query1", "query2", "query3");
        assertThat(entries).extracting(AuditEntry::getPermissions).containsOnly(Sets.immutableEnumSet(Permission.MODIFY));
        assertThat(entries).extracting(AuditEntry::getResource).containsOnly(DataResource.root());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProcessBatchPreparedStatements()
    {
        ModificationStatement mockModifyStatement = mock(ModificationStatement.class);
        BatchStatement mockBatchStatement = mock(BatchStatement.class);
        BatchQueryOptions mockBatchOptions = mock(BatchQueryOptions.class);

        UUID expectedBatchId = UUID.randomUUID();

        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;

        String preparedQuery = "insert into ts.ks (id, value) values (?, ?)";
        String expectedQuery = "insert into ts.ks (id, value) values (?, ?)['hello', 'world']";
        MD5Digest id = MD5Digest.compute(preparedQuery);

        List<ByteBuffer> values = createValues("hello", "world");
        ImmutableList<ColumnSpecification> columns = createTextColumns("hello", "world");

        when(mockBatchOptions.forStatement(0)).thenReturn(mockOptions);
        when(mockOptions.getValues()).thenReturn(values);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.hasColumnSpecifications()).thenReturn(true);

        when(mockBatchStatement.getStatements()).thenReturn(Arrays.asList(mockModifyStatement));
        when(mockBatchOptions.getQueryOrIdList()).thenReturn(Arrays.asList(id));
        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getUser()).thenReturn(mockUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);

        when(mockAuditEntryBuilderFactory.createBatchEntryBuilder())
        .thenReturn(AuditEntry
                    .newBuilder()
                    .permissions(Sets.immutableEnumSet(Permission.MODIFY))
                    .resource(DataResource.root()));
        when(mockAuditEntryBuilderFactory.updateBatchEntryBuilder(any(AuditEntry.Builder.class), any(ModificationStatement.class)))
        .thenAnswer(a -> a.getArgument(0));

        auditAdapter.mapIdToQuery(id, preparedQuery);
        auditAdapter.auditBatch(mockBatchStatement, expectedBatchId, mockState, mockBatchOptions, expectedStatus);

        // Begin, prepared statement, end
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());
        verifyNoMoreInteractions(mockOptions);

        List<AuditEntry> entries = captor.getAllValues();

        assertThat(entries).extracting(AuditEntry::getClientAddress).containsOnly(expectedSocketAddress.getAddress());
        assertThat(entries).extracting(AuditEntry::getUser).containsOnly(expectedUser);
        assertThat(entries).extracting(AuditEntry::getBatchId).containsOnly(Optional.of(expectedBatchId));
        assertThat(entries).extracting(AuditEntry::getStatus).containsOnly(expectedStatus);
        assertThat(entries).extracting(AuditEntry::getOperation).extracting(AuditOperation::getOperationString).containsExactly(expectedQuery);
        assertThat(entries).extracting(AuditEntry::getPermissions).containsOnly(Sets.immutableEnumSet(Permission.MODIFY));
        assertThat(entries).extracting(AuditEntry::getResource).containsOnly(DataResource.root());
    }

    @Test
    public void testProcessAuth()
    {
        InetAddress expectedAddress = mock(InetAddress.class);
        String expectedUser = "user";
        String expectedOperation = "Authentication attempt";
        Status expectedStatus = Status.ATTEMPT;

        auditAdapter.auditAuth(expectedUser, expectedAddress, expectedStatus);

        // Capture and perform validation
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());

        AuditEntry captured = captor.getValue();
        assertThat(captured.getClientAddress()).isEqualTo(expectedAddress);
        assertThat(captured.getUser()).isEqualTo(expectedUser);
        assertThat(captured.getOperation().getOperationString()).isEqualTo(expectedOperation);
        assertThat(captured.getStatus()).isEqualTo(expectedStatus);
        assertThat(captured.getBatchId()).isEqualTo(Optional.empty());
        assertThat(captured.getPermissions()).isEqualTo(Sets.immutableEnumSet(Permission.EXECUTE));
        assertThat(captured.getResource()).isEqualTo(ConnectionResource.root());
    }

    @Test
    public void testProcessAuthFailed()
    {
        InetAddress expectedAddress = mock(InetAddress.class);
        String expectedUser = "user";
        String expectedOperation = "Authentication failed";
        Status expectedStatus = Status.FAILED;

        auditAdapter.auditAuth(expectedUser, expectedAddress, expectedStatus);

        // Capture and perform validation
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());

        AuditEntry captured = captor.getValue();
        assertThat(captured.getClientAddress()).isEqualTo(expectedAddress);
        assertThat(captured.getUser()).isEqualTo(expectedUser);
        assertThat(captured.getOperation().getOperationString()).isEqualTo(expectedOperation);
        assertThat(captured.getStatus()).isEqualTo(expectedStatus);
        assertThat(captured.getBatchId()).isEqualTo(Optional.empty());
        assertThat(captured.getPermissions()).isEqualTo(Sets.immutableEnumSet(Permission.EXECUTE));
        assertThat(captured.getResource()).isEqualTo(ConnectionResource.root());
    }

    private ImmutableList<ColumnSpecification> createTextColumns(String... columns)
    {
        ImmutableList.Builder<ColumnSpecification> builder = ImmutableList.builder();

        for (String column : columns)
        {
            ColumnIdentifier id = new ColumnIdentifier(ByteBuffer.wrap(column.getBytes()), UTF8Type.instance);
            ColumnSpecification columnSpecification = new ColumnSpecification("ks", "cf", id, UTF8Type.instance);
            builder.add(columnSpecification);
        }

        return builder.build();
    }

    private List<ByteBuffer> createValues(String... values)
    {
        List<ByteBuffer> rawValues = new ArrayList<>();

        for (String value : values)
        {
            ByteBuffer rawValue = ByteBuffer.wrap(value.getBytes());
            rawValues.add(rawValue);
        }

        return rawValues;
    }
}
