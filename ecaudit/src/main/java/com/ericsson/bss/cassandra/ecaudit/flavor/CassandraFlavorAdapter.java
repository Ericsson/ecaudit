/*
 * Copyright 2020 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.flavor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.schema.MigrationManager;
import org.apache.cassandra.schema.TableMetadata;

public class CassandraFlavorAdapter
{
    private CassandraFlavorAdapter()
    {
    }

    public static CassandraFlavorAdapter getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder
    {
        private static final CassandraFlavorAdapter INSTANCE = new CassandraFlavorAdapter();
    }

    /**
     * This is replacing MigrationManager.forceAnnounceNewColumnFamily() which existed in version 3.0.19/3.11.5 and earlier.
     *
     * Announces the table even if the definition is already know locally.
     * This should generally be avoided but is used internally when we want to force the most up to date version of
     * a system table schema (Note that we don't know if the schema we force _is_ the most recent version or not, we
     * just rely on idempotency to basically ignore that announce if it's not. That's why we can't use announceUpdateColumnFamily,
     * it would for instance delete new columns if this is not called with the most up-to-date version)
     *
     * Note that this is only safe for system tables where we know the cfId is fixed and will be the same whatever version
     * of the definition is used.
     */
    public void forceAnnounceNewColumnFamily(TableMetadata expectedTable) throws ConfigurationException
    {
        try
        {
            Method m = MigrationManager.class.getDeclaredMethod("announceNewTable", TableMetadata.class, boolean.class, long.class);
            m.setAccessible(true);
            m.invoke(null, expectedTable, Boolean.FALSE, 0L);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof ConfigurationException)
            {
                throw (ConfigurationException) cause;
            }

            throw new ConfigurationException("Failed to create table: " + expectedTable.name, e);
        }
        catch (NoSuchMethodException | IllegalAccessException | SecurityException | IllegalArgumentException e)
        {
            throw new ConfigurationException("Failed to create table: " + expectedTable.name, e);
        }
    }
}
