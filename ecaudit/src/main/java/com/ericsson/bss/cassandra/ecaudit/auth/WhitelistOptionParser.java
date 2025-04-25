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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import com.ericsson.bss.cassandra.ecaudit.utils.Exceptions;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.exceptions.InvalidRequestException;

class WhitelistOptionParser
{
    private static final String ALL_OPERATIONS = "ALL";
    private static final String GRANT_PREFIX = "grant_audit_whitelist_for_";
    private static final String REVOKE_PREFIX = "revoke_audit_whitelist_for_";
    private static final String VALID_PREFIX = "^" + GRANT_PREFIX + "|" + "^" + REVOKE_PREFIX;
    private static final String MIGRATE_PATTERN = "migrate_audit_whitelist_table";
    private static final String DEMIGRATE_PATTERN = "syncback_audit_whitelist_table";

    WhitelistOperation parseWhitelistOperation(String inputOption)
    {
        String normalizedInput = normalizeUserInput(inputOption);

        if (normalizedInput.startsWith(GRANT_PREFIX))
        {
            return WhitelistOperation.GRANT;
        }
        else if (normalizedInput.startsWith(REVOKE_PREFIX))
        {
            return WhitelistOperation.REVOKE;
        }
        else if (normalizedInput.matches(MIGRATE_PATTERN))
        {
            return WhitelistOperation.MIGRATE;
        }
        else if (normalizedInput.matches(DEMIGRATE_PATTERN))
        {
            return WhitelistOperation.DEMIGRATE;
        }
        else
        {
            throw new InvalidRequestException("Invalid whitelist operation option: " + inputOption);
        }
    }

    Set<Permission> parseTargetOperation(String inputOption, IResource resource)
    {
        String operationString = stripOptionPrefix(inputOption).toUpperCase();

        if (ALL_OPERATIONS.equals(operationString))
        {
            return resource.applicablePermissions();
        }

        try
        {
            return ImmutableSet.of(Permission.valueOf(operationString));
        }
        catch (IllegalArgumentException e)
        {
            throw Exceptions.appendCause(new InvalidRequestException("Invalid whitelist option: " + e.getMessage()), e);
        }
    }

    private String stripOptionPrefix(String option)
    {
        return normalizeUserInput(option).replaceFirst(VALID_PREFIX, "");
    }

    private String normalizeUserInput(String option)
    {
        return option.trim().replaceAll("\\s+", "_").toLowerCase();
    }

    IResource parseResource(String resourceName)
    {
        try
        {
            return ResourceFactory.toResource(resourceName);
        }
        catch (IllegalArgumentException e)
        {
            throw Exceptions.appendCause(new InvalidRequestException(String.format("Unable to parse whitelisted resource [%s]: %s", resourceName, e.getMessage())), e);
        }
    }
}
