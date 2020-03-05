/*
 * Copyright 2018 Telefonaktiebolaget LM Ericsson
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.auth.GrantResource;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.Resources;
import org.apache.cassandra.auth.RoleResource;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class TestRoleWhitelistChecker
{
    private static final Map<IResource, Set<Permission>> WHITELIST = ImmutableMap.of(
        DataResource.fromName("data/ks"), ImmutableSet.of(Permission.SELECT),
        DataResource.fromName("data/ks/tbl"), ImmutableSet.of(Permission.CREATE, Permission.MODIFY),
        GrantResource.fromResource(DataResource.fromName("data")), ImmutableSet.of(Permission.DESCRIBE),
        GrantResource.fromResource(RoleResource.fromName("roles/kalle")), ImmutableSet.of(Permission.ALTER),
        GrantResource.root(), ImmutableSet.of(Permission.DROP)
    );

    @SuppressWarnings("unused")
    private Object[] parametersForTestWhitelistChecker()
    {
        return new Object[]{
            new Object[]{ Permission.SELECT, DataResource.fromName("data/ks"), true, false },       // keyspace whitelisted
            new Object[]{ Permission.SELECT, DataResource.fromName("data/ks/tbl"), true, false },   // table (through keyspace) whitelisted
            new Object[]{ Permission.CREATE, DataResource.fromName("data/ks/tbl"), true, false },   // table whitelisted
            new Object[]{ Permission.CREATE, DataResource.fromName("data/ks"), false, false },      // CREATE not whitelisted
            new Object[]{ Permission.DESCRIBE, DataResource.fromName("data/ks/tbl"), false, true }, // table (through data) grant whitelisted
            new Object[]{ Permission.ALTER, RoleResource.fromName("roles/kalle"), false, true },    // role (kalle) grant whitelisted
            new Object[]{ Permission.DROP, RoleResource.fromName("roles"), false, true },           // DROP grant whitelisted (through root-level grant)
        };
    }

    @Test
    @Parameters
    public void testWhitelistChecker(Permission operation, IResource resource, boolean expectedWhitelisted, boolean expectedGrantWhitelisted)
    {
        List<? extends IResource> resourceChain = Resources.chain(resource);

        RoleWhitelistChecker checker = new RoleWhitelistChecker(operation, resourceChain, WHITELIST);

        assertThat(checker.isWhitelisted()).isEqualTo(expectedWhitelisted);
        assertThat(checker.isGrantWhitelisted()).isEqualTo(expectedGrantWhitelisted);
    }
}
