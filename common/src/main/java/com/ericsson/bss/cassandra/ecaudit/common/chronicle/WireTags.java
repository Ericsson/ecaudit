/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.common.chronicle;

class WireTags
{
    static final String KEY_VERSION = "version";
    static final String KEY_TYPE = "type";
    static final String KEY_TIMESTAMP = "timestamp";
    static final String KEY_CLIENT = "client";
    static final String KEY_COORDINATOR = "coordinator";
    static final String KEY_USER = "user";
    static final String KEY_BATCH_ID = "batchId";
    static final String KEY_STATUS = "status";
    static final String KEY_OPERATION = "operation";

    static final short VALUE_VERSION_CURRENT = 0;
    static final String VALUE_TYPE_BATCH_ENTRY = "ecaudit-batch";
    static final String VALUE_TYPE_SINGLE_ENTRY = "ecaudit-single";
}
