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

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.config.AuditConfig;
import org.awaitility.Awaitility;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestRoleAuditFilterCache
{
    @Mock
    AuditConfig auditConfig;

    @Mock
    RoleAuditFilterCacheKey cacheKey;

    @Mock
    Function<RoleAuditFilterCacheKey, Boolean> loadFunction;

    @Test
    public void testValueIsReadEveryTimeWhenCacheDisabled()
    {
        givenCacheValidity(0); // Disable cache

        when(loadFunction.apply(cacheKey)).thenReturn(true, false);
        RoleAuditFilterCache cache = new RoleAuditFilterCache(loadFunction, auditConfig);

        assertThat(cache.isWhitelisted(cacheKey)).isTrue();
        assertThat(cache.isWhitelisted(cacheKey)).isFalse();
        verify(loadFunction, times(2)).apply(cacheKey);
    }

    @Test
    public void testValueIsCached()
    {
        givenCacheValidity(500);

        when(loadFunction.apply(cacheKey)).thenReturn(true, false);
        RoleAuditFilterCache cache = new RoleAuditFilterCache(loadFunction, auditConfig);

        assertThat(cache.isWhitelisted(cacheKey)).isTrue();
        assertThat(cache.isWhitelisted(cacheKey)).isTrue();
        verify(loadFunction, times(1)).apply(cacheKey);
    }

    @Test
    public void testValueIsRefreshed()
    {
        givenCacheValidity(500);

        when(loadFunction.apply(cacheKey)).thenReturn(true, false);
        RoleAuditFilterCache cache = new RoleAuditFilterCache(loadFunction, auditConfig);

        assertThat(cache.isWhitelisted(cacheKey)).isTrue();

        // Cache expires after 0.5 sec
        Awaitility.await().pollInterval(250, TimeUnit.MILLISECONDS)
                  .atMost(1, TimeUnit.SECONDS)
                  .until(() -> cache.isWhitelisted(cacheKey) == false);

        verify(loadFunction, times(2)).apply(cacheKey);
    }

    private void givenCacheValidity(int validityTime)
    {
        when(auditConfig.getWhitelistCacheValidity()).thenReturn(validityTime);
        when(auditConfig.getWhitelistCacheUpdateInterval()).thenReturn(validityTime);
        when(auditConfig.getWhitelistCacheMaxEntries()).thenReturn(10);
    }
}
