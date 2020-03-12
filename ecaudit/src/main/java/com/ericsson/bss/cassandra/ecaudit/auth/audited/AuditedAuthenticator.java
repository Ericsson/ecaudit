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
package com.ericsson.bss.cassandra.ecaudit.auth.audited;

import java.util.Optional;

import org.apache.cassandra.auth.IAuthenticator;

/**
 * An {@link IAuthenticator} that provide custom fields to be used during authentication auditing.
 */
public interface AuditedAuthenticator extends IAuthenticator
{
    /**
     * Returns an <em>audited</em> {@link AuditedSaslNegotiator SaslNegotiator}.
     * <p>
     * Note: Implementations should probably use this method when {@link IAuthenticator#newSaslNegotiator()} is called.
     *
     * @return an instance of an {@link AuditedSaslNegotiator}
     */
    AuditedSaslNegotiator createAuditedSaslNegotiator();

    /**
     * An <em>audited</em> implementation of {@link org.apache.cassandra.auth.IAuthenticator.SaslNegotiator}
     * that may optionally return additional fields to be used for auditing authentication attempts.
     */
    interface AuditedSaslNegotiator extends SaslNegotiator
    {
        /**
         * Get the user used in the authentication attempt, if applicable.
         * <p>
         * If user is not applicable in this authentication, return an empty optional.
         *
         * @return the name of the user, if applicable.
         */
        default Optional<String> getUser()
        {
            return Optional.empty();
        }
    }
}
