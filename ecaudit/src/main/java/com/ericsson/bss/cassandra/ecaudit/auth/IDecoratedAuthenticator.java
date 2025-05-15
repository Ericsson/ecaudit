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
import java.security.cert.Certificate;
import java.util.Optional;
import java.util.Set;

import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.auth.IRoleManager;

/**
 * An {@link IAuthenticator} that provide custom fields to be used during authentication auditing.
 */
public interface IDecoratedAuthenticator extends IAuthenticator
{
    /**
     * Returns a set of options supported and used by this authenticator.
     *
     * @return the options, empty if not applicable
     * @see IRoleManager#supportedOptions()
     */
    Set<IRoleManager.Option> supportedOptions();

    /**
     * Returns a set of user alterable options supported by this authenticator.
     *
     * This should be a sub set of {@link #supportedOptions()}. Regular users will be able to alter these options
     * on their own account.
     *
     * @return the options, empty if not applicable
     * @see IRoleManager#alterableOptions()
     */
    Set<IRoleManager.Option> alterableOptions();

    /**
     * Returns an <em>audited</em> {@link DecoratedSaslNegotiator}.
     *
     * Note: Implementations should probably use this method when {@link IAuthenticator#newSaslNegotiator(InetAddress)} is called.
     *
     * @param clientAddress the IP address of the client whom we wish to authenticate, or null
     *                      if an internal client (one not connected over the remote transport).
     * @return an instance of an {@link DecoratedSaslNegotiator}
     */
    DecoratedSaslNegotiator newDecoratedSaslNegotiator(InetAddress clientAddress);

    /**
     * Returns an <em>audited</em> {@link DecoratedSaslNegotiator}.
     *
     * Note: Implementations should probably use this method when
     * {@link IAuthenticator#newSaslNegotiator(InetAddress, Certificate[])} is called.
     *
     * @param clientAddress the IP address of the client whom we wish to authenticate, or null
     *                      if an internal client (one not connected over the remote transport).
     * @param certificates the peer's X509 Certificate chain, if present.
     * @return an instance of an {@link DecoratedSaslNegotiator}
     */
    default DecoratedSaslNegotiator newDecoratedSaslNegotiator(InetAddress clientAddress, Certificate[] certificates) // NOPMD
    {
        return newDecoratedSaslNegotiator(clientAddress);
    }

    /**
     * A <em>decorated</em> implementation of {@link SaslNegotiator} that will return additional fields to be used for
     * auditing authentication attempts.
     */
    interface DecoratedSaslNegotiator extends SaslNegotiator
    {
        /**
         * Get the user used in the authentication attempt.
         *
         * This method will only be called after {@link SaslNegotiator#isComplete()} returns true.
         *
         * @return the name of the user.
         */
        String getUser();

        /**
         * Get the <em>subject</em> of the authentication attempt, if applicable.
         *
         * This method will only be called after {@link SaslNegotiator#isComplete()} returns true.
         *
         * @return the subject, if applicable
         */
        default Optional<String> getSubject()
        {
            return Optional.empty();
        }
    }
}
