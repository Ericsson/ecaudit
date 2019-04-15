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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;

class FileQueueBootstrapper
{
    private static final Logger LOG = LoggerFactory.getLogger(FileQueueBootstrapper.class);

    private final Path path;
    private List<File> discoveredFiles;

    FileQueueBootstrapper(Path path)
    {
        this.path = path;
    }

    boolean isBootstrapping()
    {
        return !discoveredFiles.isEmpty();
    }

    void discoverFiles()
    {
        try
        {
            // Chronicle use filenames which allow sorting in chronological order
            // Example filename for MINUTELY rolling policy: 20190327-1231.cq4
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

    void excludeActiveFile(File file)
    {
        discoveredFiles.remove(file);
    }

    void enqueueOn(SizeTrackedFileQueue releasedFileQueue)
    {
        for (File existingFile : discoveredFiles)
        {
            releasedFileQueue.offer(existingFile);
        }
        discoveredFiles.clear();
    }
}
