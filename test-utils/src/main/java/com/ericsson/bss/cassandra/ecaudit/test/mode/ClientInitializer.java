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

import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.INetworkAuthorizer;
import org.apache.cassandra.config.DatabaseDescriptor;

import static org.mockito.Mockito.mock;

public final class ClientInitializer
{
    private ClientInitializer()
    {
    }

    public static void beforeClass()
    {
        DatabaseDescriptor.clientInitialization(true);
        DatabaseDescriptor.setAuthenticator(mock(IAuthenticator.class));
        DatabaseDescriptor.setAuthorizer(mock(IAuthorizer.class));
        DatabaseDescriptor.setNetworkAuthorizer(mock(INetworkAuthorizer.class));
    }

    public static void afterClass()
    {
        DatabaseDescriptor.setNetworkAuthorizer(null);
        DatabaseDescriptor.setAuthorizer(null);
        DatabaseDescriptor.setAuthenticator(null);
        DatabaseDescriptor.clientInitialization(false);
    }
}
