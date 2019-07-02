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
package com.ericsson.bss.cassandra.ecaudit;

import com.ericsson.bss.cassandra.ecaudit.common.record.Status;

/**
 * The log timing strategy says in which phase of the request logging should be performed.
 * <ul>
 * <li>{@link LogTimingStrategy#PRE_LOGGING_STRATEGY} - logs an ATTEMPT before the request is processed, <i>and</i>
 * a FAILURE if the request fails.
 * <li>{@link LogTimingStrategy#POST_LOGGING_STRATEGY} - always log one message (SUCCESS or FAILED) after the request
 * has been processed.
 * </ul>
 */
public interface LogTimingStrategy
{
    /**
     * @param status the log operation status
     * @return {@code true} if the provided status should be logged, {@code false} otherwise.
     */
    boolean shouldLogForStatus(Status status);

    /**
     * @return {@code true} if a summary should be logged for a failed batch, {@code false} otherwise.
     */
    boolean shouldLogFailedBatchSummary();

    LogTimingStrategy PRE_LOGGING_STRATEGY = new LogTimingStrategy()
    {
        @Override
        public boolean shouldLogForStatus(Status status)
        {
            return status == Status.ATTEMPT || status == Status.FAILED;
        }

        @Override
        public boolean shouldLogFailedBatchSummary()
        {
            return true;
        }
    };

    LogTimingStrategy POST_LOGGING_STRATEGY = new LogTimingStrategy()
    {
        @Override
        public boolean shouldLogForStatus(Status status)
        {
            return status == Status.SUCCEEDED || status == Status.FAILED;
        }

        @Override
        public boolean shouldLogFailedBatchSummary()
        {
            return false;
        }
    };
}
