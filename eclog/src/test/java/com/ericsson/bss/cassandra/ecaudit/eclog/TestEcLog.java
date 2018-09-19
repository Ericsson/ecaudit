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
package com.ericsson.bss.cassandra.ecaudit.eclog;

import java.util.function.Consumer;

import org.apache.commons.cli.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestEcLog
{
    @Mock
    private OptionParser parser;

    @Mock
    private ToolOptions options;

    @Mock
    private Consumer<Integer> exiter;

    @Test
    public void testWorking() throws ParseException
    {
        when(parser.parse(any(String[].class))).thenReturn(options);
        ToolOptions actualOptions = EcLog.getToolOptions(new String[]{ "some/path" }, parser, exiter);
        assertThat(actualOptions).isEqualTo(options);
    }

    @Test
    public void testHelp() throws ParseException
    {
        when(parser.parse(any(String[].class))).thenReturn(options);
        when(options.help()).thenReturn(true);
        EcLog.getToolOptions(new String[]{ "-h" }, parser, exiter);
        verify(parser).printUsage();
        verify(exiter).accept(eq(0));
    }

    @Test
    public void testInvalidArgument() throws ParseException
    {
        when(parser.parse(any(String[].class))).thenThrow(new ParseException("Invalid argument"));
        EcLog.getToolOptions(new String[]{ "--wrong" }, parser, exiter);
        verify(parser).printUsage();
        verify(exiter).accept(eq(1));
    }
}
