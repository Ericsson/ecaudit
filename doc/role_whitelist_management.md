# Managing Whitelists

The whitelist management described here is only applicable if you are using [Role Based Whitelists](setup.md#Role_Based_Whitelists).

With this setup whitelists are managed using custom role options in Cassandra.
Whenever a whitelist setting is changed it will be distriuted automatically in the cluster.
Whitelist changes will be effective within 2 seconds (configurable) across the cluster.


## Roles

By default all roles will have their authentication attempts and data operations logged to the audit log.

It is possible to whitelist selected roles such that their operations will be excluded from the audit log.

For instance, to __grant__ whitelisting on an existing user __bob__ on __all__ his operations on all __data__,
including all tables in all keyspaces,
execute the following statement:

```SQL
cassandra@cqlsh> ALTER ROLE bob WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' };
```

It is also possible to whitelist operations on a specific keyspace or table.
For instance, to __grant__ whitelisting on an existing user __borje__ on __all__ his operations on the __system__ keyspace and the __myks.mytable__ table,
execute the following statement:

```SQL
cassandra@cqlsh> ALTER ROLE borje WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/system, data/myks/mytable' };
```

It is also possible to whitelist a user immediatelly at creation.
To __grant__ whitelisting to user __helena__ on __all__ her operations on all __data__,
execute the following statement:

```SQL
cassandra@cqlsh> CREATE ROLE helena WITH PASSWORD = 'rule_the_world' AND LOGIN = true AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' };
```

It is possible to revoke whitelisting from a user.
To revoke the whitelist from user __hans__ on __all__ his __connection__ attempts,
execute the following statement:

```SQL
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_all' : 'connections' };
```

Note that none of these operations changes whether the roles can _access_ these resources,
it only changes whether the operations will show in the audit log or not.

To view current whitelists,
execute the following statement:

```SQL
cassandra@cqlsh> LIST ROLES;

 role         | super | login | options
--------------+-------+-------+---------------------------------------------------------------------
    cassandra |  True |  True |                                     {'audit_whitelist_for_all': ''}
          bob | False |  True |                                 {'audit_whitelist_for_all': 'data'}
        borje | False |  True |       {'audit_whitelist_for_all': 'data/system, data/myks/mytable'}
       helena | False |  True |                                 {'audit_whitelist_for_all': 'data'}
         hans | False |  True |                                     {'audit_whitelist_for_all': ''}
```


## Inheritance

Just like ordinary permissions, a role will inherit whitelists from other roles granted to it.

Assuming we have a whitelisted role __power__ which is not able to login,
and a user __ibbe__ which is currently not whitelisted:

```SQL
cassandra@cqlsh> CREATE ROLE power WITH LOGIN = false AND OPTIONS = { 'grant_audit_whitelist_for_all' : 'data' };
cassandra@cqlsh> CREATE ROLE ibbe WITH PASSWORD = 'make_it_work' AND LOGIN = true;
```

By granting __power__ to __ibbe__, he will also be whitelisted:

```SQL
cassandra@cqlsh> GRANT power TO ibbe;
```


## Resources

Three types of resources can be managed in whitelists:

* connections - represent connection (authentication) attempts
* data - represent all kinds of data resources in Cassandra such as data in tables
* roles - represent all kinds of role resources in Cassandra

Resources can be __granted__ or __revoked__ in the option map when creating or altering roles:

```SQL
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'data/someks' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'roles/jan' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_all' : 'connections' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_all' : 'data' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_all' : 'roles' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'grant_audit_whitelist_for_all' : 'connections, data' };
cassandra@cqlsh> ALTER ROLE hans WITH OPTIONS = { 'revoke_audit_whitelist_for_all' : 'data, connections' };
```


## Permissions

Any role with the __SUPERUSER__ flag can manage __connections__, __roles__ and __data__ whitelists on all roles.

Any role whith __AUTHORIZE__ permission on __ALL__ __KEYSPACESS__ will be able to manage __data__ whitelists on all roles, including itself:

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
