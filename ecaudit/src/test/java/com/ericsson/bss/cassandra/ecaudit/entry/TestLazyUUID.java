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
package com.ericsson.bss.cassandra.ecaudit.entry;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link LazyUUID} class
 */
public class TestLazyUUID
{
    @Test
    public void testThatGetUuidReturnsSameIdEveryTime()
    {
        // Given
        LazyUUID lazyUUID = new LazyUUID();
        // When
        UUID uuid = lazyUUID.getUuid();
        // Then
        assertThat(uuid).isNotNull().isSameAs(lazyUUID.getUuid());
    }
    @Test
    public void testThatTimeBasedUuidIsCreated()
    {
        assertThat(new LazyUUID().getUuid().version()).isEqualTo(1); // 1 - Time-based UUID
    }

    @Test
    public void testThatInternalUuidIsNotCreatedBeforeGetUuidMethodIsCalled() throws Exception
    {
        // Given
        LazyUUID lazyUUID = new LazyUUID();
        assertThat(getInternalUuid(lazyUUID)).isNull();
        // When
        UUID uuid = lazyUUID.getUuid();
        // Then
        assertThat(getInternalUuid(lazyUUID)).isNotNull().isSameAs(uuid);
    }

    private static UUID getInternalUuid(LazyUUID lazyUUID) throws NoSuchFieldException, IllegalAccessException
    {
        Field uuidField = LazyUUID.class.getDeclaredField("uuid");
        uuidField.setAccessible(true);
        return (UUID) uuidField.get(lazyUUID);
    }
}
