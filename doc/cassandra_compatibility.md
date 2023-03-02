# Compatibility

There are different flavors of the ecAudit plug-in; one for each supported version of Apache Cassandra.
The Cassandra version that was used during build and integration tests can be derived from the full name of the ecAudit plugin.
For instance, ecaudit_c3.11 indicate that the ecAudit flavor was built with Cassandra 3.11.x.

The ecAudit plug-in is maintained for selected Cassandra versions only.


## ecaudit_c4.1

This flavor is built with the latest version of the Apache Cassandra 4.1.x series.
The table below list the Cassandra version used while building each ecAudit release and indicate compatibility with other Cassandra versions.

| ecAudit Version  | Compiled With   | Compatible With          |
| ---------------- | --------------- | ------------------------ |
| 3.0.0 -> latest  | Cassandra 4.1.1 | Cassandra 4.1.0 -> 4.1.1 |


## ecaudit_c4.0

This flavor is built with the latest version of the Apache Cassandra 4.0.x series.
The table below list the Cassandra version used while building each ecAudit release and indicate compatibility with other Cassandra versions. 

| ecAudit Version  | Compiled With   | Compatible With          |
| ---------------- | --------------- | ------------------------ |
| 2.10.0 -> latest | Cassandra 4.0.7 | Cassandra 4.0.2 -> 4.0.8 |
| 2.9.0  -> latest | Cassandra 4.0.3 | Cassandra 4.0.2 -> 4.0.8 |


## ecaudit_c3.11

This flavor is built with the latest version of the Apache Cassandra 3.11.x series.
The table below list the Cassandra version used while building each ecAudit release and indicate compatibility with other Cassandra versions. 

| ecAudit Version  | Compiled With     | Compatible With              |
| ---------------- | ----------------- | ---------------------------- |
| 2.10.0 -> latest | Cassandra 3.11.14 | Cassandra 3.11.12 -> 3.11.14 |
| 2.9.1            | Cassandra 3.11.12 | Cassandra 3.11.12 -> 3.11.14 |
| 2.9.0            | Cassandra 3.11.12 | Cassandra 3.11.11 -> 3.11.14 |
| 2.8.0            | Cassandra 3.11.11 | Cassandra 3.11.2  -> 3.11.11 |
| 2.7.0  -> 2.7.1  | Cassandra 3.11.10 | Cassandra 3.11.2  -> 3.11.10 |
| 2.3.0  -> 2.6.0  | Cassandra 3.11.6  | Cassandra 3.11.2  -> 3.11.6  |
| 2.1.0  -> 2.2.2  | Cassandra 3.11.4  | Cassandra 3.11.2  -> 3.11.4  |
| 1.0.0  -> 2.0.0  | Cassandra 3.11.3  | Cassandra 3.11.2  -> 3.11.3  |


## ecaudit_c3.0

This flavor is built with the latest version of the Apache Cassandra 3.0.x series.
The table below list the Cassandra version used while building each ecAudit release and indicate compatibility with other Cassandra versions. 

| ecAudit Version  | Compiled With    | Compatible With            |
| ---------------- | ---------------- | -------------------------- |
| 2.10.0 -> latest | Cassandra 3.0.28 | Cassandra 3.0.25 -> 3.0.28 |
| 2.9.0            | Cassandra 3.0.26 | Cassandra 3.0.25 -> 3.0.27 |
| 2.8.0            | Cassandra 3.0.25 | Cassandra 3.0.16 -> 3.0.25 |
| 2.7.0  -> 2.7.1  | Cassandra 3.0.24 | Cassandra 3.0.16 -> 3.0.24 |
| 2.3.0  -> 2.6.0  | Cassandra 3.0.20 | Cassandra 3.0.16 -> 3.0.20 |
| 2.1.0  -> 2.2.2  | Cassandra 3.0.18 | Cassandra 3.0.16 -> 3.0.18 |
| 1.0.0  -> 2.0.0  | Cassandra 3.0.17 | Cassandra 3.0.16 -> 3.0.17 |


## ecaudit_c3.0.11

