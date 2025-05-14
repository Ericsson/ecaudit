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

import java.net.InetAddress;
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

import javax.security.cert.X509Certificate;

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
    public SaslNegotiator newSaslNegotiator(InetAddress clientAddress)
    {
        return new AuditSaslNegotiator(clientAddress, wrappedAuthenticator.newDecoratedSaslNegotiator(clientAddress));
    }

    @Override
    public SaslNegotiator newSaslNegotiator(InetAddress clientAddress, X509Certificate[] certificates)
    {
        return new AuditSaslNegotiator(clientAddress, wrappedAuthenticator.newDecoratedSaslNegotiator(clientAddress, certificates));
    }

    @Override
    public AuthenticatedUser legacyAuthenticate(Map<String, String> credentials) throws AuthenticationException
    {
        LOG.debug("Setting up SASL negotiation with client peer");
        return wrappedAuthenticator.legacyAuthenticate(credentials);
    }

    /**
     * Implements a {@link SaslNegotiator} that performs auditing on login attempts.
     */
    private class AuditSaslNegotiator implements SaslNegotiator
    {
        private final InetAddress clientAddress;
        private final IDecoratedAuthenticator.DecoratedSaslNegotiator decoratedSaslNegotiator;

        public AuditSaslNegotiator(InetAddress clientAddress, IDecoratedAuthenticator.DecoratedSaslNegotiator decoratedSaslNegotiator)
        {
            this.clientAddress = clientAddress;
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
            auditAuth(userName, clientAddress, Status.ATTEMPT, timestamp);
            try
            {
                AuthenticatedUser result = decoratedSaslNegotiator.getAuthenticatedUser();
                auditAuth(userName, clientAddress, Status.SUCCEEDED, timestamp);
                return result;
            }
            catch (RuntimeException e)
            {
                auditAuth(userName, clientAddress, Status.FAILED, timestamp);
                throw e;
            }
        }

        private void auditAuth(String userName, InetAddress clientAddress, Status status, long timestamp)
        {
            Optional<String> subject = decoratedSaslNegotiator.getSubject();
            if (subject.isPresent())
            {
                auditAdapter.auditAuth(userName, clientAddress, subject.get(), status, timestamp);
            }
            else
            {
                auditAdapter.auditAuth(userName, clientAddress, status, timestamp);
            }
        }
    }
}
