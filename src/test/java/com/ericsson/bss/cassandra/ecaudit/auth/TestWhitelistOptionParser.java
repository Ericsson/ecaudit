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

import org.junit.Before;
import org.junit.Test;

import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.exceptions.InvalidRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestWhitelistOptionParser
{
    private WhitelistOptionParser parser;

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
    public void testParseWhitelistUppercaseAndSpace()
    {
        WhitelistOperation operation = parser.parseWhitelistOperation("REVOKE AUDIT WHITELIST FOR MODIFY");
        assertThat(operation).isEqualTo(WhitelistOperation.REVOKE);
    }

    @Test
    public void testParseDropLegacy()
    {
        WhitelistOperation operation = parser.parseWhitelistOperation("DROP LEGACY AUDIT WHITELIST TABLE");
        assertThat(operation).isEqualTo(WhitelistOperation.DROP_LEGACY);
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
        Set<Permission> operations = parser.parseTargetOperation("grant_audit_whitelist_for_select", DataResource.fromName("data"));
        assertThat(operations).containsExactlyInAnyOrder(Permission.SELECT);
    }

    @Test
    public void testParseRevokeSelect()
    {
        Set<Permission> operations = parser.parseTargetOperation("revoke_audit_whitelist_for_select", DataResource.fromName("data"));
        assertThat(operations).containsExactlyInAnyOrder(Permission.SELECT);
    }

    @Test
    public void testParseGrantModify()
    {
        Set<Permission> operations = parser.parseTargetOperation("grant_audit_whitelist_for_modify", DataResource.fromName("data"));
        assertThat(operations).containsExactlyInAnyOrder(Permission.MODIFY);
    }

    @Test
    public void testParseGrantModifyUppercaseAndSpace()
    {
        Set<Permission> operations = parser.parseTargetOperation("GRANT AUDIT WHITELIST FOR MODIFY", DataResource.fromName("data"));
        assertThat(operations).containsExactlyInAnyOrder(Permission.MODIFY);
    }

    @Test
    public void testParseGrantAll()
    {
        Set<Permission> operations = parser.parseTargetOperation("grant_audit_whitelist_for_all", DataResource.fromName("data"));
        assertThat(operations).containsExactlyInAnyOrder(Permission.CREATE, Permission.ALTER, Permission.DROP, Permission.SELECT, Permission.MODIFY, Permission.AUTHORIZE);
    }

    @Test
    public void testParseGrantGuck()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseTargetOperation("grant_audit_whitelist_for_guck", DataResource.fromName("data")));
    }

    @Test
    public void testParseSingleResource()
    {
        IResource resource = parser.parseResource("data/ks/tbl");
        assertThat(resource).isEqualTo(DataResource.fromName("data/ks/tbl"));
    }

    @Test
    public void testParseSeveralResources()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseResource("data/ks/tbl1, data/ks/tbl2,data/ks/tbl3"));
    }

    @Test
    public void testParseInvalidResource()
    {
        assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> parser.parseResource("guck/ks/tbl1"));
    }
}
