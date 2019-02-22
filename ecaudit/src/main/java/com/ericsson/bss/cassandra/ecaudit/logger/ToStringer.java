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

/**
 * Container object that should be used during logging to lazily (only when needed) create strings for its value.
 * The value will be converted into a string using the provided function when the {@link #toString()} method is called.
 * These objects are supposed to be short lived.
 *
 * @param <T> the type of the contained value
 */
class ToStringer<T>
{
    private final T value;
    private final Function<T, Object> valueToStringFunction;

    ToStringer(T value, Function<T, Object> valueToStringFunction)
    {
        this.value = value;
        this.valueToStringFunction = valueToStringFunction;
    }

    @Override
    public String toString()
    {
        return String.valueOf(valueToStringFunction.apply(value));
    }
}
