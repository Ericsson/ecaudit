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

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.cassandra.auth.Permission;

class OperationFactory
{
    static Set<Permission> toOperationSet(Set<String> permissionNames)
    {
        return permissionNames
               .stream()
               .map(String::trim)
               .map(Permission::valueOf)
               .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }

    static String toOperationNameCsv(Set<Permission> operations)
    {
        return operations.stream()
                         .map(Permission::name)
                         .collect(Collectors.joining(","));
    }
}