This flavor is built with Apache Cassandra 3.0.11 specifically and it is not compatible with any other version.
This flavor is in maintenance mode and so this flavor will only get critical fixes on request.
Users should consider to upgrade to one of the latest Cassandra releases together with a suitable ecAudit flavor. 

| ecAudit Version | Compiled With    | Compatible With  |
| --------------- | ---------------- | ---------------- |
| 2.0.0 -> 2.3.0  | Cassandra 3.0.11 | Cassandra 3.0.11 |


## ecaudit_c2.2

This flavor is built with the latest version of the Apache Cassandra 2.2.x series.
The table below list the Cassandra version used while building each ecAudit release and indicate compatibility with other Cassandra versions.

| ecAudit Version | Compiled With    | Compatible With           |
| --------------- | ---------------- | ------------------------- |
| 2.7.0 -> 2.8.0  | Cassandra 2.2.19 | Cassandra 2.2.8 -> 2.2.19 |
| 2.3.0 -> 2.6.0  | Cassandra 2.2.16 | Cassandra 2.2.8 -> 2.2.16 |
| 2.2.2           | Cassandra 2.2.14 | Cassandra 2.2.8 -> 2.2.14 |


## Feature compatibility

The ecAudit versions across flavors are feature compatible as far as it makes sense.
For instance ecAudit_c3.0 version 2.0.0 and ecAudit_c3.11 2.0.0 have the same plug-in features.


## Apache Cassandra 4.0

