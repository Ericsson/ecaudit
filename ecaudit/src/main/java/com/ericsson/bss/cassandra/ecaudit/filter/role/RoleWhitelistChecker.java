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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.ericsson.bss.cassandra.ecaudit.auth.GrantResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

class RoleWhitelistChecker
{
    private final Map<IResource, Set<Permission>> whitelist;
    private final List<? extends IResource> resourceChain;
    private final Permission operation;

    RoleWhitelistChecker(Permission operation, List<? extends IResource> resourceChain, Map<IResource, Set<Permission>> whitelist)
    {
        this.operation = operation;
        this.resourceChain = resourceChain;
        this.whitelist = whitelist;
    }

    boolean isWhitelisted()
    {
        return anyResourceMatchPermission(resourceChain);
    }

    boolean isGrantWhitelisted()
    {
        List<GrantResource> grantResourceChain = resourceChain.stream()
                                                              .map(GrantResource::fromResource)
                                                              .collect(Collectors.toCollection(ArrayList::new));
        grantResourceChain.add(GrantResource.root());

        return anyResourceMatchPermission(grantResourceChain);
    }

    private boolean anyResourceMatchPermission(List<? extends IResource> resourceChain)
    {
        return resourceChain.stream()
                            .map(whitelist::get)
                            .filter(Objects::nonNull)
                            .anyMatch(whitelistedOperations -> whitelistedOperations.contains(operation));
    }
}
