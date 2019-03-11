/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
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

import org.apache.cassandra.exceptions.CassandraException;

public class Exceptions
{
    /**
     * Try to find a CassandraException which is wrapped in a generic RuntimeException.
     *
     * This is useful for instance when cache updates fail asynchronously
     * with a RuntimeException or an UncheckedExecutionException
     * that is wrapping a CassandraException which we really want to propagate back to the client.
     *
     * The unwrapping procedure will cause valuable back trace to be lost,
     * and so the original exception is added as a suppressed exception to the CassandraException.
     *
     * @param exception the generic RuntimeException
     * @return the CassandraException if found in the wrapped stack, otherwise the original RuntimeException
     */
    public static RuntimeException tryGetCassandraExceptionCause(RuntimeException exception)
    {
        Throwable cause = exception.getCause();
        if (cause instanceof CassandraException)
        {
            cause.addSuppressed(exception);
            return (CassandraException) cause;
        }
        else if (cause instanceof RuntimeException)
        {
            RuntimeException causeOfCause = tryGetCassandraExceptionCause((RuntimeException) cause);

            if (causeOfCause == cause)
            {
                return exception;
            }

            causeOfCause.addSuppressed(exception);
            return causeOfCause;
        }
        else
        {
            return exception;
        }
    }
}
