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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * An auditing {@link IAuthenticator} which is delegating authentication
 * to a {@link IDecoratedAuthenticator}.
 *
 * The {@link IDecoratedAuthenticator} provides attributes which the
 * {@link AuditAuthenticator} use to emit audit records.
 */
public class AuditAuthenticator implements IAuthenticator
{
    private static final Logger LOG = LoggerFactory.getLogger(AuditAuthenticator.class);

    private final IDecoratedAuthenticator wrappedAuthenticator;
    private final AuditAdapter auditAdapter;

    /**
     * Default constructor called by Cassandra.
     *
     * This creates an instance of {@link AuditAuthenticator} with the default {@link AuditAdapter}
     * and gets an {@link IDecoratedAuthenticator} from configuration (or the default one).
     */
    public AuditAuthenticator()
    {
        this(newWrappedAuthenticator(AuditConfig.getInstance()), AuditAdapter.getInstance());
    }

    AuditAuthenticator(IDecoratedAuthenticator wrappedAuthenticator, AuditAdapter auditAdapter)
    {
        LOG.info("Auditing enabled on authenticator");
        this.wrappedAuthenticator = wrappedAuthenticator;
        this.auditAdapter = auditAdapter;
    }

    @VisibleForTesting
    static IDecoratedAuthenticator newWrappedAuthenticator(AuditConfig auditConfig)
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
        auditAdapter.setup();
    }

    Set<IRoleManager.Option> supportedOptions()
    {
        return wrappedAuthenticator.supportedOptions();
    }

    Set<IRoleManager.Option> alterableOptions()
    {
        return wrappedAuthenticator.alterableOptions();
    }

    @Override
    public SaslNegotiator newSaslNegotiator()
    {
        return new AuditSaslNegotiator(wrappedAuthenticator.newDecoratedSaslNegotiator());
    }

    @Override
    public AuthenticatedUser legacyAuthenticate(Map<String, String> credentials) throws AuthenticationException
    {
        return wrappedAuthenticator.legacyAuthenticate(credentials);
    }

    /**
     * Implements a {@link SaslNegotiator} that performs auditing on login attempts.
     */
    private class AuditSaslNegotiator implements SaslNegotiator
    {
        private final IDecoratedAuthenticator.DecoratedSaslNegotiator decoratedSaslNegotiator;

        public AuditSaslNegotiator(IDecoratedAuthenticator.DecoratedSaslNegotiator decoratedSaslNegotiator)
        {
            this.decoratedSaslNegotiator = decoratedSaslNegotiator;
        }

        @Override
        public byte[] evaluateResponse(byte[] clientResponse) throws AuthenticationException
        {
            return decoratedSaslNegotiator.evaluateResponse(clientResponse);
        }

        @Override
        public boolean isComplete()
        {
            return decoratedSaslNegotiator.isComplete();
        }

        @Override
        public AuthenticatedUser getAuthenticatedUser() throws AuthenticationException
        {
            String userName = decoratedSaslNegotiator.getUser();

            long timestamp = System.currentTimeMillis();
            auditAuth(userName, Status.ATTEMPT, timestamp);
            try
            {
                AuthenticatedUser result = decoratedSaslNegotiator.getAuthenticatedUser();
                auditAuth(userName, Status.SUCCEEDED, timestamp);
                return result;
            }
            catch (RuntimeException e)
            {
                auditAuth(userName, Status.FAILED, timestamp);
                throw e;
            }
        }

        private void auditAuth(String userName, Status status, long timestamp)
        {
            Optional<String> subject = decoratedSaslNegotiator.getSubject();
            if (subject.isPresent())
            {
                auditAdapter.auditAuth(userName, subject.get(), status, timestamp);
            }
            else
            {
                auditAdapter.auditAuth(userName, status, timestamp);
            }
        }
    }
}
