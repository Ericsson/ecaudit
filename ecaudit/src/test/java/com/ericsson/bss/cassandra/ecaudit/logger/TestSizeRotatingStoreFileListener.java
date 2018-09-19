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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSizeRotatingStoreFileListener
{
    private File tempDir;
    private int fileCycle = 0;

    private SizeRotatingStoreFileListener storeFileListener;

    @Before
    public void before()
    {
        tempDir = com.google.common.io.Files.createTempDir();
        tempDir.deleteOnExit();
    }

    @Test
    public void testManySmallAreRotated() throws IOException
    {
        givenStoreFileListener();
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        firstFiles.forEach(file -> assertThat(file.exists()).isFalse());
        lastFiles.forEach(file -> assertThat(file.exists()).isTrue());
    }

    @Test
    public void testOneBigFileIsDeleted() throws IOException
    {
        givenStoreFileListener();
        List<File> files = givenRotatedFiles(50, 1);

        assertThat(files.get(0).exists()).isFalse();
    }

    @Test
    public void testLargeFilePushOutManySmall() throws IOException
    {
        givenStoreFileListener();
        List<File> smallFiles = givenRotatedFiles(10, 4);
        List<File> largeFiles = givenRotatedFiles(40, 1);

        smallFiles.forEach(file -> assertThat(file.exists()).isFalse());
        largeFiles.forEach(file -> assertThat(file.exists()).isTrue());
    }

    @Test
    public void testManySmallPushOutOneBig() throws IOException
    {
        givenStoreFileListener();
        List<File> largeFiles = givenRotatedFiles(40, 1);
        List<File> smallFiles = givenRotatedFiles(10, 4);

        largeFiles.forEach(file -> assertThat(file.exists()).isFalse());
        smallFiles.forEach(file -> assertThat(file.exists()).isTrue());
    }

    @Test
    public void testExistingFilesAreRotated() throws IOException
    {
        List<File> existingFiles = givenExistingFiles(10, 5);
        givenStoreFileListener();
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        existingFiles.forEach(file -> assertThat(file.exists()).isFalse());
        firstFiles.forEach(file -> assertThat(file.exists()).isFalse());
        lastFiles.forEach(file -> assertThat(file.exists()).isTrue());
    }

    @Test
    public void testExistingFilesAreRotatedWithReuse() throws IOException
    {
        List<File> existingFiles = givenExistingFiles(10, 5);
        givenStoreFileListener();
        fileCycle--;
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        existingFiles.forEach(file -> assertThat(file.exists()).isFalse());
        firstFiles.forEach(file -> assertThat(file.exists()).isFalse());
        lastFiles.forEach(file -> assertThat(file.exists()).isTrue());
    }

    @Test
    public void testSurviveMissingFile() throws IOException
    {
        givenStoreFileListener();
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lostFiles = givenRotatedFiles(10, 2);
        lostFiles.forEach(file -> file.delete());
        List<File> moreFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        firstFiles.forEach(file -> assertThat(file.exists()).isFalse());
        lostFiles.forEach(file -> assertThat(file.exists()).isFalse());
        moreFiles.forEach(file -> assertThat(file.exists()).isFalse());
        lastFiles.forEach(file -> assertThat(file.exists()).isTrue());
    }

    private void givenStoreFileListener()
    {
        storeFileListener = new SizeRotatingStoreFileListener(tempDir.toPath(), 49);
    }

    private List<File> givenRotatedFiles(int size, int count) throws IOException
    {
        List<File> files = givenExistingFiles(size, count);

        for (File file : files)
        {
            storeFileListener.onAcquired(1, file);
            storeFileListener.onReleased(1, file);
        }

        return files;
    }

    private List<File> givenExistingFiles(int size, int count) throws IOException
    {
        List<File> files = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            files.add(createFile(size));
        }

        return files;
    }

    private File createFile(int size) throws IOException
    {
        Path filePath = Paths.get(tempDir.getPath(), "chronicle_" + fileCycle++ + SingleChronicleQueue.SUFFIX);
        if (filePath.toFile().exists())
        {
            return filePath.toFile();
        }
        else
        {
            Files.write(filePath, new byte[size]);
            return filePath.toFile();
        }
    }
}
