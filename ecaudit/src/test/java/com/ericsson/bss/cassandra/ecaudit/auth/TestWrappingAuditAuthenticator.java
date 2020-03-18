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

import java.util.Collections;
import java.util.Optional;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;
import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestWrappingAuditAuthenticator
{
    @Mock
    IAuditAuthenticator mockAuthenticator;

    @Mock
    AuditAdapter mockAuditAdapter;

    @Mock
    AuditConfig mockConfig;

    @Mock
    IAuditAuthenticator.AuditSaslNegotiator mockSaslNegotiator;

    @InjectMocks
    WrappingAuditAuthenticator authenticator;

    @BeforeClass
    public static void beforeClass()
    {
        ClientInitializer.beforeClass();
    }

    @Before
    public void setup()
    {
        when(mockAuthenticator.newAuditSaslNegotiator()).thenReturn(mockSaslNegotiator);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockAuthenticator, mockAuditAdapter);
    }

    @AfterClass
    public static void afterClass()
    {
        ClientInitializer.afterClass();
    }

    @Test
    public void testWrappedMethodsAreDelegated()
    {
        authenticator.setup();
        authenticator.protectedResources();
        authenticator.requireAuthentication();
        authenticator.validateConfiguration();
        authenticator.legacyAuthenticate(Collections.emptyMap());
        authenticator.alterableOptions();
        authenticator.supportedOptions();

        verify(mockAuthenticator, times(1)).setup();
        verify(mockAuthenticator, times(1)).protectedResources();
        verify(mockAuthenticator, times(1)).requireAuthentication();
        verify(mockAuthenticator, times(1)).validateConfiguration();
        verify(mockAuthenticator, times(1)).legacyAuthenticate(eq(Collections.emptyMap()));
        verify(mockAuthenticator, times(1)).alterableOptions();
        verify(mockAuthenticator, times(1)).supportedOptions();

        IAuthenticator.SaslNegotiator negotiator = authenticator.newSaslNegotiator();
        negotiator.evaluateResponse(new byte[]{});
        negotiator.isComplete();

        verify(mockSaslNegotiator, times(1)).evaluateResponse(eq(new byte[] {}));
        verify(mockSaslNegotiator, times(1)).isComplete();
    }

    @Test
    public void testWhenUserIsProviedAuthenticationAttemptIsLogged()
    {
        AuthenticatedUser expected = mock(AuthenticatedUser.class);
        when(mockSaslNegotiator.getAuthenticatedUser()).thenReturn(expected);
        when(mockSaslNegotiator.getUser()).thenReturn("audited_user");

        IAuthenticator.SaslNegotiator negotiator = authenticator.newSaslNegotiator();
        AuthenticatedUser actual = negotiator.getAuthenticatedUser();

        assertThat(actual).isSameAs(expected);
        verify(mockAuditAdapter, times(1)).auditAuth(eq("audited_user"), eq(Status.ATTEMPT), anyLong());
        verify(mockAuditAdapter, times(1)).auditAuth(eq("audited_user"), eq(Status.SUCCEEDED), anyLong());
    }

    @Test
    public void testWhenAuthenticationFailsIsStillAudited()
    {
        when(mockSaslNegotiator.getUser()).thenReturn("audited_user");
        when(mockSaslNegotiator.getAuthenticatedUser()).thenThrow(new AuthenticationException("audited_user"));

        IAuthenticator.SaslNegotiator negotiator = authenticator.newSaslNegotiator();

        assertThatExceptionOfType(AuthenticationException.class).isThrownBy(() -> negotiator.getAuthenticatedUser());
        verify(mockAuditAdapter, times(1)).auditAuth(eq("audited_user"), eq(Status.ATTEMPT), anyLong());
        verify(mockAuditAdapter, times(1)).auditAuth(eq("audited_user"), eq(Status.FAILED), anyLong());
    }

    @Test
    public void testConstructFromAuditConfigWorksAsExpected()
    {
        String wrappedAuthenticatorName = AuditPasswordAuthenticator.class.getName();
        when(mockConfig.getWrappedAuthenticator()).thenReturn(wrappedAuthenticatorName);

        IAuditAuthenticator wrappedAuthenticator = WrappingAuditAuthenticator.newWrappedAuthenticator(mockConfig);
        assertThat(wrappedAuthenticator).isInstanceOf(AuditPasswordAuthenticator.class);
    }

    @Test
    public void testAuthWithSubject()
    {
        AuthenticatedUser expected = mock(AuthenticatedUser.class);
        when(mockSaslNegotiator.getAuthenticatedUser()).thenReturn(expected);
        when(mockSaslNegotiator.getUser()).thenReturn("audited_user");
        when(mockSaslNegotiator.getSubject()).thenReturn(Optional.of("subject_user"));

        IAuthenticator.SaslNegotiator negotiator = authenticator.newSaslNegotiator();
        AuthenticatedUser actual = negotiator.getAuthenticatedUser();

        assertThat(actual).isSameAs(expected);
        verify(mockAuditAdapter, times(1)).auditAuth(eq("audited_user"), eq("subject_user"), eq(Status.ATTEMPT), anyLong());
        verify(mockAuditAdapter, times(1)).auditAuth(eq("audited_user"), eq("subject_user"), eq(Status.SUCCEEDED), anyLong());
    }
}
