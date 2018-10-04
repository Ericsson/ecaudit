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

import java.util.Set;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.exceptions.InvalidRequestException;

class WhitelistContract
{
    void verify(Set<Permission> operations, IResource suppliedResource)
    {
        if (!suppliedResource.applicablePermissions().containsAll(operations))
        {
            throw new InvalidRequestException(String.format("Operation(s) %s are not applicable on %s", operations, suppliedResource));
        }
    }

    void verifyCreateRoleOption(WhitelistOperation whitelistOperation)
    {
        if (whitelistOperation != WhitelistOperation.GRANT)
        {
            throw new InvalidRequestException("Only possible to grant audit whitelist when creating a role");
        }
    }
}
