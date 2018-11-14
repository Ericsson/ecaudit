# Managing Whitelists

The whitelist management described here is only applicable if you are using [Role Based Whitelists](setup.md#Role_Based_Whitelists).

With this setup whitelists are managed using custom role options in Cassandra.
Whenever a whitelist setting is changed it will be distributed automatically in the cluster.
Whitelist changes will be effective within 2 seconds (configurable) across the cluster.
We'll describe this in more detail a bit later in the tuning section below.


## Roles

By default all roles will have their authentication attempts and operations logged to the audit log.

It is possible to whitelist selected roles such that some or all their operations will be excluded from the audit log.
This can for example be used to filter out normal and expected operations from the audit log. 

Let's start with a simple example.
To __grant__ whitelisting on the existing user __jim__ on all his __select__ operations on the __decisions__ table in the __design__ keyspace,
execute the following statement: 

```SQL
cassandra@cqlsh> ALTER ROLE jim WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/design/decisions' };
```

As you can see we're using the custom __OPTIONS__ parameter associated with all Cassandra roles to manage whitelists.
The __grant__ prefix of the option name is used when we're adding to the whitelist.
The __select__ suffix of the option name is used to indicate the type of operation we are whitelisting.
Finally, the value of the option is used to specify the __resource__ on which those operations are whitelisted.

You can whitelist one operation/resource combination per statement.
To whitelist several operations, create several statements, much like you would to to grant a user permissions.

For instance, to __grant__ whitelisting on an existing user __bob__ on all his __select and modification__ operations on all __data__,
including all tables in all keyspaces,
execute the following statements:

```SQL
cassandra@cqlsh> ALTER ROLE bob WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data' };
cassandra@cqlsh> ALTER ROLE bob WITH OPTIONS = { 'grant_audit_whitelist_for_modify' : 'data' };
```

Likewise, only one resource can be defined per operation in a statement.
So to whitelist several specific tables you have to issue several statements.
For instance, to __grant__ whitelisting on an existing user __helena__ on all her __select__ operations on the __system__ keyspace and the __myks.mytable__ table,
execute the following statements:

```SQL
cassandra@cqlsh> ALTER ROLE helena WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/system' };
cassandra@cqlsh> ALTER ROLE helena WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/myks/mytable' };
```

While the __OPTIONS__ entry is accessible in Cassandras __CREATE ROLE__ statements, this is not supported by ecAudit.
To keep operations clear and to the point, whitelists can only be modified with __ALTER ROLE__ statements.

All examples so far have used the __grant__ prefix to add operation/resource combinations to the whitelist
It is of course also possible to revoke whitelisting from a user.
To revoke the whitelist from user __hans__ on all his __connection__ attempts,
execute the following statement:

```SQL
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_execute' : 'connections' };
```

Note that none of these operations changes whether the roles can _access_ these resources,
it only changes whether the operations will show in the audit log or not.
Access is managed using standard mechanisms in Cassandra.

To view current whitelists,
execute the following statement:

```SQL
cassandra@cqlsh> LIST ROLES ;

 role      | super | login | options
-----------+-------+-------+---------------------------------------------
       bob |  True |  True |        {'MODIFY': 'data', 'SELECT': 'data'}
 cassandra |  True |  True |                                          {}
      hans | False | False |                                          {}
    helena | False |  True | {'SELECT': 'data/myks/mytable,data/system'}
       jim | False |  True |         {'SELECT': 'data/design/decisions'}
```

Note how the options map list all whitelists without the grant/revoke prefix.


## Inheritance

Just like ordinary permissions, a role will inherit whitelists from other roles granted to it.

Assuming we have a whitelisted role __power__ which is not able to login,
and a user __ibbe__ which is currently not whitelisted:

```SQL
cassandra@cqlsh> CREATE ROLE power WITH LOGIN = false;
cassandra@cqlsh> ALTER ROLE power WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data' };
cassandra@cqlsh> CREATE ROLE ibbe WITH PASSWORD = 'make_it_work' AND LOGIN = true;
```

By granting __power__ to __ibbe__, he will also be whitelisted:

```SQL
cassandra@cqlsh> GRANT power TO ibbe;
```


## Operations

With ecAudit you can whitelist all operations supported by Cassandras native CQL.
The operations are __ALTER, AUTHORIZE, CREATE, DESCRIBE, DROP, EXECUTE, MODIFY and SELECT__.

The operation to whitelist should be prefixed with `grant_audit_whitelist_for_` phrase to the __ROLE__s __OPTION__ map.  

Some operations are not applicable on certain resources.
For example, it doesn't make much sense to whitelist __EXECUTE__ operations on a __role__ resource.

It is possible to grant a role whitelisting on __ALL__ operations for a specific resource.
In that case ecAudit will whitelist all applicable operations on that resource.
Example:

```SQL
cassandra@cqlsh> ALTER ROLE ibbe WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/design' };
cassandra@cqlsh> LIST ROLES OF ibbe;

 role | super | login | options
------+-------+-------+--------------------------------------------------------------------------------------------------------------------------------------------------------
 ibbe | False |  True | {'ALTER': 'data/design', 'AUTHORIZE': 'data/design', 'CREATE': 'data/design', 'DROP': 'data/design', 'MODIFY': 'data/design', 'SELECT': 'data/design'}
```


## Resources

Four types of resources can be managed in whitelists:

* connections - represent connection (authentication) attempts
* data - represent all kinds of data resources in Cassandra such as data in tables
* functions - represent all kinds of function and aggregate resources in Cassandra
* roles - represent all kinds of role resources in Cassandra

### Connection Resources

Connections are a special resource type introduced by ecAudit.
Connections are only used to grant a user whitelisting on connections attempts on Cassandras native CQL interface.
Since this resource type is specific to ecAudit, there is no corresponding permission in Cassandra.

Connection resources can only be whitelisted on the __EXECUTE__ operation.
If whitelisted, no audit record will be created when that user connects to the cluster.
For example:

```SQL
cassandra@cqlsh> ALTER ROLE ibbe WITH OPTIONS = { 'grant_audit_whitelist_for_execute' : 'connections' };
```

This resource can only be whitelisted at the root level which is referred to as __connections__.
Only roles with super-user privileges may whitelist __connections__.

### Data Resources

This is the most commonly used resource type in Cassandra
and this is what you manage to whitelist read and write operations on data in tables and views.

This resource type is referred to using the internal representation in Cassandra
which is formatted as `data/<keyspace>/<name>`,
where `<name>` can be a name of any table, view, index or type.

This resource is also used to whitelist __CREATE__ or __DROP__ operations on keyspaces, tables and types.
For example:

```SQL
cassandra@cqlsh> ALTER ROLE bob WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'data' };
```

In general the whitelist model follows the built in permission model in Cassandra.
So for instance, to grant a user permission to create a secondary index or a view on a table,
that user must have the __ALTER__ permission on the _table_.
Consequently, to whitelist that operation,
you must grant that user whitelisting for __ALTER__ operations on the _table_. 
Example:

```SQL
cassandra@cqlsh> GRANT ALTER ON myks.mytable TO helena;
cassandra@cqlsh> ALTER ROLE helena WITH OPTIONS = { 'grant_audit_whitelist_for_alter' : 'data/myks/mytable' };
```

Refer to Cassandras documentation for a full listing of required permissions.

### Function Resources

This resource type is used to whitelist __CREATE__ and __DROP__ operations of __functions__ and __aggregates__.

This resource type is referred to using the internal representation in Cassandra
which is formatted as `functions/<keyspace>/<name>`,
where `<name>` can be a name of any function or aggregate.

The following example illustrates how to whitelist creation of functions and aggregates in a keyspace:

```SQL
cassandra@cqlsh> ALTER ROLE ibbe WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'functions/myks' };
```

The following example illustrates how to whitelist the drop operations of a specific function or aggregate:

```SQL
cassandra@cqlsh> ALTER ROLE ibbe WITH OPTIONS = { 'grant_audit_whitelist_for_drop' : 'functions/myks/myfunc|DoubleType' };
```

### Role Resources

This resource type is used to represent roles in Cassandra.

This resource type is referred to using the internal representation in Cassandra
which is formatted as `roles/<name>`,
where `<name>` can be a name of any specific role.

There is a range of operations that can be whitelisted on a role.

For example, to whitelist the creation of new roles:

```SQL
cassandra@cqlsh> ALTER ROLE ibbe WITH OPTIONS = { 'grant_audit_whitelist_for_create' : 'roles' };
```

It is possible to whitelist changes a role makes on other existing roles.
For instance, this change would allow __ibbe__ to perform any changes to __jims__ whitelists,
without any record of that in the audit log.

```SQL
cassandra@cqlsh> ALTER ROLE ibbe WITH OPTIONS = { 'grant_audit_whitelist_for_alter' : 'roles/jim' };
```


## Permissions

Any role with the __SUPERUSER__ flag can manage __connections__, __roles__, __functions__ and __data__ whitelists on all roles.
Further, any role with __AUTHORIZE__ permission on a resource will be able to manage whitelists on that resource.
For instance, a role which have the __AUTHORIZE__ permission on __ALL__ __KEYSPACES__ will be able to manage __data__ whitelists on all roles, including itself:

```SQL
cassandra@cqlsh> GRANT AUTHORIZE ON ALL KEYSPACES TO micke;

micke@cqlsh> ALTER ROLE micke WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' };
```

The __connections__ resource is specific to ecAudit an may only be whitelisted by super-users.


## Tuning

To minimize overhead ecAudit will cache whitelists associated with roles,
much as Cassandra does natively with roles and permissions.
The maximum validity time and update interval of cached entries is configurable.
The audit whitelists cache is using the same configuration parameters as the roles cache.
They're called ```roles_validity_in_ms``` and ```roles_update_interval_in_ms``` in the ```cassandra.yaml```.
 
By default they're both configured to 2000ms.
This will effectively disable asynchronous updates in the background.
As a result client requests will sometimes block on the whitelist check while whitelist settings is refreshed from disk.
For this reason it is recommended enable background updates
by setting an explicit value on ```roles_update_interval_in_ms```,
and then set the ```roles_validity_in_ms``` a few seconds higher than ```roles_update_interval_in_ms```.
Please review the documentation for these values in the ```cassandra.yaml``` to understand the consequences of these changes.
