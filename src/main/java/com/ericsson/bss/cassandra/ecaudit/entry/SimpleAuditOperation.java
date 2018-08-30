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
package com.ericsson.bss.cassandra.ecaudit.entry;

/**
 * This audit operation simply wraps an operation/statement and provides it on request.
 */
public class SimpleAuditOperation implements AuditOperation
{
    private final String operationString;

    /**
     * Construct a new audit operation.
     * @param operationString the operation/statement to wrap.
     */
    public SimpleAuditOperation(String operationString)
    {
        this.operationString = operationString;
    }

    @Override
    public String getOperationString()
    {
        return operationString;
    }

    @Override
    public String toString()
    {
        return "Simple audit operation: "  + operationString;
    }
}
