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
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.openhft.chronicle.queue.impl.StoreFileListener;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;

public class SizeRotatingStoreFileListener implements StoreFileListener
{
    private static final Logger LOG = LoggerFactory.getLogger(SizeRotatingStoreFileListener.class);

    private final Path path;
    private final long maxLogSize;
    private final Queue<File> releasedStoreFiles = new ConcurrentLinkedQueue<>();
    private long bytesInStoreFiles;
    private List<File> discoveredFiles;

    SizeRotatingStoreFileListener(Path path, long maxLogSize)
    {
        LOG.debug("Rotating Chronicle audit logs at threshold {} bytes", maxLogSize);
        this.path = path;
        this.maxLogSize = maxLogSize;
        discoverFiles();
    }

    private void discoverFiles()
    {
        try
        {
            discoveredFiles = Files.list(path)
                                   .filter(Files::isRegularFile)
                                   .map(Path::toFile)
                                   .filter(file -> file.getPath().endsWith(SingleChronicleQueue.SUFFIX))
                                   .sorted()
                                   .collect(Collectors.toList());
        }
        catch (IOException e)
        {
            LOG.warn("Failed to list existing Chronicle files");
            discoveredFiles = Collections.emptyList();
        }

        for (File discoveredFile : discoveredFiles)
        {
            LOG.debug("Discovered {}", discoveredFile.getPath());
        }
    }

    @Override
    public synchronized void onAcquired(int cycle, File file)
    {
        LOG.debug("Chronicle acquired [{}] {} at {} bytes", cycle, file.getPath(), file.length());

        discoveredFiles.remove(file);

        int index = 0;
        for (File existingFile : discoveredFiles)
        {
            onReleased(index++, existingFile);
        }
        discoveredFiles.clear();
    }

    @Override
    public synchronized void onReleased(int cycle, File file)
    {
        releasedStoreFiles.offer(file);
        // Not accurate because the files are sparse, but it's at least pessimistic
        bytesInStoreFiles += file.length();
        LOG.debug("Chronicle released {} at {} bytes", file.getPath(), file.length());

        LOG.debug("Released Chronicle archive at {} / {} bytes", bytesInStoreFiles, maxLogSize);
        while (bytesInStoreFiles > maxLogSize)
        {
            deleteOldestFile();
        }
    }

    private void deleteOldestFile()
    {
        if (releasedStoreFiles.isEmpty())
        {
            return;
        }
        File toDelete = releasedStoreFiles.poll();
        long toDeleteLength = toDelete.length();
        if (toDelete.delete())
        {
            LOG.debug("Deleted Chronicle file {} at {} bytes", toDelete.getPath(), toDeleteLength);
            bytesInStoreFiles -= toDeleteLength;
        }
        else
        {
            LOG.error("Failed to delete Chronicle file {} at {} bytes", toDelete.getPath(), toDeleteLength);
            bytesInStoreFiles = 0;
            discoverFiles();
        }
    }
}
