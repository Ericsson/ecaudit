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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ClientInitializer
{
    static private IAuthorizer authorizerMock = mock(IAuthorizer.class);
    static private INetworkAuthorizer networkAuthorizerMock = mock(INetworkAuthorizer.class);

	private ClientInitializer()
    {
    }

    public static void beforeClass()
    {
        DatabaseDescriptor.clientInitialization(true);
        DatabaseDescriptor.setAuthenticator(mock(IAuthenticator.class));
        when(authorizerMock.bulkLoader()).thenReturn(Collections::emptyMap);
        DatabaseDescriptor.setAuthorizer(authorizerMock);
        when(networkAuthorizerMock.bulkLoader()).thenReturn(Collections::emptyMap);
        DatabaseDescriptor.setNetworkAuthorizer(networkAuthorizerMock);
    }

    public static void afterClass()
    {
        DatabaseDescriptor.setNetworkAuthorizer(null);
        DatabaseDescriptor.setAuthorizer(null);
        DatabaseDescriptor.setAuthenticator(null);
        DatabaseDescriptor.clientInitialization(false);
    }
}
