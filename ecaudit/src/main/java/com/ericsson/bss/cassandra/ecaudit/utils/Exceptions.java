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
package com.ericsson.bss.cassandra.ecaudit.utils;

import org.apache.cassandra.exceptions.CassandraException;

public final class Exceptions
{
    private Exceptions()
    {
        // Utility class
    }

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
            // TODO: Use addSuppressed when fix for https://jira.qos.ch/browse/LOGBACK-1454 is available
            // cause.addSuppressed(exception);
            return (CassandraException) cause;
        }
        else if (cause instanceof RuntimeException)
        {
            RuntimeException causeOfCause = tryGetCassandraExceptionCause((RuntimeException) cause);

            if (causeOfCause == cause) // NOPMD
            {
                return exception;
            }

            // TODO: Use addSuppressed when fix for https://jira.qos.ch/browse/LOGBACK-1454 is available
            // causeOfCause.addSuppressed(exception);
            return causeOfCause;
        }
        else
        {
            return exception;
        }
    }

    /**
     * Appends the cause the provided exception.
     * <p>
     * This can be useful if there is no constructor available that takes a throwable parameter.
     *
     * @param exception the exception to append a cause to
     * @param cause     the cause to append
     * @param <T>       type of the throwable
     * @return the provided exception
     */
    public static <T extends Throwable> T appendCause(T exception, Throwable cause)
    {
        return (T) exception.initCause(cause);
    }
}
