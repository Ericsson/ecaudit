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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.SimpleAuditOperation;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Implements an {@link AuditObfuscator} that obfuscated passwords,
 * if present, in the operation string of the {@link AuditEntry}.
 */
public class PasswordObfuscator implements AuditObfuscator
{
    private final static String PASSWORD_OBFUSCATED = "*****";

    private final static String REGEX_PASSWORD_GROUP = "password";
    private final static Pattern QUERY_CREATE_ALTER_USER_PATTERN =
            Pattern.compile(".*password\\s+'(?<password>[^\\s]+)'.*", Pattern.CASE_INSENSITIVE);
    private final static Pattern QUERY_CREATE_ALTER_ROLE_PATTERN =
            Pattern.compile(".*password\\s*=\\s*'(?<password>[^\\s]+)'.*", Pattern.CASE_INSENSITIVE);

    private final static Set<Permission> PASSWORD_PERMISSIONS = ImmutableSet.of(Permission.CREATE, Permission.ALTER);

    @Override
    public AuditEntry obfuscate(final AuditEntry entry)
    {
        if (isRoleResource(entry.getResource()) && isPasswordPermission(entry.getPermissions()))
        {
            AuditEntry obfuscatedEntry = entry;

            String obfuscatedOperation = obfuscateOperation(entry.getOperation().getOperationString());
            if (!entry.getOperation().getOperationString().equals(obfuscatedOperation))
            {
                obfuscatedEntry = AuditEntry.newBuilder()
                        .basedOn(entry)
                        .operation(new SimpleAuditOperation(obfuscatedOperation))
                        .build();
            }
            return obfuscatedEntry;
        }

        return entry;
    }

    private boolean isRoleResource(IResource resource)
    {
        return resource instanceof RoleResource;
    }

    private boolean isPasswordPermission(Set<Permission> permissions)
    {
        return Sets.intersection(permissions, PASSWORD_PERMISSIONS).size() > 0;
    }

    /**
     * Obfuscate password in the given query, if present
     * @param operation the query to obfuscate password in
     * @return a query with an obfuscated password if present
     */
    private String obfuscateOperation(String operation)
    {
        // CREATE ROLE / ALTER ROLE query
        Matcher createAlterRoleMatcher = QUERY_CREATE_ALTER_ROLE_PATTERN.matcher(operation);
        if (createAlterRoleMatcher.matches())
        {
            return obfuscateOperation(createAlterRoleMatcher, operation);
        }

        // CREATE USER / ALTER USER query
        Matcher createAlterUserMatcher = QUERY_CREATE_ALTER_USER_PATTERN.matcher(operation);
        if (createAlterUserMatcher.matches())
        {
            return obfuscateOperation(createAlterUserMatcher, operation);
        }

        return operation;
    }

    private String obfuscateOperation(Matcher matcher, String operation)
    {
        StringBuilder obfuscated = new StringBuilder();
        int matchStart = matcher.start(REGEX_PASSWORD_GROUP);
        int matchStop = matcher.end(REGEX_PASSWORD_GROUP);

        obfuscated.append(operation, 0, matchStart).append(PASSWORD_OBFUSCATED).append(operation.substring(matchStop));

        return obfuscated.toString();
    }

}
