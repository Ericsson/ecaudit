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
package com.ericsson.bss.cassandra.ecaudit.test.mode;

import java.util.Collections;

import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.INetworkAuthorizer;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.dht.RandomPartitioner;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public final class ClientInitializer
{
    private static IAuthorizer authorizerMock = mock(IAuthorizer.class);
    private static INetworkAuthorizer networkAuthorizerMock = mock(INetworkAuthorizer.class);

    private ClientInitializer()
    {
    }

    public static void beforeClass()
    {
        // This method is used by multiple tests, so lenient() is required for tests that don't require the when statements
        DatabaseDescriptor.clientInitialization(true);
        DatabaseDescriptor.setAuthenticator(mock(IAuthenticator.class));
        lenient().when(authorizerMock.bulkLoader()).thenReturn(Collections::emptyMap);
        DatabaseDescriptor.setAuthorizer(authorizerMock);
        lenient().when(networkAuthorizerMock.bulkLoader()).thenReturn(Collections::emptyMap);
        DatabaseDescriptor.setNetworkAuthorizer(networkAuthorizerMock);
        DatabaseDescriptor.setPartitionerUnsafe(RandomPartitioner.instance);
    }

    public static void afterClass()
    {
        // We should really restore the old value here but the default value is null and
        // setPartitionerUnsafe does not allow null so there isn't mush we can do
        //DatabaseDescriptor.setPartitionerUnsafe(null);
        DatabaseDescriptor.setNetworkAuthorizer(null);
        DatabaseDescriptor.setAuthorizer(null);
        DatabaseDescriptor.setAuthenticator(null);
        DatabaseDescriptor.clientInitialization(false);
    }
}
