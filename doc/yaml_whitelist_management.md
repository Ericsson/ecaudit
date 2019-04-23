# YAML Whitelist Management

The yaml based whitelist support whitelisting of selected users in the ```audit.yaml``` configuration file.

## Enable Role Base Whitelists

To use this whitelist method, add the following option near the end of your ```cassandra-env.sh```

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=YAML"
```


## Manage The Whitelist

The whitelist is configured with settings in the ```audit.yaml``` configuration file.

Use the example below as a template and define the usernames to be whitelisted.

```YAML
whitelist:
    - foo
    - bar
```

**Note**: User connection attempts are exempt from whitelisting, and will show in the audit log even if the user is whitelisted.

If a more fine-grained whitelisting is needed, consider using [Role Based Whitelists](role_whitelist_management.md).
