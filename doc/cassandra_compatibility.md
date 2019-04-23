# Compatibility

There are different flavors of the plug-in for different versions of Apache Cassandra.
The Cassandra version that was used during build and integration tests can be derived from the full name of the ecAudit plugin.
For instance, ecaudit_c3.11 indicate that the ecAudit flavor was built with Cassandra 3.11.x.

The table below list the Cassandra versions used in the current and previous builds of ecAudit.

| Flavor          | ecAudit Versions | Compiled With    |
| ----------------| ---------------- | ---------------- |
| ecaudit_c3.11   | 2.1.0  ->        | Cassandra 3.11.4 |
| ecaudit_c3.11   | 0.22.0 -> 2.0.0  | Cassandra 3.11.3 |
| ecaudit_c3.11   | 0.1.0  -> 0.21   | Cassandra 3.11.2 |
| ecaudit_c3.0    | 2.1.0  ->        | Cassandra 3.0.18 |
| ecaudit_c3.0    | 0.22.0 -> 2.0.0  | Cassandra 3.0.17 |
| ecaudit_c3.0    | 0.11.0 -> 0.21   | Cassandra 3.0.16 |
| ecaudit_c3.0    | 0.1.0  -> 0.10   | Cassandra 3.0.15 |
| ecaudit_c3.0.11 | 2.0.0  ->        | Cassandra 3.0.11 |

The ecAudit plug-in is maintained for selected Cassandra versions only.
It may be possible to use the ecAudit plug-in with related Cassandra versions as well.
But we recommend users to deploy ecAudit with the Cassandra version that was used during build.
New version flavors can be created on request.

The ecAudit versions between flavors are feature compatible as far as it makes sense.
For instance ecAudit_c3.0 version 0.4.0 and ecAudit_c3.11 0.4.0 have the same plug-in features.

As of version 0.21.0, ecAudit is available on Maven Central.
Earlier versions are not published on any public repository.

## Apache Cassandra 4.0

As of [CASSANDRA-12151](https://issues.apache.org/jira/browse/CASSANDRA-12151) native support for audit logs are available in Apache Cassandra 4.0 and later.

Since ecAudit was developed before CASSANDRA-12151, there are several differences to be aware of.
The most notable being:

* The format of the audit entries are different in ecAudit compared to CASSANDRA-12151.

* CASSANDRA-12151 uses settings in the ```cassandra.yaml``` to configure basic whitelists.
  ecAudit uses role options in CQL and/or settings in the audit.yaml file to manage whitelists.
  [CASSANDRA-14471](https://issues.apache.org/jira/browse/CASSANDRA-14471) is attempting to close this gap.


