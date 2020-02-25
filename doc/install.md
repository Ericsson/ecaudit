# Installation

Follow these instructions to install ecAudit in each node of your Cassandra cluster.


## Deploy Plug-In Jar File

Place the ecAudit jar file in your ```$CASSANDRA_HOME/lib/``` directory.
Get the official releases from [Maven Central](https://search.maven.org/search?q=g:%22com.ericsson.bss.cassandra.ecaudit%22%20AND%20a:%22ecaudit_c3.0%22).

## Enable Plug-In

The ecAudit plug-in is enabled by configuring four different plug-in settings in Cassandra.


### cassandra.yaml

Change the following settings in your ```cassandra.yaml```.

```
authenticator: com.ericsson.bss.cassandra.ecaudit.auth.AuditPasswordAuthenticator
authorizer: com.ericsson.bss.cassandra.ecaudit.auth.AuditAuthorizer
role_manager: com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager
```

The AuditPasswordAuthenticator, AuditAuthorizer and AuditRoleManager extends the standard PasswordAuthenticator, CassandraAuthorizer and CassandraRoleManager respectively.
All configuration options and recommendations for the standard plug-ins applies for the Audit plug-ins as well.
For instance, remember to increase the replication factor of the ```system_auth``` keyspace.
Consult the Cassandra [configuration documentation](http://cassandra.apache.org/doc/latest/configuration/index.html) for details.


### cassandra-env.sh

Add the following JVM option to your ```cassandra-env.sh``` or your ```cassandra.in.sh```.

**Note:** If you configure these settings in your ```cassandra-env.sh```,
consider that the ```JVM_EXTRA_OPTS``` variable is consumed at the end of the file,
so make sure to add the following line *before* it is consumed.

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Dcassandra.custom_query_handler_class=com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler"
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -da:net.openhft..."
```

The first line installs the ecAudit QueryHandler plug-in.
The second line disable asserts for the Chronicle logger backend.
