# Chronicle Logger

The Chronicle logger backend stores audit records in a binary format and is more performant than the SLF4J logger.
Audit records are viewed with the ```eclog``` tool.

Update the ```audit.yaml``` file to enable the Chronicle logger and set target directory for audit records.

```YAML
logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger
      parameters:
      - log_dir: /var/lib/cassandra/audit
```


## Options

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

The oldest log files will be discarded once a size threshold is reached.
By default 16GB of log files will be retained before the oldest is deleted.
The value is specified in *bytes*.

```YAML
logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger
      parameters:
      - log_dir: /var/lib/cassandra/audit
        log_max_size: 536870912 # 512MB
```

## The eclog tool

The binary Chronicle log files can be viewed with the provided ```eclog``` tool.

```bash
$ java -jar eclog.jar <log-dir>
```

