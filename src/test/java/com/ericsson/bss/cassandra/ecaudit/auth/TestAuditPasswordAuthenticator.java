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
package com.ericsson.bss.cassandra.ecaudit.auth;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.auth.IAuthenticator.SaslNegotiator;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.entry.Status;
import org.mockito.stubbing.OngoingStubbing;

@RunWith(MockitoJUnitRunner.class)
public class TestAuditPasswordAuthenticator
{
    @Mock
    IAuthenticator mockAuthenticator;

    @Mock
    AuditAdapter mockAdapter;

    @Mock
    SaslNegotiator mockNegotiator;

    @InjectMocks
    AuditPasswordAuthenticator authenticator;

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockAdapter);
    }

    @Test(expected = RuntimeException.class)
    public void testLogOnRuntimeException() throws Exception
    {
        InetAddress clientAddress = InetAddress.getLocalHost();
        when(mockAuthenticator.newSaslNegotiator(any(InetAddress.class))).thenReturn(mockNegotiator);
        whenGetAuthUserThrowRuntimeException();

        SaslNegotiator negotiator = authenticator.newSaslNegotiator(clientAddress);

        byte[] clientResponse = createClientResponse("username", "secretpassword");
        negotiator.evaluateResponse(clientResponse);

        try
        {
            negotiator.getAuthenticatedUser();
        }
        finally
        {
            verify(mockAdapter, times(1)).auditAuth(eq("username"), eq(clientAddress), eq(Status.ATTEMPT));
            verify(mockAdapter, times(1)).auditAuth(eq("username"), eq(clientAddress), eq(Status.FAILED));
        }
    }

    @Test(expected = AuthenticationException.class)
    public void testLogOnFailure() throws Exception
    {
        InetAddress clientAddress = InetAddress.getLocalHost();
        when(mockAuthenticator.newSaslNegotiator(any(InetAddress.class))).thenReturn(mockNegotiator);
        whenGetAuthUserThrowAuthException();

        SaslNegotiator negotiator = authenticator.newSaslNegotiator(clientAddress);

        byte[] clientResponse = createClientResponse("username", "secretpassword");
        negotiator.evaluateResponse(clientResponse);

        try
        {
            negotiator.getAuthenticatedUser();
        }
        finally
        {
            verify(mockAdapter, times(1)).auditAuth(eq("username"), eq(clientAddress), eq(Status.ATTEMPT));
            verify(mockAdapter, times(1)).auditAuth(eq("username"), eq(clientAddress), eq(Status.FAILED));
        }
    }

    @Test
    public void testLogOnSuccess() throws Exception
    {
        InetAddress clientAddress = InetAddress.getLocalHost();
        when(mockAuthenticator.newSaslNegotiator(any(InetAddress.class))).thenReturn(mockNegotiator);

        SaslNegotiator negotiator = authenticator.newSaslNegotiator(clientAddress);

        byte[] clientResponse = createClientResponse("username", "secretpassword");
        negotiator.evaluateResponse(clientResponse);

        negotiator.getAuthenticatedUser();
        verify(mockAdapter, times(1)).auditAuth(eq("username"), eq(clientAddress), eq(Status.ATTEMPT));
    }

    @SuppressWarnings("unchecked")
    private void whenGetAuthUserThrowRuntimeException() {
        when(mockNegotiator.getAuthenticatedUser()).thenThrow(RuntimeException.class);
    }

    @SuppressWarnings("unchecked")
    private void whenGetAuthUserThrowAuthException() {
        when(mockNegotiator.getAuthenticatedUser()).thenThrow(AuthenticationException.class);
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
