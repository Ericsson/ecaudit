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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.UncheckedExecutionException;

import com.ericsson.bss.cassandra.ecaudit.auth.cache.AuthCache;
import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;
import org.apache.cassandra.exceptions.UnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleAuditFilterCache extends AuthCache<RoleAuditFilterCacheKey, Boolean>
{
    private static final AtomicInteger UNIQUE_ID = new AtomicInteger();
	
	private static final Logger LOG = LoggerFactory.getLogger(RoleAuditFilterCache.class);

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
              loadFunction,
              () -> true);
    }

    public boolean isWhitelisted(RoleAuditFilterCacheKey cacheKey)
    {
        try
        {
            return get(cacheKey);
        }
		catch(UnavailableException e) {
        	LOG.error("cannot fetch data from AuthCache, message={}", e.getMessage());
        	return false;
        }
        catch (ExecutionException e)
        {
            throw new UncheckedExecutionException(e);
        }
    }
}
