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
package com.ericsson.bss.cassandra.ecaudit.common.formatter;

import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests the {@link LogMessageFormatter} class.
 */
public class TestLogMessageFormatter
{
    private static final Map<String, Function<Integer, Object>> TEST_FIELDS = ImmutableMap.<String, Function<Integer, Object>>builder()
                                                                              .put("EQUAL", i -> i)
                                                                              .put("PLUS1", i -> i + 1)
                                                                              .put("PLUS2", i -> i + 2)
                                                                              .put("PLUS3", i -> i + 3)
                                                                              .put("EMPTY", i -> null) // Always missing value
                                                                              .build();

    @Test
    public void testFormatterWithStringFormat()
    {
        // Given
        LogMessageFormatter<Integer> formatter = LogMessageFormatter.<Integer>builder()
                                                 .format("Value=${EQUAL}, ValuePlus1=${PLUS1}")
                                                 .anchor("%s")
                                                 .escape("%", "%%")
                                                 .availableFields(TEST_FIELDS)
                                                 .build();
        // When
        String logTemplate = formatter.getLogTemplate();
        Object[] arguments = formatter.getArgumentsForEntry(42);
        // Then
        assertThat(logTemplate).isEqualTo("Value=%s, ValuePlus1=%s");
        assertThat(arguments).containsExactly("42", "43");
        String logText = String.format(logTemplate, arguments);
        assertThat(logText).isEqualTo("Value=42, ValuePlus1=43");
    }

    @Test
    public void testFormatterWithOptionalFields()
    {
        // Given
        LogMessageFormatter<Integer> formatter = LogMessageFormatter.<Integer>builder()
                                                 .format("Value=${EQUAL}{?:ValuePlus1=${PLUS1}?}{?:Empty=${EMPTY}?}:")
                                                 .anchor("%s")
                                                 .escape("%", "%%")
                                                 .availableFields(TEST_FIELDS)
                                                 .build();
        // When
        String logTemplate = formatter.getLogTemplate();
        Object[] arguments = formatter.getArgumentsForEntry(99);
        // Then
        assertThat(logTemplate).isEqualTo("Value=%s%s%s:");
        assertThat(arguments).containsExactly("99", ":ValuePlus1=100", "");
        String logText = String.format(logTemplate, arguments);
        assertThat(logText).isEqualTo("Value=99:ValuePlus1=100:");
    }

    @Test
    public void testEscaping()
    {
        // Given
        LogMessageFormatter<Integer> formatter = LogMessageFormatter.<Integer>builder()
                                                 .format("%Value=${EQUAL}%ValuePlus2=${PLUS2}%")
                                                 .anchor("%s")
                                                 .escape("%", "%%")
                                                 .availableFields(TEST_FIELDS)
                                                 .build();
        // When
        String logTemplate = formatter.getLogTemplate();
        Object[] arguments = formatter.getArgumentsForEntry(42);
        // Then
        assertThat(logTemplate).isEqualTo("%%Value=%s%%ValuePlus2=%s%%");
        assertThat(arguments).containsExactly("42", "44");
        String logText = String.format(logTemplate, arguments);
        assertThat(logText).isEqualTo("%Value=42%ValuePlus2=44%");
    }

    @Test
    public void testGetFieldFunctionOrThrow()
    {
        // Given
        LogMessageFormatter.Builder builder = LogMessageFormatter.<Integer>builder().availableFields(TEST_FIELDS);
        // When
        Function plus1 = LogMessageFormatter.getFieldFunctionOrThrow("PLUS1", builder);
        // Then
        assertThat(plus1.apply(1)).isEqualTo(2);
        // Throws when field does not exist
        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> LogMessageFormatter.getFieldFunctionOrThrow("NON_EXISTING_FIELD", builder))
        .withMessage("Unknown log format field and missing fallback: NON_EXISTING_FIELD");
    }

    @Test
    public void testGetFieldFunctionOrThrowWithFallback()
    {
        // Given
        LogMessageFormatter.Builder.FallbackFieldFunction fallback = mock(LogMessageFormatter.Builder.FallbackFieldFunction.class);
        LogMessageFormatter.Builder builder = LogMessageFormatter.<Integer>builder().availableFields(TEST_FIELDS).fallbackFunction(fallback);

        // When
        Function nonExistingFieldFunction = LogMessageFormatter.getFieldFunctionOrThrow("NON_EXISTING_FIELD", builder);
        nonExistingFieldFunction.apply(4711);

        // Then
        verify(fallback, times(1)).apply(eq("NON_EXISTING_FIELD"), eq(4711));
    }
}
