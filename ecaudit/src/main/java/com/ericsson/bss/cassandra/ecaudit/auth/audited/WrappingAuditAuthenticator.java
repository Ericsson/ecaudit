/*
 * Copyright 20202 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.auth.audited;

import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;

import com.ericsson.bss.cassandra.ecaudit.AuditAdapter;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.utils.FBUtilities;

public class WrappingAuditAuthenticator implements IAuthenticator
{
    private final AuditedAuthenticator wrappedAuthenticator;
    private final AuditAdapter auditAdapter;

    /**
     * Default constructor called by Cassandra.
     *
     * This creates an instance of {@link WrappingAuditAuthenticator} with the default {@link AuditAdapter}
     * and gets an {@link AuditedAuthenticator} from configuration (or the default one).
     */
    public WrappingAuditAuthenticator()
    {
        this(newWrappedAuthenticator(AuditConfig.getInstance()), AuditAdapter.getInstance());
    }

    @VisibleForTesting
    WrappingAuditAuthenticator(AuditedAuthenticator wrappedAuthenticator, AuditAdapter auditAdapter)
    {
        this.wrappedAuthenticator = wrappedAuthenticator;
        this.auditAdapter = auditAdapter;
    }

    @VisibleForTesting
    static AuditedAuthenticator newWrappedAuthenticator(AuditConfig auditConfig)
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
        return new AuditingSaslNegotiator(wrappedAuthenticator.createAuditedSaslNegotiator());
    }

    @Override
    public AuthenticatedUser legacyAuthenticate(Map<String, String> credentials) throws AuthenticationException
    {
        return wrappedAuthenticator.legacyAuthenticate(credentials);
    }

    /**
     * Implements a {@link org.apache.cassandra.auth.IAuthenticator.SaslNegotiator} that performs auditing on
     * login attempts.
     */
    private class AuditingSaslNegotiator implements SaslNegotiator
    {
        private final AuditedAuthenticator.AuditedSaslNegotiator auditedSaslNegotiator;

        public AuditingSaslNegotiator(AuditedAuthenticator.AuditedSaslNegotiator auditedSaslNegotiator)
        {
            this.auditedSaslNegotiator = auditedSaslNegotiator;
        }

        @Override
        public byte[] evaluateResponse(byte[] clientResponse) throws AuthenticationException
        {
            return auditedSaslNegotiator.evaluateResponse(clientResponse);
        }

        @Override
        public boolean isComplete()
        {
            return auditedSaslNegotiator.isComplete();
        }

        @Override
        public AuthenticatedUser getAuthenticatedUser() throws AuthenticationException
        {
            String userName = auditedSaslNegotiator.getUser().orElse(null);

            long timestamp = System.currentTimeMillis();
            auditAdapter.auditAuth(userName, Status.ATTEMPT, timestamp);
            try
            {
                AuthenticatedUser result = auditedSaslNegotiator.getAuthenticatedUser();
                auditAdapter.auditAuth(userName, Status.SUCCEEDED, timestamp);
                return result;
            }
            catch (RuntimeException e)
            {
                auditAdapter.auditAuth(userName, Status.FAILED, timestamp);
                throw e;
            }
        }
    }
}
