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
import java.util.Optional;

import net.openhft.chronicle.queue.RollCycles;

public class ToolOptions
{
    private final Path path;
    private final Long limit;
    private final Long tail;
    private final boolean follow;
    private final RollCycles rollCycle;
    private final boolean help;

    private ToolOptions(Builder builder)
    {
        this.path = builder.path;
        this.limit = builder.limit;
        this.tail = builder.tail;
        this.follow = builder.follow;
        this.rollCycle = builder.rollCycle;
        this.help = builder.help;
    }

    public Path path()
    {
        return path;
    }

    public Optional<Long> limit()
    {
        return Optional.ofNullable(limit);
    }

    public Optional<Long> tail()
    {
        return Optional.ofNullable(tail);
    }

    public boolean follow()
    {
        return follow;
    }

    public Optional<RollCycles> rollCycle()
    {
        return Optional.ofNullable(rollCycle);
    }

    public boolean help()
    {
        return help;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private Path path;
        private Long limit;
        private Long tail;
        private boolean follow = false;
        private RollCycles rollCycle;
        private boolean help = false;

        public Builder withPath(Path path)
        {
            this.path = path;
            return this;
        }

        public Builder withLimit(long limit)
        {
            this.limit = limit;
            return this;
        }

        public Builder withTail(long tail)
        {
            this.tail = tail;
            return this;
        }

        public Builder withFollow(boolean follow)
        {
            this.follow = follow;
            return this;
        }

        public Builder withRollCycle(RollCycles rollCycle)
        {
            this.rollCycle = rollCycle;
            return this;
        }

        public Builder withHelp(boolean help)
        {
            this.help = help;
            return this;
        }

        public ToolOptions build()
        {
            return new ToolOptions(this);
        }
    }
}
