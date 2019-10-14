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
package com.ericsson.bss.cassandra.ecaudit.entry.suppressor;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.config.ColumnDefinition.Kind;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.Int32Type;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the {@link SuppressEverything} class.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSuppressEverything
{
    @Mock
    ByteBuffer valueMock;

    @Test
    public void testSuppressorAlwaysReturnsType()
    {
        // Given
        ColumnSpecification textColumn = createColumnDefinition(UTF8Type.instance, Kind.PARTITION_KEY);
        ColumnSpecification integerColumn = createColumnDefinition(Int32Type.instance, Kind.REGULAR);

        BoundValueSuppressor suppressor = new SuppressEverything();
        // When
        Optional<String> textResult = suppressor.suppress(textColumn, valueMock);
        Optional<String> integerResult = suppressor.suppress(integerColumn, valueMock);
        // Then
        assertThat(textResult).contains("<text>");
        assertThat(integerResult).contains("<int>");
        verifyZeroInteractions(valueMock);
    }

    static final ColumnDefinition createColumnDefinition(AbstractType<?> type, Kind kind)
    {
        return new ColumnDefinition("ks", "cf", mock(ColumnIdentifier.class), type, null, null, null, 1, kind);
    }
}
