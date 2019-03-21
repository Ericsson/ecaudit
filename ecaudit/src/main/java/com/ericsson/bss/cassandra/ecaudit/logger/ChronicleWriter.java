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
package com.ericsson.bss.cassandra.ecaudit.logger;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueBuilder;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.apache.cassandra.concurrent.NamedThreadFactory;

class ChronicleWriter implements AutoCloseable
{
    private static final int BUFFER_SIZE = 256;

    private final Thread writerThread = new NamedThreadFactory("Chronicle Writer").newThread(this::writerLoop);
    private final BlockingQueue<WriteMarshallable> queue;
    private final ChronicleQueue chronicle;
    private final ExcerptAppender appender;

    private volatile boolean active = true;

    ChronicleWriter(ChronicleAuditLoggerConfig config)
    {
        this(ChronicleQueueBuilder::single, config);
    }

    @VisibleForTesting
    ChronicleWriter(Function<File, SingleChronicleQueueBuilder> builderFunction, ChronicleAuditLoggerConfig config)
    {
        chronicle = builderFunction.apply(config.getLogPath().toFile())
                                   .rollCycle(config.getRollCycle())
                                   .storeFileListener(new SizeRotatingStoreFileListener(config.getLogPath(), config.getMaxLogSize()))
                                   .build();

        appender = chronicle.acquireAppender();
        queue = new ArrayBlockingQueue<>(BUFFER_SIZE);
        writerThread.start();
    }

    @VisibleForTesting
    ChronicleWriter(ChronicleQueue chronicle)
    {
        this.chronicle = chronicle;
        appender = chronicle.acquireAppender();
        queue = new ArrayBlockingQueue<>(BUFFER_SIZE);
        writerThread.start();
    }

    void put(WriteMarshallable marshallable) throws InterruptedException
    {
        if (!active)
        {
            throw new IllegalStateException("Chronicle audit writer has been deactivated");
        }

        queue.put(marshallable);
    }

    private void writerLoop()
    {
        try
        {
            while (active)
            {
                WriteMarshallable marshallable = queue.take();
                appender.writeDocument(marshallable);
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close()
    {
        if (!active)
        {
            return;
        }

        active = false;
        try
        {
            writerThread.interrupt();
            writerThread.join(500);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        chronicle.close();
    }
}
