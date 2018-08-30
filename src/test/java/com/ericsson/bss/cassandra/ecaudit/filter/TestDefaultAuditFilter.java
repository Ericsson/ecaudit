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
package com.ericsson.bss.cassandra.ecaudit.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.AuditConfig;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.AuditYamlConfigurationLoader;

@RunWith(MockitoJUnitRunner.class)
public class TestDefaultAuditFilter
{
    @Test
    public void testWhitelistNotFilteredWithDefaultFilter()
    {
        Properties properties = getProperties("mock_configuration.yaml");
        AuditYamlConfigurationLoader loader = AuditYamlConfigurationLoader.withProperties(properties);

        AuditConfig loadedConfig = loader.loadConfig();
        assertThat(loadedConfig.getWhitelist()).containsOnly("User1", "User2");

        DefaultAuditFilter filter = new DefaultAuditFilter();

        assertThat(loadedConfig.getWhitelist().stream()
                .map(TestDefaultAuditFilter::toLogEntry)
                .map(entry -> filter.isFiltered(entry))
                .collect(Collectors.toList()))
                        .containsExactly(false, false);
    }

    private static Properties getProperties(String fileName)
    {
        URL url = TestDefaultAuditFilter.class.getResource("/" + fileName);
        Properties properties = new Properties();
        properties.put(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, url.getPath());

        return properties;
    }

    private static AuditEntry toLogEntry(String user)
    {
        return AuditEntry.newBuilder()
                .user(user)
                .build();
    }
}
