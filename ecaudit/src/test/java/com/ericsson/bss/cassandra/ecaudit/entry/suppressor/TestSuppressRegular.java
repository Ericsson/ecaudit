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

import org.apache.cassandra.config.ColumnDefinition.Kind;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.ericsson.bss.cassandra.ecaudit.entry.suppressor.TestSuppressEverything.createColumnDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the {@link SuppressRegular} class.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSuppressRegular
{
    private static final ColumnSpecification CLUSTER_KEY_COLUMN = createColumnDefinition(UTF8Type.instance, Kind.CLUSTERING_COLUMN);
    private static final ColumnSpecification PARTITION_KEY_COLUMN = createColumnDefinition(UTF8Type.instance, Kind.PARTITION_KEY);
    private static final ColumnSpecification REGULAR_COLUMN = createColumnDefinition(UTF8Type.instance, Kind.REGULAR);

    @Mock
    ByteBuffer valueMock;

    @Test
    public void testPartitionKeyIsNotSuppressed()
    {
        // Given
        BoundValueSuppressor suppressor = new SuppressRegular();
        // When
        Optional<String> result = suppressor.suppress(PARTITION_KEY_COLUMN, valueMock);
        // Then
        assertThat(result).isEmpty();
        verifyZeroInteractions(valueMock);
    }

    @Test
    public void testClusterKeyIsSuppressed()
    {
        // Given
        BoundValueSuppressor suppressor = new SuppressRegular();
        // When
        Optional<String> result = suppressor.suppress(CLUSTER_KEY_COLUMN, valueMock);
        // Then
        assertThat(result).isEmpty();
        verifyZeroInteractions(valueMock);
    }

    @Test
    public void testRegularIsSuppressed()
    {
        // Given
        BoundValueSuppressor suppressor = new SuppressRegular();
        // When
        Optional<String> result = suppressor.suppress(REGULAR_COLUMN, valueMock);
        // Then
        assertThat(result).contains("<text>");
        verifyZeroInteractions(valueMock);
    }
}
