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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.utils.FBUtilities;

public class WrappingAuditAuthenticator implements IAuditAuthenticator
{
    private final IAuditAuthenticator wrappedAuthenticator;
    private final AuditAdapter auditAdapter;

    /**
     * Default constructor called by Cassandra.
     *
     * This creates an instance of {@link WrappingAuditAuthenticator} with the default {@link AuditAdapter}
     * and gets an {@link IAuditAuthenticator} from configuration (or the default one).
     */
    public WrappingAuditAuthenticator()
    {
        this(newWrappedAuthenticator(AuditConfig.getInstance()), AuditAdapter.getInstance());
    }

    @VisibleForTesting
    WrappingAuditAuthenticator(IAuditAuthenticator wrappedAuthenticator, AuditAdapter auditAdapter)
    {
        this.wrappedAuthenticator = wrappedAuthenticator;
        this.auditAdapter = auditAdapter;
    }

    @VisibleForTesting
    static IAuditAuthenticator newWrappedAuthenticator(AuditConfig auditConfig)
    {
        String className = auditConfig.getWrappedAuthenticator();
        return FBUtilities.construct(className, "authenticator");
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
    public IAuthenticator.SaslNegotiator newSaslNegotiator()
    {
        return createAuditedSaslNegotiator();
    }

    @Override
    public AuthenticatedUser legacyAuthenticate(Map<String, String> credentials) throws AuthenticationException
    {
        return wrappedAuthenticator.legacyAuthenticate(credentials);
    }

    @Override
    public AuditSaslNegotiator createAuditedSaslNegotiator()
    {
        return new AuditingSaslNegotiator(wrappedAuthenticator.createAuditedSaslNegotiator());
    }

    @Override
    public Set<IRoleManager.Option> supportedOptions()
    {
        return wrappedAuthenticator.supportedOptions();
    }

    @Override
    public Set<IRoleManager.Option> alterableOptions()
    {
        return wrappedAuthenticator.alterableOptions();
    }

    /**
     * Implements a {@link org.apache.cassandra.auth.IAuthenticator.SaslNegotiator} that performs auditing on
     * login attempts.
     */
    private class AuditingSaslNegotiator implements AuditSaslNegotiator
    {
        private final IAuditAuthenticator.AuditSaslNegotiator auditSaslNegotiator;

        public AuditingSaslNegotiator(IAuditAuthenticator.AuditSaslNegotiator auditSaslNegotiator)
        {
            this.auditSaslNegotiator = auditSaslNegotiator;
        }

        @Override
        public byte[] evaluateResponse(byte[] clientResponse) throws AuthenticationException
        {
            return auditSaslNegotiator.evaluateResponse(clientResponse);
        }

        @Override
        public boolean isComplete()
        {
            return auditSaslNegotiator.isComplete();
        }

        @Override
        public AuthenticatedUser getAuthenticatedUser() throws AuthenticationException
        {
            String userName = getUser();
            Optional<String> subject = getSubject();

            long timestamp = System.currentTimeMillis();
            auditAdapter.auditAuth(userName, Status.ATTEMPT, timestamp, subject);
            try
            {
                AuthenticatedUser result = auditSaslNegotiator.getAuthenticatedUser();
                auditAdapter.auditAuth(userName, Status.SUCCEEDED, timestamp, subject);
                return result;
            }
            catch (RuntimeException e)
            {
                auditAdapter.auditAuth(userName, Status.FAILED, timestamp, subject);
                throw e;
            }
        }

        @Override
        public String getUser()
        {
            return auditSaslNegotiator.getUser();
        }

        @Override
        public Optional<String> getSubject()
        {
            return auditSaslNegotiator.getSubject();
        }
    }
}
