package com.ericsson.bss.cassandra.ecaudit.test.chronicle;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public class RecordValues
{
    private short version = 0;
    private String type = "ecaudit-single";
    private long timestamp = 42;
    private byte[] clientAddress;
    private int clientPort = 555;
    private byte[] coordinatorAddress;
    private String user = "john";
    private UUID batchId = null;
    private String status = "ATTEMPT";
    private String operation = "Some operation";

    private RecordValues() throws UnknownHostException
    {
        clientAddress = InetAddress.getByName("1.2.3.4").getAddress();
        coordinatorAddress = InetAddress.getByName("5.6.7.8").getAddress();
    }

    public static RecordValues defaultValues() throws UnknownHostException
    {
        return new RecordValues();
    }

    public RecordValues butWithVersion(short version)
    {
        this.version = version;
        return this;
    }

    public RecordValues butWithType(String type)
    {
        this.type = type;
        return this;
    }

    public RecordValues butWithClientAddress(byte[] clientAddress)
    {
        this.clientAddress = clientAddress;
        return this;
    }

    public RecordValues butWithCoordinatorAddress(byte[] coordinatorAddress)
    {
        this.coordinatorAddress = coordinatorAddress;
        return this;
    }

    public RecordValues butWithBatchId(UUID batchId)
    {
        this.batchId = batchId;
        return this;
    }

    public RecordValues butWithStatus(String status)
    {
        this.status = status;
        return this;
    }

    public short getVersion()
    {
        return version;
    }

    public String getType()
    {
        return type;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public byte[] getClientAddress()
    {
        return clientAddress;
    }

    public int getClientPort()
    {
        return clientPort;
    }

    public byte[] getCoordinatorAddress()
    {
        return coordinatorAddress;
    }

    public String gethUser()
    {
        return user;
    }

    public UUID getBatchId()
    {
        return batchId;
    }

    public String getStatus()
    {
        return status;
    }

    public String getOperation()
    {
        return operation;
    }
}
