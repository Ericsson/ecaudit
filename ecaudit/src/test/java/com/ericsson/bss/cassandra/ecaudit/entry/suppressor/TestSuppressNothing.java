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

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the {@link SuppressNothing} class.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSuppressNothing
{
    @Mock
    ByteBuffer valueMock;

    @Test
    public void testSuppressorNeverSuppresses()
    {
        // Given
        BoundValueSuppressor suppressor = new SuppressNothing();
        // When
        Optional<String> result = suppressor.suppress(null, valueMock);
        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(valueMock);
    }
}
