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
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.Int32Type;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.schema.ColumnMetadata;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the {@link SuppressClusteringAndRegular} class.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSuppressClusteringAndRegular
{
    private static final ColumnSpecification CLUSTER_KEY_COLUMN = ColumnMetadata.clusteringColumn("ks", "cf", "clusterKey", Int32Type.instance, 1);
    private static final ColumnSpecification PARTITION_KEY_COLUMN = ColumnMetadata.partitionKeyColumn("ks", "cf", "partitionKey", UTF8Type.instance, 1);
    private static final ColumnSpecification REGULAR_COLUMN = ColumnMetadata.regularColumn("ks", "cf", "regular", UTF8Type.instance);

    @Mock
    ByteBuffer valueMock;

    @Test
    public void testPartitionKeyIsNotSuppressed()
    {
        // Given
        BoundValueSuppressor suppressor = new SuppressClusteringAndRegular();
        // When
        Optional<String> result = suppressor.suppress(PARTITION_KEY_COLUMN, valueMock);
        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(valueMock);
    }

    @Test
    public void testClusterKeyIsNotSuppressed()
    {
        // Given
        BoundValueSuppressor suppressor = new SuppressClusteringAndRegular();
        // When
        Optional<String> result = suppressor.suppress(CLUSTER_KEY_COLUMN, valueMock);
        // Then
        assertThat(result).contains("<int>");
        verifyNoInteractions(valueMock);
    }

    @Test
    public void testRegularIsSuppressed()
    {
        // Given
        BoundValueSuppressor suppressor = new SuppressClusteringAndRegular();
        // When
        Optional<String> result = suppressor.suppress(REGULAR_COLUMN, valueMock);
        // Then
        assertThat(result).contains("<text>");
        verifyNoInteractions(valueMock);
    }
}
