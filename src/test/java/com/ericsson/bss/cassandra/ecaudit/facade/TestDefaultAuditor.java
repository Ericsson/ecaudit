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
package com.ericsson.bss.cassandra.ecaudit.facade;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import com.ericsson.bss.cassandra.ecaudit.logger.AuditLogger;
import com.ericsson.bss.cassandra.ecaudit.obfuscator.AuditObfuscator;

@RunWith(MockitoJUnitRunner.class)
public class TestDefaultAuditor
{
    @Mock
    AuditLogger mockLogger;

    @Mock
    AuditFilter mockFilter;

    @Mock
    AuditObfuscator mockObfuscator;

    @InjectMocks
    DefaultAuditor auditor;

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockLogger, mockFilter, mockObfuscator);
    }

    @Test
    public void testAuditFiltered()
    {
        AuditEntry logEntry = AuditEntry.newBuilder().build();
        when(mockFilter.isFiltered(logEntry)).thenReturn(true);

        auditor.audit(logEntry);
        verify(mockFilter, times(1)).isFiltered(logEntry);
        verifyZeroInteractions(mockLogger, mockObfuscator);
    }

    @Test
    public void testAuditNotFiltered()
    {
        AuditEntry logEntry = AuditEntry.newBuilder().build();
        when(mockFilter.isFiltered(logEntry)).thenReturn(false);
        when(mockObfuscator.obfuscate(logEntry)).thenReturn(logEntry);

        auditor.audit(logEntry);
        verify(mockFilter, times(1)).isFiltered(logEntry);
        verify(mockObfuscator, times(1)).obfuscate(logEntry);
        verify(mockLogger, times(1)).log(logEntry);
    }
}
