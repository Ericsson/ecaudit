package com.ericsson.bss.cassandra.ecaudit.auth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.FunctionResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.exceptions.InvalidRequestException;

public class ResourceFactory
{
    private static final String DATA_ROOT = "data";
    private static final String ROLES_ROOT = "roles";
    private static final String CONNECTIONS_ROOT = "connections";
    private static final String FUNCTIONS_ROOT = "functions";

    private static final String SEPARATOR = "/";

    public static Set<IResource> toResourceSet(String resourceCsv)
    {
        return toResourceSet(StringUtils.split(resourceCsv, ','));
    }

    public static Set<IResource> toResourceSet(String[] resourceNames)
    {
        return Arrays
               .stream(resourceNames)
               .map(String::trim)
               .map(ResourceFactory::toResource)
               .collect(Collectors.toSet());
    }

    public static IResource toResource(String resourceName)
    {
        String[] parts = StringUtils.split(resourceName, SEPARATOR, 2);

        switch (parts[0])
        {
            case DATA_ROOT:
                return DataResource.fromName(resourceName);
            case ROLES_ROOT:
                return RoleResource.fromName(resourceName);
            case CONNECTIONS_ROOT:
                return ConnectionResource.fromName(resourceName);
            case FUNCTIONS_ROOT:
                return FunctionResource.fromName(resourceName);
            default:
                throw new IllegalArgumentException("Invalid resource type: " + resourceName);
        }
    }
}
