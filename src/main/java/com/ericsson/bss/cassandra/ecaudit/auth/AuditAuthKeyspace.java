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

import java.util.concurrent.TimeUnit;

import org.apache.cassandra.auth.AuthKeyspace;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.KeyspaceParams;
import org.apache.cassandra.schema.Tables;

final class AuditAuthKeyspace
{
    static final String WHITELIST_TABLE_NAME = "role_audit_whitelists";
    private static final String WHITELIST_TABLE_SCHEMA = "CREATE TABLE " + WHITELIST_TABLE_NAME + " ("
                                                         + "role text,"
                                                         + "operation text,"
                                                         + "resources set<text>,"
                                                         + "PRIMARY KEY(role, operation))";
    private static final String WHITELIST_TABLE_DESCRIPTION = "audit whitelist assigned to db roles";
    private static final int WHITELIST_TABLE_GC_GRACE_SECONDS = (int) TimeUnit.DAYS.toSeconds(90);

    private static final CFMetaData CREATE_ROLE_AUDIT_WHITELISTS =
    CFMetaData.compile(WHITELIST_TABLE_SCHEMA, AuthKeyspace.NAME)
              .comment(WHITELIST_TABLE_DESCRIPTION)
              .gcGraceSeconds(WHITELIST_TABLE_GC_GRACE_SECONDS);

    static KeyspaceMetadata metadata()
    {
        return KeyspaceMetadata.create(AuthKeyspace.NAME, KeyspaceParams.simple(1), Tables.of(CREATE_ROLE_AUDIT_WHITELISTS));
    }
}
