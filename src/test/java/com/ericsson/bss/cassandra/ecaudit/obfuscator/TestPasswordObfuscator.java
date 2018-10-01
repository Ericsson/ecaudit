//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.obfuscator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.SimpleAuditOperation;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class TestPasswordObfuscator
{
    PasswordObfuscator myObfuscator;

    @Before
    public void before()
    {
        myObfuscator = new PasswordObfuscator();
    }

    @Test
    public void testNoObfuscationOfNonAuthStatement()
    {
        // These queries include the password keyword but shouldn't be obfuscated
        List<AuditOperation> queries = Arrays.asList(
        new SimpleAuditOperation("Authentication attempt"),
        new SimpleAuditOperation("select password from ks.tbl"),
        new SimpleAuditOperation("insert into ks.tbl (user, password) values ('foo', 'password')"));

        for (AuditOperation query : queries)
        {
            AuditEntry entry = AuditEntry.newBuilder()
                                         .operation(query)
                                         .permissions(Sets.immutableEnumSet(Permission.SELECT))
                                         .resource(RoleResource.fromName("roles/olle"))
                                         .build();

            AuditEntry obfuscated = myObfuscator.obfuscate(entry);
            assertThat(obfuscated.getOperation()).isSameAs(query);
        }
    }

    @Test
    public void testNoObfuscationOfNonPasswordRoleStatement()
    {
        // These queries manages roles but shouldn't be obfuscated
        List<String> alterRoleQueries = new ArrayList<>();
        alterRoleQueries.add("ALTER ROLE helena WITH LOGIN = true;");
        alterRoleQueries.add("ALTER ROLE helena WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data, roles, connection' }");

        validateUnmodifiedQueries(alterRoleQueries, "helena", Permission.ALTER);
    }

    @Test
    public void testCreateRolePasswordObfuscation()
    {
        Map<String, String> createRoleQueries = createPasswordQueries(
        "CREATE ROLE coach WITH PASSWORD = '%s' AND LOGIN = true;",
        "CREATE ROLE coach WITH PASSWORD ='%s' AND LOGIN = true;",
        "CREATE ROLE coach WITH PASSWORD='%s' AND LOGIN = true;",
        "CREATE ROLE coach WITH PASSWORD= '%s' AND LOGIN = true;",
        "CREATE ROLE coach WITH PASSWORD  =   '%s' AND LOGIN = true;");

        validateQueries(createRoleQueries, "coach", Permission.CREATE);
    }

    @Test
    public void testCreateUserPasswordObfuscation()
    {
        Map<String, String> createUserQueries = createPasswordQueries(
        "CREATE USER akers WITH PASSWORD '%s' SUPERUSER;",
        "CREATE USER akers WITH PASSWORD  '%s'  SUPERUSER;",
        "CREATE USER akers WITH PASSWORD  '%s'  SUPERUSER;");

        validateQueries(createUserQueries, "akers", Permission.CREATE);
    }

    @Test
    public void testAlterRolePasswordObfuscation()
    {
        Map<String, String> alterRoleQueries = createPasswordQueries(
        "ALTER ROLE coach WITH PASSWORD = '%s';",
        "ALTER ROLE coach WITH PASSWORD = '%s'",
        "ALTER ROLE coach WITH PASSWORD ='%s'",
        "ALTER ROLE coach WITH PASSWORD='%s'",
        "ALTER ROLE coach WITH PASSWORD=  '%s'");

        validateQueries(alterRoleQueries, "coach", Permission.ALTER);
    }

    @Test
    public void testAlterUserPasswordObfuscation()
    {
        Map<String, String> alterUserQueries = createPasswordQueries(
        "ALTER USER moss WITH PASSWORD '%s';",
        "ALTER USER moss WITH PASSWORD  '%s';",
        "ALTER USER moss WITH PASSWORD '%s' ;",
        "ALTER USER moss WITH PASSWORD  '%s'    ;");

        validateQueries(alterUserQueries, "moss", Permission.ALTER);
    }

    private void validateUnmodifiedQueries(List<String> queries, String username, Permission permission)
    {
        for (String query : queries)
        {
            AuditEntry entry = AuditEntry.newBuilder()
                                         .operation(new SimpleAuditOperation(query))
                                         .permissions(Sets.immutableEnumSet(permission))
                                         .resource(RoleResource.fromName("roles/" + username))
                                         .build();

            AuditEntry obfuscated = myObfuscator.obfuscate(entry);
            assertThat(obfuscated.getOperation().getOperationString()).isSameAs(query);
        }
    }

    private void validateQueries(Map<String, String> queries, String username, Permission permission)
    {
        for (String query : queries.keySet())
        {
            AuditEntry entry = AuditEntry.newBuilder()
                                         .operation(new SimpleAuditOperation(query))
                                         .permissions(Sets.immutableEnumSet(permission))
                                         .resource(RoleResource.fromName("roles/" + username))
                                         .build();

            AuditEntry obfuscated = myObfuscator.obfuscate(entry);
            assertThat(obfuscated.getOperation().getOperationString()).isEqualTo(queries.get(query));
        }
    }

    private static Map<String, String> createPasswordQueries(String... variants)
    {
        Map<String, String> passwordQueries = new HashMap<>();

        for (String variant : variants)
        {
            String withPassword = String.format(variant, UUID.randomUUID().toString());
            String obfuscatedPassword = String.format(variant, "*****");

            passwordQueries.put(withPassword, obfuscatedPassword);
        }

        return Collections.unmodifiableMap(passwordQueries);
    }
}
