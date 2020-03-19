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
import java.util.Optional;

import org.apache.cassandra.auth.IAuthenticator;

/**
 * An {@link IAuthenticator} that provide custom fields to be used during authentication auditing.
 */
public interface IAuditAuthenticator extends IAuthenticator, IOptionsProvider
{
    /**
     * Returns an <em>audited</em> {@link AuditSaslNegotiator SaslNegotiator}.
     * <p>
     * Note: Implementations should probably use this method when {@link IAuthenticator#newSaslNegotiator(InetAddress)}  is called.
     * @param clientAddress the IP address of the client whom we wish to authenticate, or null
     *                      if an internal client (one not connected over the remote transport).
     * @return an instance of an {@link AuditSaslNegotiator}
     */
    AuditSaslNegotiator newAuditSaslNegotiator(InetAddress clientAddress);

    /**
     * An <em>audited</em> implementation of {@link org.apache.cassandra.auth.IAuthenticator.SaslNegotiator}
     * that may optionally return additional fields to be used for auditing authentication attempts.
     */
    interface AuditSaslNegotiator extends SaslNegotiator
    {
        /**
         * Get the user used in the authentication attempt.
         * <p>
         * This method should only be called if {@link SaslNegotiator#isComplete()} returns true.
         *
         * @return the name of the user.
         */
        String getUser();

        /**
         * Get the <em>subject</em> of the authentication attempt, if applicable.
         * <p>
         * This method should only be called if {@link SaslNegotiator#isComplete()} returns true.
         *
         * @return the subject, if applicable
         */
        default Optional<String> getSubject()
        {
            return Optional.empty();
        }
    }
}
