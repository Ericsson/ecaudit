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
package com.ericsson.bss.cassandra.ecaudit.auth;

import java.net.InetAddress;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.utils.FBUtilities;

class SchemaHelper
{
    private static final Logger LOG = LoggerFactory.getLogger(SchemaHelper.class);

    private static final long RETRY_INTERVAL_MS = 1000;

    private final InetAddress localAddress;
    private final Gossiper gossiper;
    private final long retryIntervalMillis;

    SchemaHelper()
    {
        this(FBUtilities.getBroadcastAddress(), Gossiper.instance, RETRY_INTERVAL_MS);
    }

    @VisibleForTesting
    SchemaHelper(InetAddress localAddress, Gossiper gossiper, long retryIntervalMillis)
    {
        this.localAddress = localAddress;
        this.gossiper = gossiper;
        this.retryIntervalMillis = retryIntervalMillis;
    }

    boolean areSchemasAligned(long schemaAlignmentDelayMillis)
    {
        LOG.info("Waiting for schema to align in cluster");

        int delayMillis = 0;
        while (areLiveNodesWaitingOnSchemaUpdate())
        {
            if (delayMillis >= schemaAlignmentDelayMillis)
            {
                return false;
            }
            delayMillis += retryIntervalMillis;

            try
            {
                Thread.sleep(retryIntervalMillis);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return true;
    }

    private boolean areLiveNodesWaitingOnSchemaUpdate()
    {
        String localSchemaId = tryGetSchemaId(localAddress);
        if (localSchemaId == null)
        {
            return true;
        }

        for (InetAddress memberAddress : gossiper.getLiveMembers())
        {
            if (isRemoteEndpoint(memberAddress) && isDifferentSchema(localSchemaId, memberAddress))
            {
                LOG.debug("Waiting for schema alignment at endpoint {}", memberAddress);
                return true;
            }
        }

        return false;
    }

    private String tryGetSchemaId(InetAddress address)
    {
        EndpointState endpointState = gossiper.getEndpointStateForEndpoint(address);
        if (endpointState != null)
        {
            return endpointState.getApplicationState(ApplicationState.SCHEMA).value;
        }
        else
        {
            return null;
        }
    }

    private boolean isRemoteEndpoint(InetAddress memberAddress)
    {
        return !memberAddress.equals(localAddress);
    }

    private boolean isDifferentSchema(String localSchemaId, InetAddress memberAddress)
    {
        String remoteSchemaId = tryGetSchemaId(memberAddress);
        return !localSchemaId.equals(remoteSchemaId);
    }
}
