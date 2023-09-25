/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import com.google.common.collect.ImmutableSet;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;

import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.VersionedValue;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSchemaHelper
{
    private static final long RETRY_INTERVAL_MILLIS = 200;

    @Mock
    private Gossiper mockGossiper;

    private SchemaHelper schemaHelper;

    private EndpointState currentEndpointState;
    private EndpointState previousEndpointState;

    @BeforeClass
    public static void beforeClass()
    {
        ClientInitializer.beforeClass();
    }

    @Before
    public void before() throws Exception
    {
        currentEndpointState = createEndpointStateWithSchema();
        previousEndpointState = createEndpointStateWithSchema();

        InetAddressAndPort localEndpoint = InetAddressAndPort.getByAddress(InetAddress.getLoopbackAddress());
        InetAddressAndPort remoteEndpoint = InetAddressAndPort.getByAddress(InetAddress.getByName("127.0.0.2"));
        when(mockGossiper.getLiveMembers()).thenReturn(ImmutableSet.of(localEndpoint, remoteEndpoint));

        schemaHelper = new SchemaHelper(localEndpoint, mockGossiper, RETRY_INTERVAL_MILLIS);
    }

    @AfterClass
    public static void afterClass()
    {
        ClientInitializer.afterClass();
    }

    @Test(timeout = (RETRY_INTERVAL_MILLIS) - 1)
    public void testSyncedFromStart() throws UnknownHostException
    {
        givenLocalStateOrder(currentEndpointState);
        givenRemoteStateOrder(currentEndpointState);

        assertThat(schemaHelper.areSchemasAligned(10 * RETRY_INTERVAL_MILLIS)).isTrue();
    }

    @Test(timeout = (3 * RETRY_INTERVAL_MILLIS) - 1)
    public void testRemoveSyncedAfterTwoTries() throws UnknownHostException
    {
        givenLocalStateOrder(currentEndpointState);
        givenRemoteStateOrder(null, previousEndpointState, currentEndpointState);

        long startTime = System.currentTimeMillis();

        assertThat(schemaHelper.areSchemasAligned(10 * RETRY_INTERVAL_MILLIS)).isTrue();

        assertWaitTimeInMillis(startTime, 2 * RETRY_INTERVAL_MILLIS);
    }

    @Test(timeout = (2 * RETRY_INTERVAL_MILLIS) - 1)
    public void testSyncTimeout() throws UnknownHostException
    {
        givenLocalStateOrder(currentEndpointState);
        givenRemoteStateOrder(previousEndpointState);

        long startTime = System.currentTimeMillis();

        assertThat(schemaHelper.areSchemasAligned(RETRY_INTERVAL_MILLIS)).isFalse();

        assertWaitTimeInMillis(startTime, RETRY_INTERVAL_MILLIS);
    }

    @Test(timeout = (3 * RETRY_INTERVAL_MILLIS) - 1)
    public void testNoLocalSchemaInFirstTry() throws UnknownHostException
    {
        givenLocalStateOrder(null, currentEndpointState);
        givenRemoteStateOrder(currentEndpointState);

        long startTime = System.currentTimeMillis();

        assertThat(schemaHelper.areSchemasAligned(10 * RETRY_INTERVAL_MILLIS)).isTrue();

        assertWaitTimeInMillis(startTime, RETRY_INTERVAL_MILLIS);
    }

    private void givenLocalStateOrder(EndpointState endpointState, EndpointState... endpointStates)
    {
        InetAddressAndPort localEndpoint = InetAddressAndPort.getByAddress(InetAddress.getLoopbackAddress());
        when(mockGossiper.getEndpointStateForEndpoint(eq(localEndpoint)))
        .thenReturn(endpointState, endpointStates);
    }

    private void givenRemoteStateOrder(EndpointState endpointState, EndpointState... endpointStates) throws UnknownHostException
    {
        InetAddressAndPort remoteEndpoint = InetAddressAndPort.getByAddress(InetAddress.getByName("127.0.0.2"));
        when(mockGossiper.getEndpointStateForEndpoint(eq(remoteEndpoint)))
        .thenReturn(endpointState, endpointStates);
    }

    private EndpointState createEndpointStateWithSchema()
    {
        VersionedValue versionedValue = new VersionedValue.VersionedValueFactory(null).schema(UUID.randomUUID());

        EndpointState endpointState = mock(EndpointState.class);
        when(endpointState.getApplicationState(eq(ApplicationState.SCHEMA))).thenReturn(versionedValue);

        return endpointState;
    }

    private void assertWaitTimeInMillis(long startTime, long millis)
    {
        long now = System.currentTimeMillis();
        assertThat(now - startTime).isGreaterThanOrEqualTo(millis);
    }
}
