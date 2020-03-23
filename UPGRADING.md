# Upgrading

Below you'll find special instructions to consider when upgrading to a new version of ecAudit.
Check out the [change log](CHANGES.md) for a full list of new features and fixes.


## To version 2.5.x

Administrators should be aware of the following changes when upgrading to version 2.5.0 or later.
ecAudit can be upgraded to 2.5.x from any previous major version as long as these and any intermediate upgrade instructions are observed.

As of version 2.5.0 the ```AuditPasswordAuthenticator``` is deprecated and should no longer be configured as the ```authenticator``` in ```cassandra.yaml```.
Users should instead use the ```AuditAuthenticator``` which can delegate authentication operations to custom ```IAuthenticator``` implementation.
By default the new ```AuditAuthenticator``` will behave exactly like the ```AuditPasswordAuthenticator```.

## To version 2.1.x

Administrators should be aware of the following changes when upgrading from version 0.x.x, 1.x.x or 2.0.x to 2.1.0 or later.
ecAudit can be upgraded to 2.1.x from any previous major version as long as these and any intermediate upgrade instructions are observed.

As of version 2.1.0 ecAudit will read and use the configuration in the audit.yaml file stored in the Cassandra configuration directory.
This directory is typically ```/etc/cassandra/conf/``` but may be different depending on your deployment.

The path can be overridden with the ```com.ericsson.bss.cassandra.ecaudit.config``` Java property.
In previous versions of ecAudit there was a typo which caused ecAudit to evaluate the `com.ericsson.bss.cassandra.eaudit.config` Java property instead.
Users who used this previously undocumented property should fix typo in their config when upgrading to 2.1.0 or later.

## To version 2.0.x

Administrators should be aware of the following changes when upgrading from version 0.x.x or 1.x.x to 2.0.0 or later.
ecAudit can be upgraded to 2.x.x from any previous major version as long as these and any intermediate upgrade instructions are observed.

As of version 2.0.0 ecAudit support whitelisting of specific operations on a resource.
For example, it is possible to whitelisted a user for `SELECT` operations on a table, without whitelisting other operations such as the `MODIFY` operations on the same table.
Example:

```SQL
cassandra@cqlsh> ALTER ROLE bob WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/design/decisions' };
```

As of this version ecAudit will also support whitelist options with upper case letters and space characters instead of underscore.
So, the previous example could just as well have been expressed as:

```SQL
cassandra@cqlsh> ALTER ROLE bob WITH OPTIONS = { 'GRANT AUDIT WHITELIST FOR SELECT' : 'data/design/decisions' };
```

For a complete guide on new whitelisting options, refer to the [whitelist](doc/role_whitelist_management.md) guide.

The following non-backwards compatible changes have been made:
* It is no longer possible to configure whitelists in the `CREATE ROLE` statement - whitelists can only be configured with the `ALTER ROLE` statement
* It is no longer possible to configure whitelists for several resources in one `ALTER ROLE` statement - to whitelist several resources, issue several statements
* When using the `LIST ROLES` statement, the whitelist options are presented in a different format compared to previous versions of ecAudit
* Whitelist representation on disk have been modified - manual steps are required to upgrade properly as described below

### Instructions
Representation on disk for role based whitelists have been changed in order to support operation specific whitelists.
The new version of ecAudit can co-exist with older versions of ecAudit.
The new version of ecAudit can be applied on one node at a time.
Cassandra has to be restarted on a node for it to pick up the new version of ecAudit.
On startup, ecAudit will convert existing whitelists to the new representation.
Users should avoid to modify whitelists while there are mixed versions of ecAudit in the Cassandra cluster.

__NOTE__: Once all instances of ecAudit have been upgraded it is necessary to clean up the legacy whitelist representation for correct operation.
As a user with super-user privileges in Cassandra,
issue the following statement on one node in the cluster __after__ upgrading __all__ nodes to ecAudit 2.0.0 or later.

The super-user should perform the `ALTER ROLE` statement on itself. In this example the `cassandra` role is used.

```SQL
cassandra@cqlsh> ALTER ROLE cassandra WITH OPTIONS = { 'drop_legacy_audit_whitelist_table' : 'now' };
```


## To version 1.x.x

Administrators should be aware of the following changes when upgrading from version 0.x.x to 1.0.0 or later.
ecAudit can be upgrade to 1.x.x from any 0.x.x version as long as the upgrade instructions below are observed.

ecAudit now provides a custom authorizer plug-in in.
This is necessary in order to resolve issue #31,
allowing a role to whitelist applicable to a grantee as long as the role has `AUTHORIZE` permission on the resource.

### Instructions

As ecAudit is upgraded, make sure to configure the `authorizer` property in the `cassandra.yaml` according to the instructions in the [setup guide](doc/setup.md).
