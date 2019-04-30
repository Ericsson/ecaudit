# Description

Audit records are created locally on each Cassandra node covering all CQL requests.
ecAudit has a pluggable logger backend with support for SLF4J and Chronicle Queues out of the box.
The SLF4J logging framework makes it possible to store and format audit logs in various ways.
The binary Chronicle Queues offers the best possible performance.
It is possible to create a whitelist of usernames/resources which will not be included in the audit log.

ecAudit will create an audit record before the operation is performed, marked with status=ATTEMPT.
If the operation fails, for one reason or another, a corresponding failure record will be created, marked with status=FAILED.

Passwords which appear in an audit record will be obfuscated.


## Audit Records

| Field Label | Field Value                                                       | Mandatory Field |
| ----------- | ----------------------------------------------------------------- | --------------- |
| client      | Client IP address                                                 | yes             |
| user        | Username of the authenticated user                                | yes             |
| batchId     | Internal identifier shared by all statements in a batch operation | no              |
| status      | Value is either ATTEMPT or FAILED                                 | yes             |
| operation   | The CQL statement or a textual description of the operation       | yes             |
| timestamp   | The system timestamp of the request                               | yes             |
| coordinator | The coordinator address (host address)                            | yes             |


### Examples

In the examples below the audit record is prepended with a timestamp which is created by the SFL4J/Logback framework.

```
19:53:00.644 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'CREATE ROLE ecuser WITH PASSWORD = '*****' AND LOGIN = true'
19:53:00.653 - client:'127.0.0.1'|user:'cassandra'|status:'FAILED'|operation:'CREATE ROLE ecuser WITH PASSWORD = '*****' AND LOGIN = true'
```

```
19:53:07.889 - client:'127.0.0.1'|user:'ecuser'|status:'ATTEMPT'|operation:'Authentication attempt'
```

```
19:53:05.585 - client:'127.0.0.1'|user:'unknown'|status:'ATTEMPT'|operation:'Authentication attempt'
19:53:05.587 - client:'127.0.0.1'|user:'unknown'|status:'FAILED'|operation:'Authentication failed'
```

```
15:42:41.644 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[1, '1', 'valid']'
15:42:41.646 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'SELECT * FROM ecks.ectbl WHERE partk = ?[1]'
15:42:41.650 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'DELETE FROM ecks.ectbl WHERE partk = ?[1]'
15:42:41.651 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[2, '2', 'valid']'
15:42:41.653 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'SELECT * FROM ecks.ectbl WHERE partk = ?[2]'
15:42:41.655 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'DELETE FROM ecks.ectbl WHERE partk = ?[2]'
```

***Note*** - It is possible to customize the audit records by providing a parameterized log message format, see the [SLF4J Logger](slf4j_logger.md) guide for details.

## Audit Logs

Cassandra is a distributed system and so the generated audit logs will be created on different nodes.

Records are created at the coordinator layer at Cassandra
which means that each request typically will be audited on one single Cassandra node in the cluster.
However, as requests may be retried at the client side it is possible for a request to appear in an audit log at several Cassandra nodes.
In order to get a complete cluster wide view of all audited operations
it is necessary to gather and merge all individual audit logs into one.

When ecAudit is configured to write audit logs with the SLF4J/Logback framework
there are many ways to customize behavior in terms of synchronous vs. asynchronous writes, log file rotation etc.
For details, consult the Logback [documentation](https://logback.qos.ch/).
There is a useful example in the [SLF4J Logger](slf4j_logger.md) guide to get you started.

When ecAudit is configured to write audit logs into Chronicle Queues
it is possible to write large volumes of audit records with minimal performance impact.
This is achieved by minimizing memory allocations and storing records in an efficient binary format.
Logs are extracted with a tool provided with ecAudit.


## Audit Whitelists

Two different mechanisms are used to define what operations that are whitelisted.
Whitelists can be configured either centrally using custom options on Roles in Cassandra,
or locally in each separate node in a YAML file.
Whitelisted operations will not appear in the audit logs.
For details, consult the [whitelist guide](setup.md#audit-whitelists).


## Performance

There are two parts of ecAudit that adds an overhead to requests in Cassandra.

First, each request is checked towards the whitelist.
This adds an overhead of ~1%.
This check is performed on all requests when ecAudit is enabled.

Second, there is an overhead involved with the actual logging of the audit record.
This operation is performed on all requests that are not filtered by the whitelist.
In a busy system this may add as much as 20% overhead.

This cassandra-stress chart illustrates typical performance impact of ecAudit:

* [Throughput](https://rawgit.com/Ericsson/ecaudit/master/doc/ecaudit-performance.html)
* [Latency](https://rawgit.com/Ericsson/ecaudit/master/doc/ecaudit-performance.html?metric=mean)

Refer to the guides of Logback settings, authentication caches and whitelist settings to get best possible performance.


## Connections

A typical Cassandra client will connect to many (if not all) nodes in the Cassandra cluster.
For this reason audit records will be created on several places when a new client connect to a cluster.
Further, it is not uncommon for a client to set up several connections to a server which will result in several audit records for user authentication.

Some clients will also make a few internal queries to the internal system tables in Cassandra.
These queries will appear as any other audit record even though they were not generated by the actual application logic at the client.


## Known Limitations


### Interfaces

ecAudit does not provide auditing on the JMX interface.
ecAudit does not provide auditing functionality for prepared statements on the legacy RPC (Thrift) interface in Cassandra.
However, regular (un-prepared) statements on the RPC (Thrift) interface are audit logged.


### The USE statement

When a USE statement is used at the client side this will be visible in the audit log.
However, behavior is somewhat inconsistent with other statements as each USE statement typically will be logged twice.
The first log entry will appear at the time when the USE statement is sent.
The second log entry will appear just before the next statement in order is logged.
So order will be preserved, but timing on second USE log entry may get wrong timestamp.
This behavior is observed with the Java driver.
