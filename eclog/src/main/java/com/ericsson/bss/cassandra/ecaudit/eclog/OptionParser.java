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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import net.openhft.chronicle.queue.RollCycles;

class OptionParser
{
    private static final String TOOL_NAME = "eclog";

    private static final String LIMIT_OPTION_SHORT = "l";
    private static final String LIMIT_OPTION = "limit";
    private static final String TAIL_OPTION_SHORT = "t";
    private static final String TAIL_OPTION = "tail";
    private static final String FOLLOW_OPTION_SHORT = "f";
    private static final String FOLLOW_OPTION = "follow";
    private static final String ROLL_CYCLE_OPTION_SHORT = "r";
    private static final String ROLL_CYCLE_OPTION = "roll-cycle";
    private static final String HELP_OPTION_SHORT = "h";
    private static final String HELP_OPTION = "help";

    ToolOptions parse(String[] argv) throws ParseException
    {
        CommandLine cmd = new DefaultParser().parse(getCmdLineOptions(), argv, false);

        ToolOptions.Builder optionsBuilder = ToolOptions.builder();

        if (cmd.hasOption(HELP_OPTION))
        {
            return optionsBuilder
                   .withHelp(true)
                   .build();
        }

        parseFollowOption(cmd).ifPresent(optionsBuilder::withFollow);
        parseLongOption(cmd, LIMIT_OPTION).ifPresent(optionsBuilder::withLimit);
        parseLongOption(cmd, TAIL_OPTION).ifPresent(optionsBuilder::withTail);
        parseRollCycleOption(cmd).ifPresent(optionsBuilder::withRollCycle);
        optionsBuilder.withPath(parsePath(cmd));

        if (noFollowOrExplicitLimit(cmd))
        {
            // The tail is a moving target in a live queue
            // This will make sure we do not get more records than specified
            parseLongOption(cmd, TAIL_OPTION).ifPresent(optionsBuilder::withLimit);
        }

        return optionsBuilder.build();
    }

    private Optional<Boolean> parseFollowOption(CommandLine cmd)
    {
        return Optional.of(cmd.hasOption(FOLLOW_OPTION));
    }

    private Optional<Long> parseLongOption(CommandLine cmd, String option) throws ParseException
    {
        if (cmd.hasOption(option))
        {
            try
            {
                return Optional.of(Math.abs(Long.valueOf(cmd.getOptionValue(option))));
            }
            catch (NumberFormatException e)
            {
                throw new ParseException("Option '" + option + "' is used with an invalid value '" + cmd.getOptionValue(option) + "' - specify a number");
            }
        }
        else
        {
            return Optional.empty();
        }
    }

    private Optional<RollCycles> parseRollCycleOption(CommandLine cmd) throws ParseException
    {
        if (cmd.hasOption(ROLL_CYCLE_OPTION))
        {
            try
            {
                return Optional.of(RollCycles.valueOf(cmd.getOptionValue(ROLL_CYCLE_OPTION)));
            }
            catch (IllegalArgumentException e)
            {
                throw new ParseException("Unrecognized roll cycle '" + cmd.getOptionValue(ROLL_CYCLE_OPTION) + "' - valid options are " + Arrays.asList(RollCycles.values()));
            }
        }
        else
        {
            return Optional.empty();
        }
    }

    private Path parsePath(CommandLine cmd) throws ParseException
    {
        String[] args = cmd.getArgs();
        if (args.length != 1)
        {
            throw new ParseException("Audit log directory must be specified");
        }
        return Paths.get(args[0]);
    }

    private boolean noFollowOrExplicitLimit(CommandLine cmd)
    {
        return !cmd.hasOption(FOLLOW_OPTION) && !cmd.hasOption(LIMIT_OPTION);
    }

    void printUsage()
    {
        Options options = getCmdLineOptions();

        String usage = TOOL_NAME + " [options] <log-directory>";
        String header = "Print ecAudit Chronicle logs to standard output.";
        new HelpFormatter().printHelp(usage, header, options, "");
    }

    private Options getCmdLineOptions()
    {
        Options options = new Options();

        options.addOption(new Option(LIMIT_OPTION_SHORT, LIMIT_OPTION, true, "Exit after printing <arg> records"));
        options.addOption(new Option(TAIL_OPTION_SHORT, TAIL_OPTION, true, "Skip to the <arg> last records and print them"));
        options.addOption(new Option(FOLLOW_OPTION_SHORT, FOLLOW_OPTION, false, "Upon reaching the end of the log continue indefinitely waiting for more records"));
        options.addOption(new Option(ROLL_CYCLE_OPTION_SHORT, ROLL_CYCLE_OPTION, true, "How often the log file was rolled. May be necessary for Chronicle to correctly parse file names. (MINUTELY, HOURLY, DAILY)."));
        options.addOption(new Option(HELP_OPTION_SHORT, HELP_OPTION, false, "Display this help message"));

        return options;
    }
}
