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
package com.ericsson.bss.cassandra.ecaudit.entry.obfuscator;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.cassandra.cql3.ColumnSpecification;

/**
 * Column obfuscator used to handle prepared statement bound values.
 */
public interface ColumnObfuscator
{
    /**
     * Creates an obfuscated string that represent the column value only IF the column should be obfuscated.
     *
     * @param column the column to check
     * @param value  the value that may be obfuscated
     * @return the obfuscated string representation of the column value, or {@link Optional#empty()} if the value
     * should not be obfuscated.
     */
    Optional<String> obfuscate(ColumnSpecification column, ByteBuffer value);
}
