//**********************************************************************
// Copyright 2018 Telefonaktiebolaget LM Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//**********************************************************************
package com.ericsson.bss.cassandra.ecaudit.integration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.service.CassandraDaemon;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.ericsson.bss.cassandra.ecaudit.AuditAdapterFactory;
import com.ericsson.bss.cassandra.ecaudit.filter.yaml.AuditYamlConfigurationLoader;
import com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler;

/**
 * Singleton for creating a Cassandra Daemon for Test.
 */
public class CassandraDaemonForAuditTest // NOSONAR
{
    private static final Logger LOG = LoggerFactory.getLogger(CassandraDaemonForAuditTest.class);

    private static CassandraDaemonForAuditTest cdtSingleton;

    private CassandraDaemon cassandraDaemon;

    private File tempDir;

    private volatile int rpcPort = -1;
    private volatile int storagePort = -1;
    private volatile int sslStoragePort = -1;
    private volatile int nativePort = -1;
    private volatile int jmxPort = -1;

    public static CassandraDaemonForAuditTest getInstance() throws IOException
    {
        synchronized (CassandraDaemonForAuditTest.class)
        {
            if (cdtSingleton == null)
            {
                cdtSingleton = new CassandraDaemonForAuditTest();
            }

            cdtSingleton.activate();
        }

        return cdtSingleton;
    }

    private CassandraDaemonForAuditTest() throws IOException
    {
        synchronized (CassandraDaemonForAuditTest.class)
        {
            if (cassandraDaemon == null)
            {
                setupConfiguration();
                cassandraDaemon = new CassandraDaemon(true);
            }
        }
    }

    /**
     * Setup the Cassandra configuration for this instance.
     */
    private void setupConfiguration() throws IOException
    {
        randomizePorts();

        tempDir = com.google.common.io.Files.createTempDir();
        tempDir.deleteOnExit();

        InputStream inStream = CassandraDaemonForAuditTest.class.getClassLoader().getResourceAsStream("cassandra.yaml");
        String content = readStream(inStream);
        Path outPath = Paths.get(tempDir.getPath() + "/cassandra.yaml");
        content = content.replaceAll("###tmp###", tempDir.getPath().replace("\\", "\\\\"));
        content = content.replaceAll("###rpc_port###", String.valueOf(rpcPort));
        content = content.replaceAll("###storage_port###", String.valueOf(storagePort));
        content = content.replaceAll("###ssl_storage_port###", String.valueOf(sslStoragePort));
        content = content.replaceAll("###native_transport_port###", String.valueOf(nativePort));
        java.nio.file.Files.write(outPath, content.getBytes(StandardCharsets.UTF_8));

        System.setProperty("cassandra.config", outPath.toUri().toURL().toExternalForm());

        System.setProperty("cassandra.jmx.local.port", String.valueOf(jmxPort));

        String rackdcTempPath = moveResourceFileToTempDir("cassandra-rackdc.properties");
        System.setProperty("cassandra-rackdc.properties", Paths.get(rackdcTempPath).toUri().toURL().toExternalForm());

        System.setProperty("cassandra-foreground", "true");
        System.setProperty("cassandra.superuser_setup_delay_ms", "1");

        System.setProperty("cassandra.custom_query_handler_class", AuditQueryHandler.class.getCanonicalName());
        System.setProperty(AuditAdapterFactory.FILTER_TYPE_PROPERTY_NAME, AuditAdapterFactory.FILTER_TYPE_YAML_AND_ROLE);

        String auditYamlTempPath = moveResourceFileToTempDir("integration_audit.yaml");
        System.setProperty(AuditYamlConfigurationLoader.PROPERTY_CONFIG_FILE, auditYamlTempPath);

        LOG.info("Using temporary cassandra directory: " + tempDir);
    }

    private void activate()
    {
        if (!cassandraDaemon.setupCompleted() && !cassandraDaemon.isNativeTransportRunning())
        {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deactivate()));
            cassandraDaemon.activate();
        }
        else if (!cassandraDaemon.isNativeTransportRunning())
        {
            cassandraDaemon.start();
        }

        // Cassandra create default super user in a setup task with a small delay
        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private void deactivate()
    {
        try
        {
            cassandraDaemon.deactivate();

            Thread.sleep(1000);

            if (tempDir != null && tempDir.exists())
            {
                try
                {
                    FileUtils.deleteDirectory(tempDir);
                }
                catch (IOException e)
                {
                    LOG.error("Failed to delete temp files", e);
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    public Cluster createCluster()
    {
        return createCluster("cassandra", "cassandra");
    }

    public Cluster createCluster(String username, String password)
    {
        return Cluster.builder().addContactPoint(DatabaseDescriptor.getListenAddress().getHostAddress())
                .withPort(nativePort).withCredentials(username, password).build();
    }

    /**
     * Get the random created Cassandra port.
     *
     * @return the port
     */
    public int getPort()
    {
        return nativePort;
    }

    /**
     * Get the random created JMX server port.
     *
     * @return the port
     */
    public int getJMXPort()
    {
        return jmxPort;
    }

    public File getDataDirectory()
    {
        return new File(getCassandraDirectory(), "data");
    }

    public File getCassandraDirectory()
    {
        return new File(tempDir.getAbsoluteFile(), "cassandra");
    }

    private void randomizePorts()
    {
        rpcPort = randomAvailablePort();
        storagePort = randomAvailablePort();
        sslStoragePort = randomAvailablePort();
        nativePort = randomAvailablePort();
        jmxPort = randomAvailablePort();
    }

    private int randomAvailablePort()
    {
        int port = -1;
        while (port < 0)
        {
            port = (new Random().nextInt(16300) + 49200);
            if (rpcPort == port
                    || storagePort == port
                    || sslStoragePort == port
                    || nativePort == port
                    || jmxPort == port)
            {
                port = -1;
            }
            else
            {
                try (ServerSocket socket = new ServerSocket(port))
                {
                    break;
                }
                catch (IOException e)
                {
                    port = -1;
                }
            }
        }
        return port;
    }

    private String moveResourceFileToTempDir(String filename) throws IOException
    {
        InputStream inStream = CassandraDaemonForAuditTest.class.getClassLoader().getResourceAsStream(filename);
        String content = readStream(inStream);

        Path outPath = Paths.get(tempDir.getPath() + "/" + filename);
        Files.write(outPath, content.getBytes(StandardCharsets.UTF_8));

        String tempPath = outPath.toString();
        LOG.debug("Created temporary resource at: " + tempPath);
        return tempPath;
    }

    private static String readStream(InputStream inputStream) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = inputStream.read(buffer)) != -1)
        {
            out.write(buffer, 0, length);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
