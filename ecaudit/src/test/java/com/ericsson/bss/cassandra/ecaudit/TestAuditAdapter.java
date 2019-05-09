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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import com.ericsson.bss.cassandra.ecaudit.common.record.AuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.factory.AuditEntryBuilderFactory;
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
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.apache.cassandra.exceptions.ReadTimeoutException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.MD5Digest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestAuditAdapter
{
    private static final long TIMESTAMP = 42L;
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
    private LoggingStrategy mockLoggingStrategy;

    @Mock
    private AuditEntryBuilderFactory mockAuditEntryBuilderFactory;

    @Mock
    private BatchStatement mockBatchStatement;

    @Mock
    private BatchQueryOptions mockBatchOptions;

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
        auditAdapter = new AuditAdapter(mockAuditor, mockAuditEntryBuilderFactory, mockLoggingStrategy);
        when(mockState.getUser()).thenReturn(mockUser);
        when(mockLoggingStrategy.logStatus(any(Status.class))).thenReturn(true);
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
    public void testSetupDelegation()
    {
        auditAdapter.setup();
        verify(mockAuditor, times(1)).setup();
    }

    @Test
    public void testProcessRegular()
    {
        // Given
        String expectedStatement = "select * from ks.tbl";
        InetSocketAddress expectedSocketAddress = InetSocketAddress.createUnresolved("localhost", 0);
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;
        DataResource expectedResource = DataResource.table("ks", "tbl");
        ImmutableSet<Permission> expectedPermissions = ImmutableSet.of(Permission.SELECT);

        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);
        AuditEntry.Builder entryBuilder = AuditEntry.newBuilder()
                                                    .permissions(expectedPermissions)
                                                    .resource(expectedResource);
        when(mockAuditEntryBuilderFactory.createEntryBuilder(eq(expectedStatement), eq(mockState))).thenReturn(entryBuilder);

        // When
        auditAdapter.auditRegular(expectedStatement, mockState, expectedStatus, TIMESTAMP);

        // Then
        AuditEntry entry = getAuditEntry();
        assertThat(entry.getClientAddress()).isEqualTo(expectedSocketAddress.getAddress());
        assertThat(entry.getCoordinatorAddress()).isEqualTo(FBUtilities.getBroadcastAddress());
        assertThat(entry.getOperation().getOperationString()).isEqualTo(expectedStatement);
        assertThat(entry.getUser()).isEqualTo(expectedUser);
        assertThat(entry.getStatus()).isEqualTo(expectedStatus);
        assertThat(entry.getBatchId()).isEmpty();
        assertThat(entry.getResource()).isEqualTo(expectedResource);
        assertThat(entry.getTimestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    public void testProcessRegularNoLoggingStrategy()
    {
        // Given
        when(mockLoggingStrategy.logStatus(any(Status.class))).thenReturn(false);
        // When
        auditAdapter.auditRegular("select * from ks.tbl", mockState, Status.ATTEMPT, TIMESTAMP);
        // Then
        verifyNoMoreInteractions(mockAuditor, mockAuditEntryBuilderFactory);
    }

    @Test
    public void testProcessPrepared()
    {
        // Given
        String preparedQuery = "select value1, value2 from ks.cf where pk = ? and ck = ?";
        MD5Digest statementId = MD5Digest.compute(preparedQuery);

        String expectedQuery = "select value1, value2 from ks.cf where pk = ? and ck = ?['text', 'text']";
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;

        List<ByteBuffer> values = createValues("text", "text");
        ImmutableList<ColumnSpecification> columns = createTextColumns("text", "text");
        ImmutableSet<Permission> expectedPermissions = ImmutableSet.of(Permission.SELECT);
        DataResource expectedDataResource = DataResource.table("ks", "cf");

        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);
        when(mockOptions.getValues()).thenReturn(values);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.hasColumnSpecifications()).thenReturn(true);

        AuditEntry.Builder entryBuilder = AuditEntry.newBuilder()
                                                    .permissions(expectedPermissions)
                                                    .resource(expectedDataResource);
        when(mockAuditEntryBuilderFactory.createEntryBuilder(eq(mockStatement))).thenReturn(entryBuilder);

        // When
        auditAdapter.mapIdToQuery(statementId, preparedQuery);
        auditAdapter.auditPrepared(statementId, mockStatement, mockState, mockOptions, expectedStatus, TIMESTAMP);

        // Then
        verifyNoMoreInteractions(mockOptions);

        AuditEntry entry = getAuditEntry();
        assertThat(entry.getClientAddress()).isEqualTo(expectedSocketAddress.getAddress());
        assertThat(entry.getCoordinatorAddress()).isEqualTo(FBUtilities.getBroadcastAddress());
        assertThat(entry.getOperation().getOperationString()).isEqualTo(expectedQuery);
        assertThat(entry.getUser()).isEqualTo(expectedUser);
        assertThat(entry.getStatus()).isEqualByComparingTo(expectedStatus);
        assertThat(entry.getBatchId()).isEqualTo(Optional.empty());
        assertThat(entry.getPermissions()).isEqualTo(expectedPermissions);
        assertThat(entry.getResource()).isEqualTo(expectedDataResource);
        assertThat(entry.getTimestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    public void testProcessPreparedNoLoggingStrategy()
    {
        // Given
        when(mockLoggingStrategy.logStatus(any(Status.class))).thenReturn(false);
        // When
        auditAdapter.auditPrepared(mock(MD5Digest.class), mockStatement, mockState, mockOptions, Status.ATTEMPT, TIMESTAMP);
        // Then
        verifyNoMoreInteractions(mockAuditor, mockAuditEntryBuilderFactory);
    }

    @Test
    public void testProcessBatchWithLogSummaryStrategy()
    {
        // Given
        when(mockLoggingStrategy.logBatchSummary(any(Status.class))).thenReturn(true);

        UUID expectedBatchId = UUID.randomUUID();
        String expectedQuery = String.format("Apply batch failed: %s", expectedBatchId.toString());
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.FAILED;
        ImmutableSet<Permission> expectedPermissions = Sets.immutableEnumSet(Permission.MODIFY, Permission.SELECT);
        DataResource expectedResource = DataResource.root();

        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);
        AuditEntry.Builder entryBuilder = AuditEntry.newBuilder()
                                                    .permissions(expectedPermissions)
                                                    .resource(expectedResource);
        when(mockAuditEntryBuilderFactory.createBatchEntryBuilder()).thenReturn(entryBuilder);

        // When
        auditAdapter.auditBatch(mockBatchStatement, expectedBatchId, mockState, mockBatchOptions, expectedStatus, TIMESTAMP);

        // Then
        AuditEntry entry = getAuditEntry();
        assertThat(entry.getClientAddress()).isEqualTo(expectedSocketAddress.getAddress());
        assertThat(entry.getCoordinatorAddress()).isEqualTo(FBUtilities.getBroadcastAddress());
        assertThat(entry.getUser()).isEqualTo(expectedUser);
        assertThat(entry.getBatchId()).contains(expectedBatchId);
        assertThat(entry.getStatus()).isEqualByComparingTo(expectedStatus);
        assertThat(entry.getOperation().getOperationString()).isEqualTo(expectedQuery);
        assertThat(entry.getPermissions()).isEqualTo(expectedPermissions);
        assertThat(entry.getResource()).isEqualTo(expectedResource);
        assertThat(entry.getTimestamp()).isEqualTo(TIMESTAMP);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProcessBatchRegularStatements()
    {
        // Given
        UUID expectedBatchId = UUID.randomUUID();
        List<Object> expectedQueries = Arrays.asList("query1", "query2", "query3");
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;
        ImmutableSet<Permission> expectedPermissions = Sets.immutableEnumSet(Permission.MODIFY);
        DataResource expectedResource = DataResource.root();

        when(mockBatchOptions.getQueryOrIdList()).thenReturn(expectedQueries);
        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);
        AuditEntry.Builder entryBuilder = AuditEntry.newBuilder()
                                                    .permissions(expectedPermissions)
                                                    .resource(expectedResource);
        when(mockAuditEntryBuilderFactory.createBatchEntryBuilder()).thenReturn(entryBuilder);

        // When
        auditAdapter.auditBatch(mockBatchStatement, expectedBatchId, mockState, mockBatchOptions, expectedStatus, TIMESTAMP);

        // Then
        List<AuditEntry> entries = getAuditEntries(3);
        assertThat(entries).extracting(AuditEntry::getClientAddress).containsOnly(expectedSocketAddress.getAddress());
        assertThat(entries).extracting(AuditEntry::getUser).containsOnly(expectedUser);
        assertThat(entries).extracting(AuditEntry::getBatchId).containsOnly(Optional.of(expectedBatchId));
        assertThat(entries).extracting(AuditEntry::getStatus).containsOnly(expectedStatus);
        assertThat(entries).extracting(AuditEntry::getOperation).extracting(AuditOperation::getOperationString).containsExactly("query1", "query2", "query3");
        assertThat(entries).extracting(AuditEntry::getPermissions).containsOnly(expectedPermissions);
        assertThat(entries).extracting(AuditEntry::getResource).containsOnly(expectedResource);
        assertThat(entries).extracting(AuditEntry::getTimestamp).containsOnly(TIMESTAMP);
    }

    @Test
    public void testProcessBatchPreparedStatements()
    {
        // Given
        ModificationStatement mockModifyStatement = mock(ModificationStatement.class);
        UUID expectedBatchId = UUID.randomUUID();
        InetSocketAddress expectedSocketAddress = spy(InetSocketAddress.createUnresolved("localhost", 0));
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;
        String preparedQuery = "insert into ts.ks (id, value) values (?, ?)";
        String expectedQuery = "insert into ts.ks (id, value) values (?, ?)['hello', 'world']";
        MD5Digest id = MD5Digest.compute(preparedQuery);
        ImmutableSet<Permission> expectedPermissions = Sets.immutableEnumSet(Permission.MODIFY);

        List<ByteBuffer> values = createValues("hello", "world");
        ImmutableList<ColumnSpecification> columns = createTextColumns("hello", "world");
        DataResource expectedResource = DataResource.root();

        when(mockBatchOptions.forStatement(0)).thenReturn(mockOptions);
        when(mockOptions.getValues()).thenReturn(values);
        when(mockOptions.getColumnSpecifications()).thenReturn(columns);
        when(mockOptions.hasColumnSpecifications()).thenReturn(true);

        when(mockBatchStatement.getStatements()).thenReturn(Collections.singletonList(mockModifyStatement));
        when(mockBatchOptions.getQueryOrIdList()).thenReturn(Collections.singletonList(id));
        when(mockUser.getName()).thenReturn(expectedUser);
        when(mockState.getRemoteAddress()).thenReturn(expectedSocketAddress);

        AuditEntry.Builder entryBuilder = AuditEntry.newBuilder()
                                                    .permissions(expectedPermissions)
                                                    .resource(expectedResource);
        when(mockAuditEntryBuilderFactory.createBatchEntryBuilder()).thenReturn(entryBuilder);

        // When
        auditAdapter.mapIdToQuery(id, preparedQuery);
        auditAdapter.auditBatch(mockBatchStatement, expectedBatchId, mockState, mockBatchOptions, expectedStatus, TIMESTAMP);

        // Then
        verifyNoMoreInteractions(mockOptions);
        AuditEntry entry = getAuditEntry();
        assertThat(entry.getClientAddress()).isEqualTo(expectedSocketAddress.getAddress());
        assertThat(entry.getCoordinatorAddress()).isEqualTo(FBUtilities.getBroadcastAddress());
        assertThat(entry.getUser()).isEqualTo(expectedUser);
        assertThat(entry.getBatchId()).contains(expectedBatchId);
        assertThat(entry.getStatus()).isEqualByComparingTo(expectedStatus);
        assertThat(entry.getOperation().getOperationString()).isEqualTo(expectedQuery);
        assertThat(entry.getPermissions()).isEqualTo(expectedPermissions);
        assertThat(entry.getResource()).isEqualTo(expectedResource);
        assertThat(entry.getTimestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    public void testProcessBatchNoLoggingStrategy()
    {
        // Given
        when(mockLoggingStrategy.logStatus(any(Status.class))).thenReturn(false);
        // When
        auditAdapter.auditBatch(mock(BatchStatement.class), mock(UUID.class), mockState, mock(BatchQueryOptions.class), Status.ATTEMPT, TIMESTAMP);
        // Then
        verifyNoMoreInteractions(mockAuditor, mockAuditEntryBuilderFactory);
    }

    @Test
    public void testProcessAuth()
    {
        // Given
        InetAddress expectedAddress = mock(InetAddress.class);
        String expectedUser = "user";
        String expectedOperation = "Authentication attempt";
        Status expectedStatus = Status.ATTEMPT;
        ImmutableSet<Permission> expectedPermissions = ImmutableSet.of(Permission.EXECUTE);
        ConnectionResource resource = ConnectionResource.root();
        AuditEntry.Builder auditBuilder = AuditEntry.newBuilder()
                                                    .permissions(expectedPermissions)
                                                    .resource(resource);
        when(mockAuditEntryBuilderFactory.createAuthenticationEntryBuilder()).thenReturn(auditBuilder);

        // When
        auditAdapter.auditAuth(expectedUser, expectedAddress, expectedStatus, TIMESTAMP);

        // Then
        AuditEntry entry = getAuditEntry();
        assertThat(entry.getClientAddress()).isEqualTo(expectedAddress);
        assertThat(entry.getCoordinatorAddress()).isEqualTo(FBUtilities.getBroadcastAddress());
        assertThat(entry.getUser()).isEqualTo(expectedUser);
        assertThat(entry.getOperation().getOperationString()).isEqualTo(expectedOperation);
        assertThat(entry.getStatus()).isEqualTo(expectedStatus);
        assertThat(entry.getBatchId()).isEmpty();
        assertThat(entry.getPermissions()).isEqualTo(expectedPermissions);
        assertThat(entry.getResource()).isEqualTo(resource);
        assertThat(entry.getTimestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    public void testProcessAuthNoLoggingStrategy()
    {
        // Given
        when(mockLoggingStrategy.logStatus(any(Status.class))).thenReturn(false);
        // When
        auditAdapter.auditAuth("user", mock(InetAddress.class), Status.ATTEMPT, TIMESTAMP);
        // Then
        verifyNoMoreInteractions(mockAuditor, mockAuditEntryBuilderFactory);
    }

    @Test
    public void testProcessAuthException()
    {
        InetAddress expectedAddress = mock(InetAddress.class);
        String expectedUser = "user";
        Status expectedStatus = Status.ATTEMPT;

        when(mockAuditEntryBuilderFactory.createAuthenticationEntryBuilder())
        .thenReturn(AuditEntry.newBuilder()
                              .permissions(ImmutableSet.of(Permission.EXECUTE))
                              .resource(ConnectionResource.root()));

        doThrow(new ReadTimeoutException(ConsistencyLevel.QUORUM, 3, 4, true)).when(mockAuditor).audit(any(AuditEntry.class));

        assertThatExceptionOfType(AuthenticationException.class)
        .isThrownBy(() -> auditAdapter.auditAuth(expectedUser, expectedAddress, expectedStatus, TIMESTAMP));
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

    private AuditEntry getAuditEntry()
    {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(1)).audit(captor.capture());

        return captor.getValue();
    }

    private List<AuditEntry> getAuditEntries(int expectedNumberOfEntries)
    {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mockAuditor, times(expectedNumberOfEntries)).audit(captor.capture());

        return captor.getAllValues();
    }
}
