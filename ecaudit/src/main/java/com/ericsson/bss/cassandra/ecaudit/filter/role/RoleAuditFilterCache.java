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
package com.ericsson.bss.cassandra.ecaudit.filter.role;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;

import org.apache.cassandra.auth.AuthCache;

public class RoleAuditFilterCache extends AuthCache<RoleAuditFilterCacheKey, Boolean>
{
    private static final AtomicInteger UNIQUE_ID = new AtomicInteger();

    RoleAuditFilterCache(Function<RoleAuditFilterCacheKey, Boolean> loadFunction)
    {
        this(loadFunction, AuditConfig.getInstance());
    }

    @VisibleForTesting
    RoleAuditFilterCache(Function<RoleAuditFilterCacheKey, Boolean> loadFunction, AuditConfig auditConfig)
    {
        super("RoleAuditFilterCache" + UNIQUE_ID.incrementAndGet(), // Unique name is needed for unit tests to work
              auditConfig::setWhitelistCacheValidity,
              auditConfig::getWhitelistCacheValidity,
              auditConfig::setWhitelistCacheUpdateInterval,
              auditConfig::getWhitelistCacheUpdateInterval,
              auditConfig::setWhitelistCacheMaxEntries,
              auditConfig::getWhitelistCacheMaxEntries,
              auditConfig::setWhitelistCacheActiveUpdate,
              auditConfig::isWhitelistCacheActiveUpdate,
              loadFunction,
              Collections::emptyMap, //Consider creating bilk loader
              () -> true);
    }

    public boolean isWhitelisted(RoleAuditFilterCacheKey cacheKey)
    {
        try
        {
            return get(cacheKey);
        }
        catch (Exception e)
        {
            // The call to get() may throw ExecutionException in version 3.11.4 and older
            // We're catching Exception here to remain compatible with those older versions
            throw new UncheckedExecutionException(e);
        }
    }
}
