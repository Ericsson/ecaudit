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

You can whitelist several operations in one statement.

For instance, to __grant__ whitelisting on an existing user __bob__ on all his __select and modification__ operations on all __data__,
including all tables in all keyspaces,
execute the following statement:

```SQL
cassandra@cqlsh> ALTER ROLE bob WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data', 'grant_audit_whitelist_for_modify' : 'data' };
```

However, only one resource can be defined per operation in one statement.
So to whitelist several tables you have to issue several statements.
For instance, to __grant__ whitelisting on an existing user __borje__ on all his __select__ operations on the __system__ keyspace and the __myks.mytable__ table,
execute the following statements:

```SQL
cassandra@cqlsh> ALTER ROLE borje WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/system' };
cassandra@cqlsh> ALTER ROLE borje WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/myks/mytable' };
```

It is also possible to whitelist a user immediately at creation.
To __grant__ whitelisting to user __helena__ on all her __modification__ operations on all __data__,
execute the following statement:

```SQL
cassandra@cqlsh> CREATE ROLE helena WITH PASSWORD = 'rule_the_world' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_modify' : 'data' };
```

All examples so far have used the __grant__ prefix to add operation-resource combinations to the whitelsit
It is of course also possible to revoke whitelisting from a user.
To revoke the whitelist from user __hans__ on all his __connection__ attempts,
execute the following statement:

```SQL
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_execute' : 'connections' };
```

Note that none of these operations changes whether the roles can _access_ these resources,
it only changes whether the operations will show in the audit log or not.

To view current whitelists,
execute the following statement:

```SQL
cassandra@cqlsh> LIST ROLES;

 role         | super | login | options
--------------+-------+-------+--------------------------------------------------------------------------------
    cassandra |  True |  True |                                                 {'audit_whitelist_for_all': ''}
          bob | False |  True |    {'audit_whitelist_for_select': 'data', 'audit_whitelist_for_modify': 'data'}
        borje | False |  True |                {'audit_whitelist_for_select': 'data/system, data/myks/mytable'}
          jim | False |  True |                        {'audit_whitelist_for_select' : 'data/design/decisions'}
       helena | False |  True |                                          {'audit_whitelist_for_modify': 'data'}
         hans | False |  True |                                                 {'audit_whitelist_for_all': ''}
```

Note how the options map list all whitelists without the grant/revoke prefix.


## Inheritance

Just like ordinary permissions, a role will inherit whitelists from other roles granted to it.

Assuming we have a whitelisted role __power__ which is not able to login,
and a user __ibbe__ which is currently not whitelisted:

```SQL
cassandra@cqlsh> CREATE ROLE power WITH LOGIN = false AND OPTIONS = { 'grant_audit_whitelist_for_select' : 'data' };
cassandra@cqlsh> CREATE ROLE ibbe WITH PASSWORD = 'make_it_work' AND LOGIN = true;
```

By granting __power__ to __ibbe__, he will also be whitelisted:

```SQL
cassandra@cqlsh> GRANT power TO ibbe;
```


## Operations


## Resources

Four types of resources can be managed in whitelists:

* connections - represent connection (authentication) attempts
* data - represent all kinds of data resources in Cassandra such as data in tables
* functions - represent all kinds of function and aggregate resources in Cassandra
* roles - represent all kinds of role resources in Cassandra

Resources can be __granted__ or __revoked__ in the option map when creating or altering roles:

```SQL
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'grant_audit_whitelist_for_execute' : 'connections' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/someks' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'grant_audit_whitelist_for_execute' : 'functions/myks/myfunc' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'grant_audit_whitelist_for_grant' : 'roles/jan' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_execute' : 'connections' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_select' : 'data' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_grant' : 'roles' };
```


## Permissions

Any role with the __SUPERUSER__ flag can manage __connections__, __roles__ and __data__ whitelists on all roles.
Further, any role with __AUTHORIZE__ permission on a resource will be able to manage whitelists on that resource.
For instance, a role which have the __AUTHORIZE__ permission on __ALL__ __KEYSPACESS__ will be able to manage __data__ whitelists on all roles, including itself:

```SQL
cassandra@cqlsh> GRANT AUTHORIZE ON ALL KEYSPACES TO micke;

micke@cqlsh> ALTER ROLE micke WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' };
```


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
