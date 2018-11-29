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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.FunctionResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.RoleResource;

class ResourceFactory
{
    private static final String DATA_ROOT = "data";
    private static final String ROLES_ROOT = "roles";
    private static final String CONNECTIONS_ROOT = "connections";
    private static final String FUNCTIONS_ROOT = "functions";

    private static final String SEPARATOR = "/";

    static Set<IResource> toResourceSet(String[] resourceNames)
    {
        return Arrays
               .stream(resourceNames)
               .map(String::trim)
               .map(ResourceFactory::toResource)
               .collect(Collectors.toSet());
    }

    static Set<IResource> toResourceSet(Set<String> resourceNames)
    {
        return resourceNames
               .stream()
               .map(String::trim)
               .map(ResourceFactory::toResource)
               .collect(Collectors.toSet());
    }

    static IResource toResource(String resourceName)
    {
        String[] parts = StringUtils.split(resourceName, SEPARATOR, 2);

        switch (parts[0])
        {
            case DATA_ROOT:
                DataResource dataResource = DataResource.fromName(resourceName);
                validateDataResourceName(dataResource);
                return dataResource;
            case ROLES_ROOT:
                RoleResource roleResource = RoleResource.fromName(resourceName);
                validateRoleResourceName(roleResource);
                return roleResource;
            case CONNECTIONS_ROOT:
                return ConnectionResource.fromName(resourceName);
            case FUNCTIONS_ROOT:
                FunctionResource functionResource = FunctionResource.fromName(resourceName);
                validateFunctionResourceName(functionResource);
                return functionResource;
            default:
                throw new IllegalArgumentException("Invalid resource type: " + resourceName);
        }
    }

    private static void validateDataResourceName(DataResource dataResource)
    {
        if (!dataResource.isRootLevel())
        {
            if (!dataResource.getKeyspace().matches("\\w+"))
            {
                throw new IllegalArgumentException(String.format("\"%s\" is not a valid keyspace name", dataResource.getKeyspace()));
            }
            if (dataResource.isTableLevel() && !dataResource.getTable().matches("\\w+"))
            {
                throw new IllegalArgumentException(String.format("\"%s\" is not a valid table name", dataResource.getTable()));
            }
        }
    }

    private static void validateRoleResourceName(RoleResource roleResource)
    {
        if (roleResource.hasParent())
        {
            if (!roleResource.getRoleName().matches("\\w+"))
            {
                throw new IllegalArgumentException(String.format("\"%s\" is not a valid role name", roleResource.getRoleName()));
            }
        }
    }

    private static void validateFunctionResourceName(FunctionResource functionResource)
    {
        if (functionResource.hasParent())
        {
            if (!functionResource.getKeyspace().matches("\\w+"))
            {
                throw new IllegalArgumentException(String.format("\"%s\" is not a valid keyspace name", functionResource.getKeyspace()));
            }
        }
    }

    static String toPrintableName(IResource resource)
    {
        return "AUDIT WHITELIST ON " + resource.getName();
    }
}
