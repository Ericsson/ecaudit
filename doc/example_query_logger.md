# Query Logger Example

This short guide will demonstrate how to setup ecAudit as a pure Query Logger in combination with the [Chronicle Logger](chronicle_logger.md) backend.
In this example all CQL queries will be logged without any filtering, and users will be able to connect without any credentials.
With a setup like this it is possible to log all CQL queries in a cluster for troubleshooting purposes and similar. 

## cassandra.yaml

Change the following setting in your ```cassandra.yaml```.

```
role_manager: com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager
```

Leave the ```authenticator``` and ```authorizer``` settings at their default.

```
authenticator: AllowAllAuthenticator
authorizer: AllowAllAuthorizer
```


## cassandra-env.sh

Add the following JVM option to the ```cassandra-env.sh``` or the ```cassandra.in.sh```.

**Note:** If you configure these settings in your ```cassandra-env.sh```,
consider that the ```JVM_EXTRA_OPTS``` variable is consumed at the end of the file,
so make sure to add the following lines *before* they are consumed.

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Dcassandra.custom_query_handler_class=com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler"
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=NONE"
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -da:net.openhft..."
```


## audit.yaml

Setup the ```audit.yaml``` as follows to roll a new Chronicle log file every minute,
and store only fields which are relevant in this example.
Also, use ```post_logging``` to make sure there is one audit record per CQL request.

```
log_timing_strategy: post_logging

logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger
      parameters:
      - log_dir: /var/lib/cassandra/audit
        roll_cycle: MINUTELY
        fields: TIMESTAMP, OPERATION, BATCH_ID, STATUS
```

## eclog

The query logs contain binary data since they are written with the Chronicle logger backend.

The ```eclog``` tool will print the content in a human readable format.
With a configuration file it is possible to get a custom format of each record.
Create a ```eclog.yaml``` file with the following content in the same place as the chronicle log files under ```/var/lib/casssandra/audit/```.

```
log_format: "${TIMESTAMP}|{?${BATCH_ID}?}|${STATUS}|${OPERATION}"
time_format: "yyyy-MM-dd HH:mm:ss z"
```

Here's an example on how that could look.

```
# java -jar eclog.jar -t 6 /var/lib/cassandra/audit/
2020-04-01 12:42:02 CEST||ATTEMPT|INSERT INTO country.by_code (code, name, iso) VALUES ( 46, 'Sweden', 'SE');
2020-04-01 12:42:10 CEST||ATTEMPT|INSERT INTO country.by_code (code, name, iso) VALUES ( 47, 'Norway', 'NO');
2020-04-01 12:42:18 CEST||ATTEMPT|INSERT INTO country.by_code (code, name, iso) VALUES ( 48, 'Poland', 'PL');
2020-04-01 12:42:24 CEST||ATTEMPT|INSERT INTO country.by_code (code, name, iso) VALUES ( 49, 'Germany', 'DE');
2020-04-01 12:42:32 CEST||ATTEMPT|INSERT INTO country.by_code (code, name, iso) VALUES ( 51, 'Peru', 'PE');
2020-04-01 12:43:10 CEST||ATTEMPT|SELECT iso FROM country.by_code WHERE code = 46;
```
