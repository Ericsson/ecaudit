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
package com.ericsson.bss.cassandra.ecaudit.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.auth.ConnectionResource;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;

@RunWith(MockitoJUnitRunner.class)
public class TestYamlAuditFilter
{
    @Mock
    private AuditConfigurationLoader configLoaderMock;

    @Test
    public void testWhitelistOnlyFiltersWhitelistedUsers()
    {
        AuditConfig config = new AuditConfig();
        config.setWhitelist(Arrays.asList("User1", "User2"));

        when(configLoaderMock.loadConfig()).thenReturn(config);

        YamlAuditFilter filter = new YamlAuditFilter(configLoaderMock);

        List<String> users = new ArrayList<>(Arrays.asList("foo", "User1", "bar", "User2", "fnord", "another"));

        assertThat(users.stream().map(TestYamlAuditFilter::toLogEntry).map(entry -> filter.isFiltered(entry))
                .collect(Collectors.toList()))
                        .containsExactly(false, true, false, true, false, false);
    }

    @Test
    public void tesatWhitelistDoesntApplyToLoginAttempts()
    {
        AuditConfig config = new AuditConfig();
        config.setWhitelist(Arrays.asList("User1", "User2"));

        when(configLoaderMock.loadConfig()).thenReturn(config);

        YamlAuditFilter filter = new YamlAuditFilter(configLoaderMock);

        List<String> users = new ArrayList<>(Arrays.asList("foo", "User1", "bar", "User2", "fnord", "another"));

        assertThat(users.stream()
                .map(TestYamlAuditFilter::toLogEntry)
                .map(TestYamlAuditFilter::asLoginEntry)
                .map(entry -> filter.isFiltered(entry))
                .collect(Collectors.toList()))
                .containsOnly(false);
    }

    @Test(expected = ConfigurationException.class)
    public void testExceptionOnConfigError()
    {
        when(configLoaderMock.loadConfig()).thenThrow(new ConfigurationException("something failed"));

        new YamlAuditFilter(configLoaderMock);
    }

    private static AuditEntry toLogEntry(String user)
    {
        return AuditEntry.newBuilder()
                .user(user)
                .build();
    }

    private static AuditEntry asLoginEntry(AuditEntry entry)
    {
        return AuditEntry.newBuilder()
                .basedOn(entry)
                .resource(ConnectionResource.root())
                .build();
    }
}
