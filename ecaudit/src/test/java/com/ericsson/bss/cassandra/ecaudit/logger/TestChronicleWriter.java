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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestChronicleWriter
{
    @Mock
    private ChronicleQueue mockChronicleQueue;

    @Mock
    private ExcerptAppender mockAppender;

    @Mock
    private WriteMarshallable marshallable;

    private ChronicleWriter writer;

    @Before
    public void before()
    {
        when(mockChronicleQueue.acquireAppender()).thenReturn(mockAppender);
        writer = new ChronicleWriter(mockChronicleQueue);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockChronicleQueue);
        verifyNoMoreInteractions(mockAppender);
    }

    @Test
    public void putOneAndClose() throws Exception
    {
        writer.put(marshallable);

        Thread.sleep(50);
        writer.close();

        verify(mockAppender).writeDocument(eq(marshallable));
        verify(mockChronicleQueue).close();
    }

    @Test
    public void closeAndPutOne() throws Exception
    {
        new Thread(writer::close).start();

        Thread.sleep(100);
        assertThatIllegalStateException()
        .isThrownBy(() -> writer.put(marshallable));

        verify(mockChronicleQueue).close();
    }

    @Test
    public void putOneAndInterruptOnClose() throws Exception
    {
        Thread testThread = Thread.currentThread();
        doAnswer(invocation -> {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException ignored)
            {
                testThread.interrupt();
                Thread.sleep(100);
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(mockAppender).writeDocument(any(WriteMarshallable.class));

        writer.put(marshallable);
        Thread.sleep(50);

        writer.close();

        verify(mockAppender).writeDocument(eq(marshallable));
        verify(mockChronicleQueue).close();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // Clear interrupt flag
        Thread.interrupted();
    }

    @Test
    public void closeQueueOnceOnly()
    {
        writer.close();
        writer.close();

        verify(mockChronicleQueue, times(1)).close();
    }
}
