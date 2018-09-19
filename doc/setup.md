# Setup

Configure ecAudit for usage by following the steps outlined below.


## Deploy JAR

Install the ecAudit jar file in your ```$CASSANDRA_HOME/lib/``` directory


## Enable plug-in

Add below JVM option to your ```cassandra-env.sh```

**Note:** The ```JVM_EXTRA_OPTS``` variable is consumed at the end of ```cassandra-env.sh```,
so make sure to add the following line *before* it is consumed.

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Dcassandra.custom_query_handler_class=com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler"
```

Change the following setting in your ```cassandra.yaml```

```
authenticator: com.ericsson.bss.cassandra.ecaudit.auth.AuditPasswordAuthenticator
role_manager:  com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager
```

The AuditPasswordAuthenticator and AuditRoleManager extends the standard PasswordAuthenticator and CassandraRoleManager respectivelly.
All configuration options and recommendations for the standard plug-ins applies for the Audit plug-ins as well.
For instance, remember to increase the replication factor of the ```system_auth``` keyspace.
Consult the Cassandra [configuration documentation](http://cassandra.apache.org/doc/latest/configuration/index.html) for details.

To get permission management in Cassandra, enable the standard ```CassandraAuthorizer```.
This is optional, but necessary to get a fully secured Cassandra deployment.


## Configure LOGBack

Add an appender and logger in your ```logback.xml``` configuration
The logger name of the audit records is ```ECAUDIT```.

Tuning tips:
* The asynchronous appender can _improve or demote_ performance depending on your setup.
* Compression on rotated may impact performance significantly.
* If you are logging large volumes of data, make sure your storage can keep up.

In the example snippet below,
LOGBack is configured to use rotation of files with a synchronous appender.
Run performance tests on your workload to find out what settings works best for you.


```XML
<!--audit log-->
<appender name="AUDIT-FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <file>${cassandra.logdir}/audit/audit.log</file>
  <encoder>
    <pattern>%d{HH:mm:ss.SSS} - %msg%n</pattern>
    <immediateFlush>true</immediateFlush>
  </encoder>
  <rollingPolicy class="ch.qos.logback.core.rolling.FixedWindowRollingPolicy">
    <fileNamePattern>${cassandra.logdir}/audit/audit.log.%i</fileNamePattern>
    <minIndex>1</minIndex>
    <maxIndex>5</maxIndex>
  </rollingPolicy>
  <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
    <maxFileSize>200MB</maxFileSize>
  </triggeringPolicy>
</appender>

<logger name="ECAUDIT" level="INFO" additivity="false">
  <appender-ref ref="AUDIT-FILE" />
</logger>
```

There are many ways to configure appenders with LOGBack.
Refere to the [official documentation](https://logback.qos.ch/manual/appenders.html) for details.


## Configure Whitelists

ecAudit support two different ways to define whitelists; Role Based Whitelists and/or YAML Whitelists.
ecAudit can be configured to use any one of the whitelists, or a combination of the two.
Finally, it is also possible to disable whitelists completely in which case all operations will be audit logged.


### Role Based Whitelists

The role based whitelist is configured usign custom role options in Cassandra.
This is the default whitelist method.

* Whitelists are configured per role in Cassandra.
* A role will inherit whitelists from other roles granted to it.
* Only roles with SUPERUSER flag or with AUTHORIZE permission on the data resource have permission to whitelist another role on data access.
* Only roles with SUPERUSER flag have permission to whitelist another role for connections.

This whitelist type is enabled by default.
To set it explisitly, add the following option near the end of your ```cassandra-env.sh```

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=ROLE"
```

Once all nodes are configured to use this whitelist backend it is possible to manage whitelisted users/resources without restart.
Whitelist management is described in the [Whitelist Management](role_whitelist_management.md) section.


### YAML Whitelists

The yaml based whitelist support whitelisting of selected users in a configuration file.

To use this whitelist method, add the following option near the end of your ```cassandra-env.sh```

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=YAML"
```

Create a ```audit.yaml``` configuraiton file in the Cassandra configuration directory.

Use the example below as a template and define the usernames to be whitelisted.

```YAML
---Whitelisted users
whitelist:
  - foo
  - bar
```

**Note**: User connection attempts are exempt from whitelisting, and will show in the audit log even if the user is whitelisted.

If a more fine-grained whitelisting is needed, consider using [Role Based Whitelists](setup.md#Role_Based_Whitelists).


### YAML 'n' Role Whitelists

This is a combination of the YAML and Role Based whitelists.

To use this whitelist method, add the following option near the end of your ```cassandra-env.sh```

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=YAML_AND_ROLE"
```

Configure the different whitelists methods as described in the sections above.


### Disable Whitelists

In order to disable whitelists all together, add the following option near the end of your ```cassandra-env.sh```

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=NONE"
```


## Finish

Whenever any of the above configuration options are modified it is necessary to restart the Casandra instance.
