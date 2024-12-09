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
 * Tests the {@link SuppressEverything} class.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSuppressEverything
{
    private static final ColumnSpecification TEXT_KEY_COLUMN = ColumnMetadata.partitionKeyColumn("ks", "cf", "partitionKey", UTF8Type.instance, 1);
    private static final ColumnSpecification INT_REGULAR_COLUMN = ColumnMetadata.regularColumn("ks", "cf", "regular", Int32Type.instance);

    @Mock
    ByteBuffer valueMock;

    @Test
    public void testSuppressorAlwaysReturnsType()
    {
        // Given
        BoundValueSuppressor suppressor = new SuppressEverything();
        // When
        Optional<String> textResult = suppressor.suppress(TEXT_KEY_COLUMN, valueMock);
        Optional<String> integerResult = suppressor.suppress(INT_REGULAR_COLUMN, valueMock);
        // Then
        assertThat(textResult).contains("<text>");
        assertThat(integerResult).contains("<int>");
        verifyNoInteractions(valueMock);
    }
}
