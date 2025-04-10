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

import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.schema.Schema;
import org.apache.cassandra.schema.SchemaConstants;

import java.util.concurrent.TimeUnit;

final class EcauditKeyspace
{
    static final String ECAUDIT_KEYSPACE_NAME = "system_ecaudit";
    static final String CREATE_KEYSPACE = Schema.instance.getKeyspaceMetadata(SchemaConstants.AUTH_KEYSPACE_NAME)
            .toCqlString(true, false).replace(SchemaConstants.AUTH_KEYSPACE_NAME, ECAUDIT_KEYSPACE_NAME);
    static final String WHITELIST_TABLE_NAME_V2 = "role_audit_whitelists_v2";
    private static final String WHITELIST_TABLE_DESCRIPTION = "audit whitelist assigned to db roles";
    private static final int WHITELIST_TABLE_GC_GRACE_SECONDS = (int) TimeUnit.DAYS.toSeconds(90);
    private static final String CREATE_WHITELIST_TABLE = "CREATE TABLE " + ECAUDIT_KEYSPACE_NAME + "." + WHITELIST_TABLE_NAME_V2 + " ("
            + "role text,"
            + "resource text,"
            + "operations set<text>,"
            + "PRIMARY KEY(role, resource))"
            + "WITH comment = '" + WHITELIST_TABLE_DESCRIPTION + "'"
            + "AND gc_grace_seconds = " + WHITELIST_TABLE_GC_GRACE_SECONDS;

    private EcauditKeyspace()
    {
        // Utility class
    }

    static void createKeyspace()
    {
        QueryProcessor.executeOnceInternal(CREATE_KEYSPACE);
    }

    static void createTable()
    {
        QueryProcessor.executeOnceInternal(CREATE_WHITELIST_TABLE);
    }
}
