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
        public boolean shouldLogForStatus(Status status)
        {
            return status == Status.ATTEMPT || status == Status.FAILED;
        }

        public boolean shouldLogFailedBatchSummary()
        {
            return true;
        }
    };

    LogTimingStrategy POST_LOGGING_STRATEGY = new LogTimingStrategy()
    {
        public boolean shouldLogForStatus(Status status)
        {
            return status == Status.SUCCEEDED || status == Status.FAILED;
        }

        public boolean shouldLogFailedBatchSummary()
        {
            return false;
        }
    };
}
