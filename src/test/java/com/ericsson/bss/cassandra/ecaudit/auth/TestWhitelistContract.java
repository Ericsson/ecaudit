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

import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;

import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.Permission;
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
    public void testGrantSelectOnData()
    {
        contract.verify(ImmutableSet.of(Permission.SELECT), DataResource.fromName("data/ks/table"));
    }

    @Test
    public void testRevokeSelectOnData()
    {
        contract.verify(ImmutableSet.of(Permission.SELECT), DataResource.fromName("data/ks/table"));
    }

    @Test
    public void testGrantModifyOnData()
    {
        contract.verify(ImmutableSet.of(Permission.MODIFY), DataResource.fromName("data/ks/table"));
    }

    @Test
    public void testGrantSelectAndModifyOnData()
    {
        contract.verify(ImmutableSet.of(Permission.SELECT, Permission.MODIFY), DataResource.fromName("data/ks/table"));
    }

    @Test
    public void testRevokeModifyOnData()
    {
        contract.verify(ImmutableSet.of(Permission.MODIFY), DataResource.fromName("data/ks/table"));
    }

    @Test
    public void testGrantExecuteOnConnections()
    {
        contract.verify(ImmutableSet.of(Permission.EXECUTE), ConnectionResource.fromName("connections"));
    }

    @Test
    public void testGrantSelectOnConnections()
    {
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> contract.verify(ImmutableSet.of(Permission.SELECT), ConnectionResource.fromName("connections")));
    }
}
