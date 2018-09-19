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

Change the following settings in your ```cassandra.yaml```

```
authenticator: com.ericsson.bss.cassandra.ecaudit.auth.AuditPasswordAuthenticator
authorizer: com.ericsson.bss.cassandra.ecaudit.auth.AuditAuthorizer
role_manager:  com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager
```

The AuditPasswordAuthenticator, AuditAuthorizer and AuditRoleManager extends the standard PasswordAuthenticator, CassandraAuthorizer and CassandraRoleManager respectively.
All configuration options and recommendations for the standard plug-ins applies for the Audit plug-ins as well.
For instance, remember to increase the replication factor of the ```system_auth``` keyspace.
Consult the Cassandra [configuration documentation](http://cassandra.apache.org/doc/latest/configuration/index.html) for details.

Disable asserts for the Chronicle logger backend.
Depending on your Cassandra version, add the following JVM option to your ```jvm.options``` or ```cassandra-env.sh``` file.

```
-da:net.openhft...
```


## Configure Logger Backend

The ecAudit plug-in is shipped with two different logger backends - SLF4J and Chronicle.
The SLF4J logger can be configured with LogBack to produce various log file formats,
including clear text log files.
The Chronicle logger produces binary log files but is more performant when handling large volumes of audit records. 

The logger backend is configured in the ```audit.yaml```.
By default ecAudit will look for the ```audit.yaml``` file in the Cassandra configuration directory.
The path to the configuration file can be overridden with the ```com.ericsson.bss.cassandra.ecaudit.config``` Java property.


### SLF4J Logger

The SLF4J logger is used by default.
This can be configured explicitly.

```YAML
logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger
```

The SLF4J logger backend can be configured further with custom log message formats.


#### Custom Log Message Format

To configure a custom log message format the following parameters can be configured in the ```audit.yaml``` file:

| Parameter   | Description                                                       | Default |
| ----------- | ----------------------------------------------------------------- | --------------- |
| log_format  | Parameterized log message formatting string, see examples below  | the "legacy" format, see [README](../README.md) |
| time_format | time formatter pattern, see examples below or [DateTimeFormatter](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns) | number of millis since EPOCH |
| time_zone   | the time zone id, see examples below or [ZoneId](https://docs.oracle.com/javase/8/docs/api/java/time/ZoneId.html#of-java.lang.String-) | system default |

It is possible to configure a parameterized log message by providing a formatting string.
The following fields are available:

| Field       | Field Value                                                       | Mandatory Field |
| ----------- | ----------------------------------------------------------------- | --------------- |
| CLIENT      | Client IP address                                                 | yes             |
| USER        | Username of the authenticated user                                | yes             |
| BATCH_ID    | Internal identifier shared by all statements in a batch operation | no              |
| STATUS      | Value is either ATTEMPT or FAILED                                 | yes             |
| OPERATION   | The CQL statement or a textual description of the operation       | yes             |
| TIMESTAMP   | The system timestamp of the request (\*) (**)                     | yes             |

(*) - This timestamp is more accurate than the LOGBack time (since that is written asynchronously).
If this timestamp is used, then the LOGBack timestamp can be removed by reconfiguring the encoder pattern in logback.xml.

(**) - It is possible to configure a custom display format.

Modify the ```audit.yaml``` configuration file.
Field name goes between ```${``` and ```}``` (*bash*-style parameter substitution).
Use the example below as a template to define the log message format.

```YAML
logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger
      parameters:
      - log_format: "client=${CLIENT}, user=${USER}, status=${STATUS}, operation='${OPERATION}'"
```

Which will generate logs entries like this:

```
15:18:14.089 - client=127.0.0.1, user=cassandra, status=ATTEMPT, operation='SELECT * FROM students'
```

*Conditional formatting* of fields is also available, which makes it possible to only log the field value and
its descriptive text only if a value exists, which can be useful for optional fields like BATCH_ID.
Conditional fields and its associated text goes between ```{?``` and ```?}```.
The example below use conditional formatting for the batch id to get different log messges depending on if the operation
was part of a batch or not. Also the TIMESTAMP field have a custom time format configured.

```YAML
logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger
      parameters:
      - log_format: "${TIMESTAMP}-> client=${CLIENT}, user=${USER}, status=${STATUS}, {?batch-id=${BATCH_ID}, ?}operation='${OPERATION}'"
        time_format: "yyyy-MM-dd HH:mm:ss.SSS"
        time_zone: UTC
```

Which will generate logs entries like this (assuming LOGBack pattern does not contain timestamp as well):

```
2019-02-28 15:18:14.089-> client=127.0.0.1, user=cassandra, status=ATTEMPT, operation='SELECT * FROM students'
2019-02-28 15:18:14.090-> client=127.0.0.1, user=cassandra, status=ATTEMPT, batch-id=6f3cae9b-f1f1-4a4c-baa2-ed168ee79f9d, operation='INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[1, '1', 'valid']'
2019-02-28 15:18:14.091-> client=127.0.0.1, user=cassandra, status=ATTEMPT, batch-id=6f3cae9b-f1f1-4a4c-baa2-ed168ee79f9d, operation='INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[2, '2', 'valid']'
```


#### Configure LOGBack

When using the SLF4J logger, update the Cassandra ```logback.xml``` file to define path and rolling policy
for generated audit logs.
Add an appender and logger in your ```logback.xml``` configuration.
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
Refer to the [official documentation](https://logback.qos.ch/manual/appenders.html) for details.


### Chronicle Logger

The Chronicle logger backend stores audit records in a binary format and is more performant than the SLF4J logger.
Audit records are viewed with the ```eclog``` tool.

Update the ```audit.yaml``` file to enable the Chronicle logger and set target directory for audit records.

```YAML
logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger
      parameters:
      - log_dir: /var/lib/cassandra/audit
```


#### Options

The Chronicle logger will roll to a new file every hour by default.
The roll cycle frequency is configurable.
Valid options are ```MINUTELY```, ```HOURLY```, and ```DAILY```.

```YAML
logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger
      parameters:
      - log_dir: /var/lib/cassandra/audit
        roll_cycle: MINUTELY
```

Log files will be rotated once a size threshold is reached.
16GB of log files will be retained before the oldest is deleted.
The value must be specified in *bytes*.

```YAML
logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger
      parameters:
      - log_dir: /var/lib/cassandra/audit
        log_max_size: 536870912 # 512MB
```

#### The eclog tool

The binary Chronicle log files can be viewed with the provided ```eclog``` tool.

```bash
$ java -jar eclog.jar <log-dir>
```

## Configure Whitelists

ecAudit support two different ways to define whitelists - Role Based Whitelists and YAML Whitelists.
ecAudit can be configured to use any one of the whitelists, or a combination of the two.
Finally, it is also possible to disable whitelists completely in which case all operations will be audit logged.


### Role Based Whitelists

The role based whitelist is configured using custom role options in Cassandra.
This is the default whitelist method.

* Whitelists are configured per role in Cassandra.
* A role will inherit whitelists from other roles granted to it.
* Only roles with SUPERUSER flag or with AUTHORIZE permission on the data resource have permission to whitelist another role on data access.
* Only roles with SUPERUSER flag have permission to whitelist another role for connections.

This whitelist type is enabled by default.
To set it explicitly, add the following option near the end of your ```cassandra-env.sh```

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

Modify the ```audit.yaml``` configuration file.

Use the example below as a template and define the usernames to be whitelisted.

```YAML
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
