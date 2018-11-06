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

import org.junit.Before;
import org.junit.Test;

import org.apache.cassandra.exceptions.InvalidRequestException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestWhitelistContract
{
    private WhitelistContract contract;

    @Before
    public void before()
    {
        contract = new WhitelistContract();
    }

    @Test
    public void testGrantOnCreate()
    {
        contract.verifyCreateRoleOption(WhitelistOperation.GRANT);
    }

    @Test
    public void testRevokeOnCreate()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> contract.verifyCreateRoleOption(WhitelistOperation.REVOKE));
    }
}
