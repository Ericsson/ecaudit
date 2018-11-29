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

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.Test;

import org.apache.cassandra.auth.Permission;

import static org.assertj.core.api.Assertions.assertThat;

public class TestOperationFactory
{
    @Test
    public void stringSetToEnumSet()
    {
        Set<String> stringSet = Sets.newHashSet(Arrays.asList("SELECT", "MODIFY"));
        Set<Permission> permissionSet = OperationFactory.toOperationSet(stringSet);
        assertThat(permissionSet).containsExactly(Permission.SELECT, Permission.MODIFY);
    }

    @Test
    public void emptyStringSetToEnumSet()
    {
        Set<String> stringSet = Collections.emptySet();
        Set<Permission> permissionSet = OperationFactory.toOperationSet(stringSet);
        assertThat(permissionSet).isEmpty();
    }

    @Test
    public void enumSetToCsv()
    {
        Set<Permission> enumSet = Sets.immutableEnumSet(Permission.EXECUTE, Permission.ALTER);
        String csv = OperationFactory.toOperationNameCsv(enumSet);
        assertThat(csv).isEqualTo("ALTER,EXECUTE");
    }

    @Test
    public void emptyEnumSetToCsv()
    {
        Set<Permission> enumSet = Collections.emptySet();
        String csv = OperationFactory.toOperationNameCsv(enumSet);
        assertThat(csv).isEqualTo("");
    }
}
