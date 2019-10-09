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

import org.apache.cassandra.cql3.ColumnSpecification;

/**
 * Used to suppress prepared statement bound values.
 */
public interface ColumnSuppressor
{
    /**
     * Creates an suppressed string representation of the column value only IF the column should be suppressed.
     *
     * @param column the column to check
     * @param value  the value that may be suppressed
     * @return the suppressed string representation of the column value, or {@link Optional#empty()} if the value
     * should not be suppressed.
     */
    Optional<String> suppress(ColumnSpecification column, ByteBuffer value);
}
