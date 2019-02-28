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
import java.util.UUID;

import com.google.common.collect.ImmutableSet;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.VersionedValue;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSchemaHelper
{
    @Mock
    private Gossiper mockGossiper;

    private SchemaHelper schemaHelper;

    private EndpointState localEndpointState;
    private EndpointState anotherEndpointState;

    private InetAddress remoteEndpoint;

    @BeforeClass
    public static void beforeClass()
    {
        Config.setClientMode(true);
    }

    @Before
    public void before() throws Exception
    {
        InetAddress localEndpoint = InetAddress.getLoopbackAddress();
        localEndpointState = createEndpointStateWithSchema();
        when(mockGossiper.getEndpointStateForEndpoint(eq(localEndpoint))).thenReturn(localEndpointState);

        remoteEndpoint = InetAddress.getByName("127.0.0.2");

        anotherEndpointState = createEndpointStateWithSchema();

        when(mockGossiper.getLiveMembers()).thenReturn(ImmutableSet.of(localEndpoint, remoteEndpoint));

        schemaHelper = new SchemaHelper(localEndpoint, mockGossiper);
    }

    @AfterClass
    public static void afterClass()
    {
        Config.setClientMode(false);
    }

    @Test(timeout = 999)
    public void testSyncedFromStart()
    {
        when(mockGossiper.getEndpointStateForEndpoint(eq(remoteEndpoint)))
        .thenReturn(localEndpointState);

        schemaHelper.waitForSchemaAlignment(10);
    }

    @Test(timeout = 1999)
    public void testSyncedAfterOneSecond()
    {
        when(mockGossiper.getEndpointStateForEndpoint(eq(remoteEndpoint)))
        .thenReturn(anotherEndpointState)
        .thenReturn(localEndpointState);

        long startTime = System.currentTimeMillis();

        schemaHelper.waitForSchemaAlignment(10);

        assertWaitTimeInMillis(startTime, 1000L);
    }

    @Test(timeout = 1999)
    public void testSyncTimeout()
    {
        when(mockGossiper.getEndpointStateForEndpoint(eq(remoteEndpoint)))
        .thenReturn(anotherEndpointState);

        long startTime = System.currentTimeMillis();

        schemaHelper.waitForSchemaAlignment(1);

        assertWaitTimeInMillis(startTime, 1000L);
    }

    private EndpointState createEndpointStateWithSchema()
    {
        VersionedValue versionedValue = new VersionedValue.VersionedValueFactory(null).schema(UUID.randomUUID());

        EndpointState endpointState = mock(EndpointState.class);
        when(endpointState.getApplicationState(eq(ApplicationState.SCHEMA))).thenReturn(versionedValue);

        return endpointState;
    }

    private void assertWaitTimeInMillis(long startTime, long delay)
    {
        long now = System.currentTimeMillis();
        assertThat(now - startTime).isGreaterThan(delay);
    }
}
