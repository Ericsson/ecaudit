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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.Test;

import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.db.marshal.AsciiType;
import org.apache.cassandra.serializers.AsciiSerializer;
import org.apache.cassandra.serializers.SetSerializer;
import org.apache.cassandra.utils.ByteBufferUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link WhitelistDataAccess} class.
 */
public class TestWhitelistDataAccess
{
    @Test
    public void testGetSerializedUpdateValues() throws Exception
    {
        // Given
        Set<Permission> operations = Sets.newHashSet(Permission.SELECT, Permission.MODIFY);
        SetSerializer<String> stringSetSerializer = SetSerializer.getInstance(AsciiSerializer.instance);
        // When
        List<ByteBuffer> values = WhitelistDataAccess.getSerializedUpdateValues("Role1", "Resource1", operations);
        // Then
        assertThat(values).hasSize(3);

        ByteBuffer operationsByteBuffer = values.get(0);
        assertThat(stringSetSerializer.deserialize(operationsByteBuffer)).containsOnly("SELECT", "MODIFY");

        ByteBuffer roleByteBuffer = values.get(1);
        assertThat(ByteBufferUtil.string(roleByteBuffer)).isEqualTo("Role1");

        ByteBuffer resourceByteBuffer = values.get(2);
        assertThat(ByteBufferUtil.string(resourceByteBuffer)).isEqualTo("Resource1");
    }
}
