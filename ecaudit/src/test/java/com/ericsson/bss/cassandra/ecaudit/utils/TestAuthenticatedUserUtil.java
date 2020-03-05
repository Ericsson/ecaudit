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
package com.ericsson.bss.cassandra.ecaudit.utils;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.RoleResource;

import static org.assertj.core.api.Assertions.assertThat;

public class TestAuthenticatedUserUtil
{
    @BeforeClass
    public static void beforeAll()
    {
        ClientInitializer.beforeClass();
    }

    @AfterClass
    public static void afterAll()
    {
        ClientInitializer.afterClass();
    }

    @Test
    public void testSuper()
    {
        AuthenticatedUser user = AuthenticatedUserUtil.createFromString("system");
        assertThat(user).isSameAs(AuthenticatedUser.SYSTEM_USER);
        assertThat(user.isSystem()).isTrue();
    }

    @Test
    public void testAnonymous()
    {
        AuthenticatedUser user = AuthenticatedUserUtil.createFromString("anonymous");
        assertThat(user).isSameAs(AuthenticatedUser.ANONYMOUS_USER);
        assertThat(user.isAnonymous()).isTrue();
    }

    @Test
    public void testCustomUser()
    {
        AuthenticatedUser user = AuthenticatedUserUtil.createFromString("Bob");
        assertThat(user.getName()).isEqualTo("Bob");
        assertThat(user.getPrimaryRole()).isEqualTo(RoleResource.role("Bob"));
    }
}
