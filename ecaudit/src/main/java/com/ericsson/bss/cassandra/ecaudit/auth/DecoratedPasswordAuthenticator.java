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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.PasswordAuthenticator;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.apache.cassandra.exceptions.ConfigurationException;

/**
 * A decorated version of the {@link PasswordAuthenticator}.
 */
public class DecoratedPasswordAuthenticator implements IDecoratedAuthenticator
{
    private static final Logger LOG = LoggerFactory.getLogger(DecoratedPasswordAuthenticator.class);

    private final PasswordAuthenticator wrappedAuthenticator;

    /**
     * Default constructor used by {@link AuditAuthenticator}.
     *
     * The default constructor wraps {@link PasswordAuthenticator}, which enables password authentication.
     */
    public DecoratedPasswordAuthenticator()
    {
        this(new PasswordAuthenticator());
    }

    @VisibleForTesting
    DecoratedPasswordAuthenticator(PasswordAuthenticator authenticator)
    {
        LOG.info("Auditing enabled on authenticator");
        this.wrappedAuthenticator = authenticator;
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
    }

    @Override
    public Set<IRoleManager.Option> supportedOptions()
    {
        return Collections.singleton(IRoleManager.Option.PASSWORD);
    }

    @Override
    public Set<IRoleManager.Option> alterableOptions()
    {
        return Collections.singleton(IRoleManager.Option.PASSWORD);
    }

    @Override
    public SaslNegotiator newSaslNegotiator()
    {
        return newDecoratedSaslNegotiator();
    }

    @Override
    public DecoratedSaslNegotiator newDecoratedSaslNegotiator()
    {
        LOG.debug("Setting up SASL negotiation with client peer");
        return new DecoratedPlainTextSaslAuthenticator(wrappedAuthenticator.newSaslNegotiator());
    }

    @Override
    public AuthenticatedUser legacyAuthenticate(Map<String, String> credentials) throws AuthenticationException
    {
        return wrappedAuthenticator.legacyAuthenticate(credentials);
    }

    private class DecoratedPlainTextSaslAuthenticator implements DecoratedSaslNegotiator
    {
        private final SaslNegotiator saslNegotiator;

        private String decodedUsername;

        DecoratedPlainTextSaslAuthenticator(SaslNegotiator saslNegotiator)
        {
            this.saslNegotiator = saslNegotiator;
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
            return saslNegotiator.getAuthenticatedUser();
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
