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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;

import org.apache.commons.cli.ParseException;
import org.junit.Test;

import net.openhft.chronicle.queue.RollCycles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestOptionParser
{
    private OptionParser parser = new OptionParser();

    @Test
    public void noOptions()
    {
        String[] argv = givenInputOptions();

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("directory");
    }

    @Test
    public void withDirectoryOnly() throws ParseException
    {
        String[] argv = givenInputOptions("./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).isEmpty();
        assertThat(options.tail()).isEmpty();
    }

    @Test
    public void withFollowAndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("-f", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isTrue();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).isEmpty();
        assertThat(options.tail()).isEmpty();
    }

    @Test
    public void withLongFollowAndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("--follow", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isTrue();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).isEmpty();
        assertThat(options.tail()).isEmpty();
    }

    @Test
    public void withFollowAndNoDirectory()
    {
        String[] argv = givenInputOptions("-f");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("directory");
    }

    @Test
    public void withRollMinutelyAndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("-r", "MINUTELY", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).contains(RollCycles.MINUTELY);
        assertThat(options.limit()).isEmpty();
        assertThat(options.tail()).isEmpty();
    }

    @Test
    public void withLongRollMinutelyAndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("--roll-cycle", "MINUTELY", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).contains(RollCycles.MINUTELY);
        assertThat(options.limit()).isEmpty();
        assertThat(options.tail()).isEmpty();
    }

    @Test
    public void withRollUndefinedAndDirectory()
    {
        String[] argv = givenInputOptions("-r", "./dir");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("roll cycle")
        .withMessageContaining("DAILY")
        .withMessageContaining("HOURLY")
        .withMessageContaining("MINUTELY");
    }

    @Test
    public void withRollMinutelyAndNoDirectory()
    {
        String[] argv = givenInputOptions("-r", "MINUTELY");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("directory");
    }


    @Test
    public void withRollUndefinedAndNoDirectory()
    {
        String[] argv = givenInputOptions("-r");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("Missing argument");
    }

    @Test
    public void withLimit20AndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("-l", "20", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).contains(20L);
        assertThat(options.tail()).isEmpty();
    }

    @Test
    public void withLongLimit20AndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("--limit", "20", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).contains(20L);
        assertThat(options.tail()).isEmpty();
    }

    @Test
    public void withLimitUndefinedAndDirectory()
    {
        String[] argv = givenInputOptions("-l", "./dir");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("specify number of records");
    }

    @Test
    public void withLimit20AndNoDirectory()
    {
        String[] argv = givenInputOptions("-l", "20");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("directory");
    }

    @Test
    public void withLimitUndefinedAndNoDirectory()
    {
        String[] argv = givenInputOptions("-l");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("Missing argument");
    }

    @Test
    public void withTail20AndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("-t", "20", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).contains(20L);
        assertThat(options.tail()).contains(20L);
    }

    @Test
    public void withLongTail20AndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("--tail", "20", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).contains(20L);
        assertThat(options.tail()).contains(20L);
    }

    @Test
    public void withTail20Limit30Directory() throws ParseException
    {
        String[] argv = givenInputOptions("-t", "20", "-l", "30", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).contains(30L);
        assertThat(options.tail()).contains(20L);
    }

    @Test
    public void withTail20Limit10Directory() throws ParseException
    {
        String[] argv = givenInputOptions("-t", "20", "-l", "10", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isFalse();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).contains(10L);
        assertThat(options.tail()).contains(20L);
    }

    @Test
    public void withTail20FollowAndDirectory() throws ParseException
    {
        String[] argv = givenInputOptions("-t", "20", "-f", "./dir");

        ToolOptions options = parser.parse(argv);

        assertThat(options.path()).isEqualTo(Paths.get("./dir"));
        assertThat(options.follow()).isTrue();
        assertThat(options.rollCycle()).isEmpty();
        assertThat(options.limit()).isEmpty();
        assertThat(options.tail()).contains(20L);
    }

    @Test
    public void withTailUndefinedAndDirectory()
    {
        String[] argv = givenInputOptions("-t", "./dir");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("specify number of records");
    }

    @Test
    public void withTail20AndNoDirectory()
    {
        String[] argv = givenInputOptions("-t", "20");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("directory");
    }

    @Test
    public void withTailUndefinedAndNoDirectory()
    {
        String[] argv = givenInputOptions("-t");

        assertThatExceptionOfType(ParseException.class)
        .isThrownBy(() -> parser.parse(argv))
        .withMessageContaining("Missing argument");
    }

    @Test
    public void printHelp()
    {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut));

        try
        {
            parser.printUsage();
        }
        finally {
            System.setOut(originalOut);
        }

        assertThat(testOut.toString()).contains("eclog");
        assertThat(testOut.toString()).contains("follow");
        assertThat(testOut.toString()).contains("roll-cycle");
        assertThat(testOut.toString()).contains("help");
    }

    @Test
    public void withHelp() throws ParseException
    {
        String[] argv = givenInputOptions("-h");

        ToolOptions options = parser.parse(argv);

        assertThat(options.help()).isTrue();
    }

    private String[] givenInputOptions(String... options)
    {
        return options;
    }
}
