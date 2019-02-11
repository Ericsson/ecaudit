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
package com.ericsson.bss.cassandra.ecaudit.logger;

import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestToStringer
{
    @Mock
    private Function<Integer, Object> function;

    @Test
    public void testThatFunctionIsCalled()
    {
        // Given
        when(function.apply(any())).thenReturn("The string");
        ToStringer toStringer = new ToStringer<>(42, function);
        // When
        String result = toStringer.toString();
        // Then
        assertThat(result).isEqualTo("The string");
        verify(function).apply(42);
    }

    @Test
    public void testThatFunctionNotCalled()
    {
        // Given
        new ToStringer<>(42, function);
        // Then
        verify(function, never()).apply(any());
    }
}
