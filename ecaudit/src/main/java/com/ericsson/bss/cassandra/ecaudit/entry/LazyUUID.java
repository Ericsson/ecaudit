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

import java.util.UUID;

import com.google.common.annotations.VisibleForTesting;

import org.apache.cassandra.utils.UUIDGen;

/**
 * Contains an UUID that is lazily created the first time {@link #getUuid()} is called.
 * A time-base UUID (type 1) will be created, see {@link UUIDGen#getTimeUUID()}.
 */
public class LazyUUID
{
    private UUID uuid;

    public UUID getUuid()
    {
        if (uuid == null)
        {
            uuid = UUIDGen.getTimeUUID();
        }
        return uuid;
    }

    @VisibleForTesting
    public static LazyUUID fromUuid(UUID uuid)
    {
        LazyUUID lazyUUID = new LazyUUID();
        lazyUUID.uuid = uuid;
        return  lazyUUID;
    }
}
