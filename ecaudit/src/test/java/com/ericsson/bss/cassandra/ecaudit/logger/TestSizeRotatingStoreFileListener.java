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
        givenStoreFileListener(49);
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        firstFiles.forEach(file -> assertThat(file).doesNotExist());
        lastFiles.forEach(file -> assertThat(file).exists());
    }

    @Test
    public void testOneBigFileIsDeleted() throws IOException
    {
        givenStoreFileListener(49);
        List<File> files = givenRotatedFiles(50, 1);

        assertThat(files.get(0)).doesNotExist();
    }

    @Test
    public void testLargeFilePushOutManySmall() throws IOException
    {
        givenStoreFileListener(49);
        List<File> smallFiles = givenRotatedFiles(10, 4);
        List<File> largeFiles = givenRotatedFiles(40, 1);

        smallFiles.forEach(file -> assertThat(file).doesNotExist());
        largeFiles.forEach(file -> assertThat(file).exists());
    }

    @Test
    public void testManySmallPushOutOneBig() throws IOException
    {
        givenStoreFileListener(49);
        List<File> largeFiles = givenRotatedFiles(40, 1);
        List<File> smallFiles = givenRotatedFiles(10, 4);

        largeFiles.forEach(file -> assertThat(file).doesNotExist());
        smallFiles.forEach(file -> assertThat(file).exists());
    }

    @Test
    public void testExistingFilesAreRotated() throws IOException
    {
        List<File> existingFiles = givenExistingFiles(10, 5);
        givenStoreFileListener(39);
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 3);

        existingFiles.forEach(file -> assertThat(file).doesNotExist());
        firstFiles.forEach(file -> assertThat(file).doesNotExist());
        lastFiles.forEach(file -> assertThat(file).exists());
    }

    @Test
    public void testExistingFilesAreRotatedWithReuse() throws IOException
    {
        List<File> existingFiles = givenExistingFiles(10, 5);
        givenStoreFileListener(49);
        fileCycle--;
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        existingFiles.forEach(file -> assertThat(file).doesNotExist());
        firstFiles.forEach(file -> assertThat(file).doesNotExist());
        lastFiles.forEach(file -> assertThat(file).exists());
    }

    @Test
    public void testExistingFilesAreRotatedAndUnknownIsIgnored() throws IOException
    {
        List<File> existingFiles = givenExistingFiles(10, 5);
        Path fileToIgnorePath = Paths.get(tempDir.getPath(), "guck.txt");
        Files.write(fileToIgnorePath, new byte[10]);

        givenStoreFileListener(49);
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        existingFiles.forEach(file -> assertThat(file).doesNotExist());
        assertThat(fileToIgnorePath.toFile()).exists();
        firstFiles.forEach(file -> assertThat(file).doesNotExist());
        lastFiles.forEach(file -> assertThat(file).exists());
    }

    @Test
    public void testExistingFilesAreRotatedAndDirectoryIsIgnored() throws IOException
    {
        List<File> existingFiles = givenExistingFiles(10, 5);
        Path dirToIgnorePath = Paths.get(tempDir.getPath(), "guck" + SingleChronicleQueue.SUFFIX);
        Files.createDirectory(dirToIgnorePath);

        givenStoreFileListener(49);
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        existingFiles.forEach(file -> assertThat(file).doesNotExist());
        assertThat(dirToIgnorePath.toFile()).exists();
        firstFiles.forEach(file -> assertThat(file).doesNotExist());
        lastFiles.forEach(file -> assertThat(file).exists());
    }

    @Test
    public void testSurviveMissingFile() throws IOException
    {
        givenStoreFileListener(49);
        List<File> firstFiles = givenRotatedFiles(10, 10);
        List<File> lostFiles = givenRotatedFiles(10, 2);
        lostFiles.forEach(file -> assertThat(file.delete()).isTrue());
        List<File> moreFiles = givenRotatedFiles(10, 10);
        List<File> lastFiles = givenRotatedFiles(10, 4);

        firstFiles.forEach(file -> assertThat(file).doesNotExist());
        lostFiles.forEach(file -> assertThat(file).doesNotExist());
        moreFiles.forEach(file -> assertThat(file).doesNotExist());
        lastFiles.forEach(file -> assertThat(file).exists());
    }

    private void givenStoreFileListener(long maxLogSize)
    {
        storeFileListener = new SizeRotatingStoreFileListener(tempDir.toPath(), maxLogSize);
    }

    private List<File> givenRotatedFiles(int size, int count) throws IOException
    {
        List<File> files = new ArrayList<>(count);

        for (int i = 0; i < count; i++)
        {
            File file = createFile(size);
            storeFileListener.onAcquired(1, file);
            storeFileListener.onReleased(1, file);
            files.add(file);
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
