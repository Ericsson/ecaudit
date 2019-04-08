# YAML 'n' ROLE Whitelists

This is a combination of the [YAML](yaml_whitelist_management.md) and [Role Based Whitelists](role_whitelist_management.md).

To use this whitelist method, add the following option near the end of your ```cassandra-env.sh```

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=YAML_AND_ROLE"
```

Then configure the different whitelists as described in the corresponding section.
