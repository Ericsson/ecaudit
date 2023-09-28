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
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.openhft.chronicle.queue.impl.StoreFileListener;

class SizeRotatingStoreFileListener implements StoreFileListener
{
    private static final Logger LOG = LoggerFactory.getLogger(SizeRotatingStoreFileListener.class);

    private final SizeTrackedFileQueue releasedFileQueue = new SizeTrackedFileQueue();
    private final FileQueueBootstrapper bootstrapper;
    private final long maxLogSize;

    SizeRotatingStoreFileListener(Path path, long maxLogSize)
    {
        LOG.debug("Rotating Chronicle audit logs at threshold {} bytes", maxLogSize);
        bootstrapper = new FileQueueBootstrapper(path);
        this.maxLogSize = maxLogSize;
        reset();
    }

    private void reset()
    {
        releasedFileQueue.clear();
        bootstrapper.discoverFiles();
    }

    @Override
    public synchronized void onAcquired(int cycle, File file)
    {
        if(LOG.isDebugEnabled())
        {
            LOG.debug("Chronicle acquired [{}] {} at {} bytes", cycle, file.getPath(), file.length());
        }

        if (bootstrapper.isBootstrapping())
        {
            bootstrapper.excludeActiveFile(file);
            bootstrapper.enqueueOn(releasedFileQueue);
            // We may be above threshold at this point
            // But we'll reclaim disk space on next call to onReleased()
        }
    }

    @Override
    public void onReleased(int cycle, File file)
    {
        if(LOG.isDebugEnabled())
        {
            LOG.debug("Chronicle released [{}] {} at {} bytes", cycle, file.getPath(), file.length());
        }

        releasedFileQueue.offer(file);
        maybeRotate();
    }

    private void maybeRotate()
    {
        while (releasedFileQueue.accumulatedFileSize() > maxLogSize)
        {
            if (!tryDeleteOldestFile())
            {
                reset();
                return;
            }
        }
    }

    private boolean tryDeleteOldestFile()
    {
        File toDelete = releasedFileQueue.poll();
        if (toDelete == null)
        {
            LOG.error("Above audit file threshold but no Chronicle file to delete");
            return false;
        }

        if(LOG.isDebugEnabled())
        {
            LOG.debug("Deleting Chronicle file {} at {} bytes", toDelete.getPath(), toDelete.getPath().length());
        }
        if (!toDelete.delete())
        {
            if(LOG.isErrorEnabled())
            {
                LOG.error("Failed to delete Chronicle file {}", toDelete.getPath());
            }
            return false;
        }

        return true;
    }
}
