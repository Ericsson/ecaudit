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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.cassandra.auth.Permission;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestConnectionResource
{
    @Test
    public void testRootResource()
    {
        ConnectionResource root = ConnectionResource.root();

        assertThat(root).isEqualTo(ConnectionResource.fromName("connections"));
        assertThat(root).isEqualByComparingTo(ConnectionResource.fromName("connections"));
        assertThat(root).isNotEqualTo(null);
        assertThat(root).isNotEqualTo("other type");
        assertThat(root.exists()).isEqualTo(true);
        assertThat(root.applicablePermissions()).containsExactly(Permission.AUTHORIZE, Permission.EXECUTE);
        assertThat(root.getName()).isEqualTo("connections");
    }

    @Test(expected = IllegalStateException.class)
    public void testRootHasNoParent()
    {
        ConnectionResource root = ConnectionResource.root();
        assertThat(root.hasParent()).isEqualTo(false);
        root.getParent();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnlyRootIsSupported()
    {
        ConnectionResource.fromName("connections/native");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRoot()
    {
        ConnectionResource.fromName("foo");
    }
}