As of [CASSANDRA-12151](https://issues.apache.org/jira/browse/CASSANDRA-12151) native support for audit logs are available in Apache Cassandra 4.0 and later.

Since ecAudit was developed before CASSANDRA-12151, there are several differences to be aware of.
The most notable being:

* The parameters available to use in each audit record are different,
  more details below.

* The default format of the audit entries are different when using the SLF4J logger backend.

* The binary Chronicle format uses a different layout of the record header.
  [CASSANDRA-15076](https://issues.apache.org/jira/browse/CASSANDRA-15076) is attempting to close this gap.

* The binary Chronicle format of each audit record is different.
  Cassandra 4.0 is storing audit records as one single formatted line.
  ecAudit is storing individual parameters and is doing formatting before printing lines.

* Cassandra 4.0 uses settings in the ```cassandra.yaml``` to configure basic whitelists.
  ecAudit uses role options in CQL and/or settings in the audit.yaml file to manage whitelists.
  [CASSANDRA-14471](https://issues.apache.org/jira/browse/CASSANDRA-14471) is attempting to close this gap.

* Cassandra 4.0 will create an audit record to indicate when a new statement is prepared.
  This is not the case in ecAudit.

* Both Cassandra 4.0 and ecAudit will create separate records for the operations within a batch sometimes (for prepared statements),
  and sometimes not (regular statements).
  When Cassandra 4.0 do separate statements, they will be prepended with a summary record indicating number of records in the batch.
  The ecAudit plug-in will not write a batch summary record.

* ecAudit requires an authentication backend to be enabled.
  Cassandra 4.0 have no such requirements.
  This is being addressed in [#77](https://github.com/Ericsson/ecaudit/issues/77).

* ecAudit will by default log values for prepared statements.
  Audit logs in Cassandra 4.0 will not but this is being addressed in [CASSANDRA-14465](https://issues.apache.org/jira/browse/CASSANDRA-14465)
  FQL (Full Query Log) records in Cassandra 4.0 will include values for prepared statements.
  With ecAudit it is possible to skip the prepared statement values by configuring a custom log message format ([SLF4J Logger](slf4j_logger.md)).


### Audit Record Format

Cassandra 4.0 is creating a fixed record format with some optional fields.
The record format is the same whether the SLF4J logger or Chronicle logger is being used.

Here's a random set of example records produced by Cassandra 4.0:
```
user:cassandra|host:127.0.0.1:7000|source:/127.0.0.1|port:45164|timestamp:1556888680933|type:UPDATE|category:DML|ks:myks|scope:mytbl|operation:INSERT INTO myks.mytbl (part, value) VALUES (3,2);
user:cassandra|host:127.0.0.1:7000|source:/127.0.0.1|port:45164|timestamp:1556888680949|type:SELECT|category:QUERY|ks:myks|scope:mytbl|operation:SELECT * from myks.mytbl;
user:cassandra|host:127.0.0.1:7000|source:/127.0.0.1|port:42726|timestamp:1557392371598|type:BATCH|category:DML|operation:BEGIN BATCH
insert into myks.mytbl (part, value) VALUES (3,2);
insert into myks.mytbl (part, value) VALUES (4,2);
APPLY BATCH;
user:cassandra|host:127.0.0.1:7000|source:/127.0.0.1|port:42712|timestamp:1557392226735|type:REQUEST_FAILURE|category:ERROR|operation:select * from nonexistent.nonexistent;; keyspace nonexistent does not exist
user:anonymous|host:127.0.0.1:7000|source:/127.0.0.1|port:42854|timestamp:1557392983448|type:UPDATE|category:DML|ks:keyspace1|scope:standard1|operation:UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?
user:anonymous|host:127.0.0.1:7000|source:/127.0.0.1|port:44156|timestamp:1557402879728|type:BATCH|category:DML|batch:a6d522aa-2eff-4f6a-a768-fba362ac3f59|operation:BatchId:[a6d522aa-2eff-4f6a-a768-fba362ac3f59] - BATCH of [3] statements
user:anonymous|host:127.0.0.1:7000|source:/127.0.0.1|port:44156|timestamp:1557402879728|type:UPDATE|category:DML|batch:a6d522aa-2eff-4f6a-a768-fba362ac3f59|ks:myks|scope:mytbl|operation:UPDATE myks.mytbl SET value=? WHERE part=?;
user:anonymous|host:127.0.0.1:7000|source:/127.0.0.1|port:44156|timestamp:1557402879728|type:UPDATE|category:DML|batch:a6d522aa-2eff-4f6a-a768-fba362ac3f59|ks:myks|scope:mytbl|operation:UPDATE myks.mytbl SET value=? WHERE part=?;
user:anonymous|host:127.0.0.1:7000|source:/127.0.0.1|port:44156|timestamp:1557402879728|type:UPDATE|category:DML|batch:a6d522aa-2eff-4f6a-a768-fba362ac3f59|ks:myks|scope:mytbl|operation:UPDATE myks.mytbl SET value=? WHERE part=?;
```

The following fields are optional in Cassandra 4.0 and may be missing in some records:
* port - representing the port used by the client
* batch - indicates a UUID to correlate different records which belong to the same batch operation
* ks - representing the keyspace used by the operation where applicable
* scope - typically representing the table used by the operation but can indicate other kinds of scope
* operation - typically representing the CQL query, but can be other kinds of descriptive messages

Comparing record fields between Cassandra 4.0 and ecAudit:

| Cassandra 4.0 | ecAudit         | Comment |
| ------------- | --------------- | -------------------------------------------------------------------------------------- |
| user          | USER            |                                                                                        |
| host          | COORDINATOR     | C* 4.0 will print \<IP\>:\<port\>, while ecAudit only print \<IP\>                     |
| source        | CLIENT          | C* 4.0 will print \[\<hostname\>\]/\<IP\> (including the '/'), while ecAudit only print \<IP\> |
| port          | -               | Not present in ecAudit, addressed in [#90](https://github.com/Ericsson/ecaudit/issues/90) |
| batch         | BATCH_ID        | Correlation ID for entries in separated batches                                        |
| timestamp     | TIMESTAMP       |                                                                                        |
| type          | -               | Not present in ecAudit                                                                 |
| category      | -               | Not present in ecAudit                                                                 |
| ks            | -               | Not present in ecAudit                                                                 |
| scope         | -               | Not present in ecAudit                                                                 |
| operation     | OPERATION_NAKED | operation, without bound values appended to prepared statements                        |
| -             | OPERATION       | operation, with bound values appended to prepared statements. Not present in Cassandra |
| -             | STATUS          | Not present in Cassandra                                                               |
| -             | SUBJECT         | Not present in Cassandra                                                               |
