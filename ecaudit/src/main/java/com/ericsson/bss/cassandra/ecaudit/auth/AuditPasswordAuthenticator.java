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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.PasswordAuthenticator;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.apache.cassandra.exceptions.ConfigurationException;

/**
 * A decorator of {@link PasswordAuthenticator} with added audit logging.
 */
public class AuditPasswordAuthenticator implements IAuditAuthenticator
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditPasswordAuthenticator.class);

    private final IAuthenticator wrappedAuthenticator;
    private final AuditAdapter auditAdapter;

    /**
     * Default constructor used by Cassandra.
     *
     * The default constructor wraps {@link PasswordAuthenticator}, which enables password authentication.
     */
    public AuditPasswordAuthenticator()
    {
        this(new PasswordAuthenticator(), AuditAdapter.getInstance());
    }

    @VisibleForTesting
    AuditPasswordAuthenticator(IAuthenticator authenticator, AuditAdapter adapter)
    {
        LOG.info("Auditing enabled on authenticator");
        this.wrappedAuthenticator = authenticator;
        this.auditAdapter = adapter;
    }

    @Override
    public boolean requireAuthentication()
    {
        return wrappedAuthenticator.requireAuthentication();
    }

    @Override
    public Set<? extends IResource> protectedResources()
    {
        return wrappedAuthenticator.protectedResources();
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        wrappedAuthenticator.validateConfiguration();
    }

    @Override
    public void setup()
    {
        wrappedAuthenticator.setup();
        auditAdapter.setup();
    }

    @Override
    public SaslNegotiator newSaslNegotiator()
    {
        return newAuditSaslNegotiator();
    }

    @Override
    public AuditSaslNegotiator newAuditSaslNegotiator()
    {
        // For BWC, check if being wrapped. If not, log authentication attempts as normal.
        // If being wrapped, i.e. not used standalone, let the wrapper do the authentication
        // logging for us (disable logging)
        IAuthenticator authenticator = DatabaseDescriptor.getAuthenticator();
        boolean disableLogging = authenticator instanceof WrappingAuditAuthenticator;

        LOG.debug("Setting up SASL negotiation with client peer");
        return new AuditPlainTextSaslAuthenticator(wrappedAuthenticator.newSaslNegotiator(), disableLogging);
    }

    @Override
    public Set<IRoleManager.Option> supportedOptions()
    {
        return ImmutableSet.of(IRoleManager.Option.LOGIN, IRoleManager.Option.SUPERUSER, IRoleManager.Option.PASSWORD, IRoleManager.Option.OPTIONS);
    }

    @Override
    public Set<IRoleManager.Option> alterableOptions()
    {
        return  ImmutableSet.of(IRoleManager.Option.PASSWORD, IRoleManager.Option.OPTIONS);
    }

    @Override
    public AuthenticatedUser legacyAuthenticate(Map<String, String> credentials) throws AuthenticationException
    {
        return wrappedAuthenticator.legacyAuthenticate(credentials);
    }

    private class AuditPlainTextSaslAuthenticator implements AuditSaslNegotiator
    {
        private final SaslNegotiator saslNegotiator;

        private String decodedUsername;
        private final boolean disableLogging;

        AuditPlainTextSaslAuthenticator(SaslNegotiator saslNegotiator, boolean disableLogging)
        {
            this.saslNegotiator = saslNegotiator;
            this.disableLogging = disableLogging;
        }

        @Override
        public byte[] evaluateResponse(byte[] clientResponse) throws AuthenticationException
        {
            decodedUsername = decodeUserNameFromSasl(clientResponse);
            return saslNegotiator.evaluateResponse(clientResponse);
        }

        @Override
        public boolean isComplete()
        {
            return saslNegotiator.isComplete();
        }

        @Override
        public AuthenticatedUser getAuthenticatedUser() throws AuthenticationException
        {
            // If wrapped by the WrappingAuditAuthenticator just try to authenticate without logging
            // since the wrapper will log for us
            if (disableLogging)
            {
                return saslNegotiator.getAuthenticatedUser();
            }

            long timestamp = System.currentTimeMillis();
            auditAdapter.auditAuth(decodedUsername, Status.ATTEMPT, timestamp);
            try
            {
                AuthenticatedUser result = saslNegotiator.getAuthenticatedUser();
                auditAdapter.auditAuth(decodedUsername, Status.SUCCEEDED, timestamp);
                return result;
            }
            catch (RuntimeException e)
            {
                auditAdapter.auditAuth(decodedUsername, Status.FAILED, timestamp);
                throw e;
            }
        }

        @Override
        public String getUser()
        {
            return decodedUsername;
        }

        /**
         * Decoded the credentials so that we know what username was used in the authentication attempt.
         *
         * @see PasswordAuthenticator original implementation
         */
        private String decodeUserNameFromSasl(byte[] bytes) throws AuthenticationException
        {
            boolean passConsumed = false;
            int end = bytes.length;
            for (int i = bytes.length - 1; i >= 0; i--)
            {
                if (bytes[i] == 0 /* null */)
                {
                    if (passConsumed)
                    {
                        return new String(Arrays.copyOfRange(bytes, i + 1, end), StandardCharsets.UTF_8);
                    }
                    passConsumed = true;
                    end = i;
                }
            }
            throw new AuthenticationException("Authentication ID must not be null");
        }
    }

}
