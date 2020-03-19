/*
 * Copyright 2020 Telefonaktiebolaget LM Ericsson
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.auth.IDecoratedAuthenticator.DecoratedSaslNegotiator;
import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IAuthenticator.SaslNegotiator;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.PasswordAuthenticator;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestDecoratedPasswordAuthenticator
{
    @Mock
    private PasswordAuthenticator mockPasswordAuthenticator;

    @Mock
    private SaslNegotiator mockNegotiator;

    private DecoratedPasswordAuthenticator authenticator;

    @BeforeClass
    public static void beforeClass()
    {
        ClientInitializer.beforeClass();
    }

    @Before
    public void before()
    {
        authenticator = new DecoratedPasswordAuthenticator(mockPasswordAuthenticator);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockPasswordAuthenticator, mockNegotiator);
    }

    @AfterClass
    public static void afterClass()
    {
        ClientInitializer.afterClass();
    }

    @Test
    public void testRequireAuthDelegation()
    {
        when(mockPasswordAuthenticator.requireAuthentication()).thenReturn(true);
        assertThat(authenticator.requireAuthentication()).isEqualTo(true);
        verify(mockPasswordAuthenticator, times(1)).requireAuthentication();
    }

    @Test
    public void testProtectedResourcesDelegation()
    {
        Set<IResource> expectedResources = Collections.singleton(DataResource.fromName("data"));
        doReturn(expectedResources).when(mockPasswordAuthenticator).protectedResources();
        assertThat(authenticator.protectedResources()).isEqualTo(expectedResources);
        verify(mockPasswordAuthenticator, times(1)).protectedResources();
    }

    @Test
    public void testValidateConfigurationDelegation()
    {
        authenticator.validateConfiguration();
        verify(mockPasswordAuthenticator, times(1)).validateConfiguration();
    }

    @Test
    public void testSetupDelegation()
    {
        authenticator.setup();
        verify(mockPasswordAuthenticator, times(1)).setup();
    }

    @Test
    public void testOptions()
    {
        assertThat(authenticator.supportedOptions()).isEqualTo(Collections.singleton(IRoleManager.Option.PASSWORD));
        assertThat(authenticator.alterableOptions()).isEqualTo(Collections.singleton(IRoleManager.Option.PASSWORD));
    }

    @Test
    public void testAuthenticationSuccess()
    {
        when(mockPasswordAuthenticator.newSaslNegotiator()).thenReturn(mockNegotiator);

        DecoratedSaslNegotiator negotiator = authenticator.newDecoratedSaslNegotiator();

        byte[] clientResponse = createClientResponse("user1", "password1");

        negotiator.evaluateResponse(clientResponse);
        verify(mockNegotiator, times(1)).evaluateResponse(eq(clientResponse));

        negotiator.isComplete();
        verify(mockNegotiator, times(1)).isComplete();

        negotiator.getAuthenticatedUser();
        verify(mockNegotiator, times(1)).getAuthenticatedUser();

        assertThat(negotiator.getUser()).isEqualTo("user1");
        assertThat(negotiator.getSubject()).isEmpty();
    }

    @Test
    public void testAuthenticationFailure()
    {
        when(mockPasswordAuthenticator.newSaslNegotiator()).thenReturn(mockNegotiator);
        when(mockNegotiator.getAuthenticatedUser()).thenThrow(AuthenticationException.class);

        DecoratedSaslNegotiator negotiator = authenticator.newDecoratedSaslNegotiator();

        byte[] clientResponse = createClientResponse("user2", "password2");

        negotiator.evaluateResponse(clientResponse);
        verify(mockNegotiator, times(1)).evaluateResponse(eq(clientResponse));

        negotiator.isComplete();
        verify(mockNegotiator, times(1)).isComplete();

        assertThatExceptionOfType(AuthenticationException.class)
        .isThrownBy(negotiator::getAuthenticatedUser);
        verify(mockNegotiator, times(1)).getAuthenticatedUser();

        assertThat(negotiator.getUser()).isEqualTo("user2");
        assertThat(negotiator.getSubject()).isEmpty();
    }

    @Test
    public void testVanillaAuthenticationSuccess()
    {
        when(mockPasswordAuthenticator.newSaslNegotiator()).thenReturn(mockNegotiator);

        SaslNegotiator negotiator = authenticator.newSaslNegotiator();

        byte[] clientResponse = createClientResponse("user1", "password1");

        negotiator.evaluateResponse(clientResponse);
        verify(mockNegotiator, times(1)).evaluateResponse(eq(clientResponse));

        negotiator.isComplete();
        verify(mockNegotiator, times(1)).isComplete();

        negotiator.getAuthenticatedUser();
        verify(mockNegotiator, times(1)).getAuthenticatedUser();
    }

    @Test
    public void testLegacyAuthDelegation()
    {
        Map<String, String> expectedCredentials = new HashMap<>();
        expectedCredentials.put("user", "password");

        AuthenticatedUser expectedUser = mock(AuthenticatedUser.class);
        when(mockPasswordAuthenticator.legacyAuthenticate(eq(expectedCredentials))).thenReturn(expectedUser);

        AuthenticatedUser actualUser = authenticator.legacyAuthenticate(expectedCredentials);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPasswordAuthenticator, times(1)).legacyAuthenticate(captor.capture());

        Map<String, String> actualCredentials = captor.getValue();
        assertThat(actualCredentials).isEqualTo(expectedCredentials);
        assertThat(expectedUser).isEqualTo(actualUser);
    }

    /**
     * Create a client response as SASL encoded bytes
     *
     * @param username the user name
     * @param password the user password
     * @return a plain-sasl encoded byte buffer
     */
    private static byte[] createClientResponse(String username, String password)
    {
        // Encoded format is: <NUL>username<NUL>password
        final int capacity = 1 + username.length() + 1 + password.length();
        byte[] data = new byte[capacity];

        ByteBuffer bb = ByteBuffer.allocate(capacity);
        bb.put((byte) 0x00); // <NUL>
        bb.put(username.getBytes(StandardCharsets.UTF_8)); // username
        bb.put((byte) 0x00); // <NUL>
        bb.put(password.getBytes(StandardCharsets.UTF_8)); // password

        // Get the data as a byte array
        bb.rewind();
        bb.get(data);
        return data;
    }
}
