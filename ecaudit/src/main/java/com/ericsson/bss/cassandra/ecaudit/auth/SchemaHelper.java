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
import org.apache.cassandra.gms.VersionedValue;
import org.apache.cassandra.utils.FBUtilities;

class SchemaHelper
{
    private static final Logger LOG = LoggerFactory.getLogger(SchemaHelper.class);

    private final InetAddress localAddress;
    private final Gossiper gossiper;

    SchemaHelper()
    {
        this(FBUtilities.getBroadcastAddress(), Gossiper.instance);
    }

    @VisibleForTesting
    SchemaHelper(InetAddress localAddress, Gossiper gossiper)
    {
        this.localAddress = localAddress;
        this.gossiper = gossiper;
    }

    void waitForSchemaAlignment(int maxTimeToWaitInSeconds)
    {
        LOG.info("Waiting for schema to align in cluster");
        int timeToWaitInSeconds = maxTimeToWaitInSeconds;

        while(areLiveNodesWaitingOnSchemaUpdate() && timeToWaitInSeconds-- > 0)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean areLiveNodesWaitingOnSchemaUpdate()
    {
        EndpointState localEndpointState = gossiper.getEndpointStateForEndpoint(localAddress);
        String localSchemaId = localEndpointState.getApplicationState(ApplicationState.SCHEMA).value;

        for (InetAddress memberAddress : gossiper.getLiveMembers())
        {
            if (isRemoteEndpoint(memberAddress))
            {
                EndpointState memberEndpointState = gossiper.getEndpointStateForEndpoint(memberAddress);
                VersionedValue memberVersionedSchema = memberEndpointState.getApplicationState(ApplicationState.SCHEMA);
                if (isDifferentSchema(localSchemaId, memberVersionedSchema)) {
                    LOG.debug("Endpoint {} is not aligned yet", memberAddress);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isRemoteEndpoint(InetAddress memberAddress)
    {
        return !memberAddress.equals(localAddress);
    }

    private boolean isDifferentSchema(String localSchema, VersionedValue versionedValue)
    {
        return versionedValue != null && !localSchema.equals(versionedValue.value);
    }
}
