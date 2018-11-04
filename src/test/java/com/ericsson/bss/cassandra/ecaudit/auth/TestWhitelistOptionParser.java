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

import org.junit.Before;
import org.junit.Test;

import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.exceptions.InvalidRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestWhitelistOptionParser
{
    private WhitelistOptionParser parser = null;

    @Before
    public void before()
    {
        parser = new WhitelistOptionParser();
    }

    @Test
    public void testParseWhitelistOperationGrant()
    {
        WhitelistOperation operation = parser.parseWhitelistOperation("grant_audit_whitelist_for_select");
        assertThat(operation).isEqualTo(WhitelistOperation.GRANT);
    }

    @Test
    public void testParseWhitelistOperationRevoke()
    {
        WhitelistOperation operation = parser.parseWhitelistOperation("revoke_audit_whitelist_for_modify");
        assertThat(operation).isEqualTo(WhitelistOperation.REVOKE);
    }

    @Test
    public void testParseWhitelistOperationFoo()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseWhitelistOperation("foo_audit_whitelist_for_select"));
    }

    @Test
    public void testParseGrantSelect()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseTargetOperation("grant_audit_whitelist_for_select"));
    }

    @Test
    public void testParseRevokeSelect()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseTargetOperation("revoke_audit_whitelist_for_select"));
    }

    @Test
    public void testParseGrantModify()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseTargetOperation("grant_audit_whitelist_for_modify"));
    }

    @Test
    public void testParseGrantAll()
    {
        String operation = parser.parseTargetOperation("grant_audit_whitelist_for_all");
        assertThat(operation).isEqualTo("ALL");
    }

    @Test
    public void testParseGrantGuck()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseTargetOperation("grant_audit_whitelist_for_guck"));
    }

    @Test
    public void testParseSingleResource()
    {
        Set<IResource> resources = parser.parseResource("data/ks/tbl");
        assertThat(resources).containsExactly(DataResource.fromName("data/ks/tbl"));
    }

    @Test
    public void testParseSeveralResources()
    {
        Set<IResource> resources = parser.parseResource("data/ks/tbl1, data/ks/tbl2,data/ks/tbl3");
        assertThat(resources).containsExactlyInAnyOrder(DataResource.fromName("data/ks/tbl1"), DataResource.fromName("data/ks/tbl2"), DataResource.fromName("data/ks/tbl3"));
    }

    @Test
    public void testParseInvalidResource()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseResource("guck/ks/tbl1"));
    }
}
