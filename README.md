# ecAudit - Ericsson Cassandra Audit

[![build](https://travis-ci.org/Ericsson/ecaudit.svg?branch=release/c3.0)](https://travis-ci.org/Ericsson/ecaudit)
[![coverage](https://coveralls.io/repos/github/Ericsson/ecaudit/badge.svg?branch=release/c3.0)](https://coveralls.io/github/Ericsson/ecaudit?branch=release/c3.0)
[![maven central](https://img.shields.io/maven-central/v/com.ericsson.bss.cassandra.ecaudit/ecaudit_c3.0.svg?label=maven%20central)](https://search.maven.org/search?q=g:%22com.ericsson.bss.cassandra.ecaudit%22%20AND%20a:%22ecaudit_c3.0%22)

The ecAudit plug-in provides an audit logging feature for Cassandra to audit CQL statement execution and login attempt through native CQL protocol.

If you are reading this on github.com, please be aware of that this is the documentation for the Cassandra 3.0 flavor of ecAudit.
To get documentation for a specific flavor and version, refer to the corresponding tag.
For example, you can read about ecAudit 0.21.0 for Cassandra 3.0 by viewing the [ecaudit_c3.0-0.21.0](https://github.com/Ericsson/ecaudit/tree/ecaudit_c3.0-0.21.0) tag.


## Basic Functionality

Audit records are created locally on each Cassandra node covering all CQL requests.
ecAudit is using the SLF4J logging framework which makes it possible to store and format audit logs in various ways.
It is possible to create a whitelist of usernames/resources which will not be included in the audit log.

ecAudit will create an audit record before the operation is performed, marked with status=ATTEMPT.
If the operation fails, for one reason or another, a corresponding failure record will be created, marked with status=FAILED.

Passwords which appear in an audit record will be obfuscated.


## Compatibility

There are different flavors of the plug-in for different versions of Cassandra.
The Cassandra version that was used during build and integration tests can be derived from the full name of the ecAudit plugin.
For instance, ecaudit_c3.11 indicate that the ecAudit flavor was built with Cassandra 3.11.x.

The table below list the Cassandra versions used in the current and previous builds of ecAudit.

| Flavor          | Versions       | Compiled With    |
| ----------------| -------------- | ---------------- |
| ecaudit_c3.0    | 0.1  -> 0.10   | Cassandra 3.0.15 |
| ecaudit_c3.0    | 0.11 -> 0.21   | Cassandra 3.0.16 |
| ecaudit_c3.0    | 0.22 ->        | Cassandra 3.0.17 |
| ecaudit_c3.11   | 0.1  ->        | Cassandra 3.11.2 |

The ecAudit plug-in is maintained for selected Cassandra versions only.
It may be possible to use the ecAudit plug-in with related Cassandra versions as well.
But we recommend users to deploy ecAudit with the Cassandra version that was used during build.
New version flavors can be created on request.

The ecAudit versions between flavors are feature compatible as far as it makes sense.
For instance ecAudit_c3.0 version 0.4.0 and ecAudit_c3.11 0.4.0 have the same plug-in features.

As of version 0.21.0, ecAudit is available on Maven Central.
Earlier versions are not published on any public repository.


### Cassandra 4.0

As of [CASSANDRA-12151](https://issues.apache.org/jira/browse/CASSANDRA-12151) native support for audit logs are available in 4.0 Cassandra and later.

Since ecAudit was developed before CASSANDRA-12151, there are several differences to be aware of. The most notable being:

* ecAudit depends primarily on SLF4J/LOGBack.
  CASSANDRA-12151 uses a Chronicle Queue as its primary backend, but SLF4J is also supported.

* The format of the audit entries are different in ecAudit compared to CASSANDRA-12151.

* CASSANDRA-12151 uses settings in the ```cassandra.yaml``` to configure basic whitelists.
  ecAudit uses role options in CQL and/or settings in the audit.yaml file to manage whitelists.
  [CASSANDRA-14471](https://issues.apache.org/jira/browse/CASSANDRA-14471) is attempting to close this gap.


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


## Audit Records

| Field Label | Field Value                                                       | Mandatory Field |
| ----------- | ----------------------------------------------------------------- | --------------- |
| client      | Client IP address                                                 | yes             |
| user        | Username of the authenticated user                                | yes             |
| batchId     | Internal identifier shared by all statements in a batch operation | no              |
| status      | Value is either ATTEMPT or FAILED                                 | yes             |
| operation   | The CQL statement or a textual description of the operation       | yes             |


### Examples

In the examples below the audit record is prepended with a timestamp which is created by the SFL4J/LOGBack framework.

```
9:53:00.644 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'CREATE ROLE ecuser WITH PASSWORD = '*****' AND LOGIN = true'
19:53:00.653 - client:'127.0.0.1'|user:'cassandra'|status:'FAILED'|operation:'CREATE ROLE ecuser WITH PASSWORD = '*****' AND LOGIN = true'

19:53:07.889 - client:'127.0.0.1'|user:'ecuser'|status:'ATTEMPT'|operation:'Authentication attempt'

19:53:05.585 - client:'127.0.0.1'|user:'unknown'|status:'ATTEMPT'|operation:'Authentication attempt'
19:53:05.587 - client:'127.0.0.1'|user:'unknown'|status:'FAILED'|operation:'Authentication failed'

15:42:41.644 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[1, '1', 'valid']'
15:42:41.646 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'SELECT * FROM ecks.ectbl WHERE partk = ?[1]'
15:42:41.650 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'DELETE FROM ecks.ectbl WHERE partk = ?[1]'
15:42:41.651 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[2, '2', 'valid']'
15:42:41.653 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'SELECT * FROM ecks.ectbl WHERE partk = ?[2]'
15:42:41.655 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'DELETE FROM ecks.ectbl WHERE partk = ?[2]'
```

## Audit Logs

Cassandra is a distributed system and so the generated audit logs will be created on different nodes.

Records are created at the coordinator layer at Cassandra
which means that each request typically will be audited on one single Cassandra node in the cluster.
However, as requests may be retried at the client side it is possible for a request to appear in an audit log at several Cassandra nodes.
In order to get a complete cluster wide view of all audited operations
it is necessary to gather and merge all individual audit logs into one.

As the audit logs are generated with the SLF4J/LOGBack framework,
there are many ways to customize this when it comes to synchronous or asynchronous writes, log file rotation etc.
For details, consult the LOGBack [documentation](https://logback.qos.ch/).
There is a useful example in the ecAudit [setup guide](doc/setup.md) to get you started.


## Audit Whitelists

Two different mechanisms are used to define what operations that are whitelisted.
Whitelists can be configured either centrally using custom options on Roles in Cassandra,
or locally in each separate node in a YAML file.
Whitelisted operations will not appear in the audit logs.
For details, consult the [setup](doc/setup.md) and [whitelist](doc/role_whitelist_management.md) pages.


## Performance

There are two parts of ecAudit that adds an overhead to requests in Cassandra.

First, each request is checked towards the whitelist.
This adds an overhead of ~1%.
This check is performed on all requests when ecAudit is enabled.

Second, there is an overhead involved with the actual logging of the audit record.
This operation is performed on all requests that are not filtered by the whitelist.
In a busy system this may add as much as 20% overhead.

This cassandra-stress chart illustrates typical performance impact of ecAudit:

 * [Throughput](https://rawgit.com/Ericsson/ecaudit/release/c3.0/doc/ecaudit-performance.html)
 * [Latency](https://rawgit.com/Ericsson/ecaudit/release/c3.0/doc/ecaudit-performance.html?stats=undefined&metric=mean&operation=WRITE&smoothing=1&show_aggregates=true&xmin=0&xmax=91.08&ymin=0&ymax=0.33)

Refer to the guides of LOGBack settings, authentication caches and whitelist settings to get best possible performance.


## Connections

A typical Cassandra client will connect to many (if not all) nodes in the Cassandra cluster.
For this reason audit records will be created on several places when a new client connect to a cluster.
Further, it is not uncommon for a client to set up several connections to a server which will result in several audit records for user authentication.

Some clients will also make a few internal queries to the internal system tables in Cassandra.
These queries will appear as any other audit record even though they were not generated by the actual application logic at the client.
