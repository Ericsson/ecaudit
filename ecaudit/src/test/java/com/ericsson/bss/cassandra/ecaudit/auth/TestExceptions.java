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

import org.junit.Test;

import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.exceptions.ReadTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

public class TestExceptions
{
    @Test
    public void testSimpleRuntimeExceptionIsSame()
    {
        RuntimeException expectedException = new RuntimeException();

        RuntimeException actualCause = Exceptions.tryGetCassandraExceptionCause(expectedException);

        assertThat(actualCause).isSameAs(expectedException);
        assertThat(actualCause.getSuppressed()).isEmpty();
    }

    @Test
    public void testSimpleCassandraExceptionIsSame()
    {
        RuntimeException expectedException = new ReadTimeoutException(ConsistencyLevel.QUORUM, 1, 1, false);

        RuntimeException actualCause = Exceptions.tryGetCassandraExceptionCause(expectedException);

        assertThat(actualCause).isSameAs(expectedException);
        assertThat(actualCause.getSuppressed()).isEmpty();
    }

    @Test
    public void testWrappedRuntimeExceptionIsSame()
    {
        RuntimeException exception = new RuntimeException();
        RuntimeException expectedException = new RuntimeException(exception);

        RuntimeException actualCause = Exceptions.tryGetCassandraExceptionCause(expectedException);

        assertThat(actualCause).isSameAs(expectedException);
        assertThat(actualCause.getSuppressed()).isEmpty();
        assertThat(exception.getSuppressed()).isEmpty();
    }

    @Test
    public void testWrappedCassandraExceptionIsUnwrapped()
    {
        RuntimeException expectedException = new ReadTimeoutException(ConsistencyLevel.QUORUM, 1, 1, false);
        RuntimeException exception = new RuntimeException(expectedException);

        RuntimeException actualCause = Exceptions.tryGetCassandraExceptionCause(exception);

        assertThat(actualCause).isSameAs(expectedException);
        assertThat(actualCause.getSuppressed()).contains(exception);
        assertThat(exception.getSuppressed()).isEmpty();
    }

    @Test
    public void testMultiWrappedCassandraExceptionIsUnwrapped()
    {
        RuntimeException expectedException = new ReadTimeoutException(ConsistencyLevel.QUORUM, 1, 1, false);
        RuntimeException exception = new RuntimeException(expectedException);
        exception = new RuntimeException(exception);

        RuntimeException actualCause = Exceptions.tryGetCassandraExceptionCause(exception);

        assertThat(actualCause).isSameAs(expectedException);
        assertThat(actualCause.getSuppressed()).contains(exception);
        assertThat(exception.getSuppressed()).isEmpty();
    }
}
