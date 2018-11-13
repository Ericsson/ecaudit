# Upgrading

## To version 2.x.x

Administrators should be aware of the following changes when upgrading from version 0.x.x or 1.x.x to 2.0.0 or later.
ecAudit can be upgraded to 2.x.x from any previous major version as long as these and any intermediate upgrade instructions are observed.

ecAudit now support whitelisting of specific operations on a resource.
For example, a user can be whitelisted for `SELECT` operations on a table, but not `MODIFY` operations on the same table.
Example:

```SQL
cassandra@cqlsh> ALTER ROLE bob WITH OPTIONS = { 'grant_audit_whitelist_for_select' : 'data/design/decisions' };
```

For a complete guide on new whitelisting options, refer to the [whitelist](doc/role_whitelist_management.md) guide.

The following non-backwards compatible changes have been made:
* It is no longer possible to configure whitelists in the `CREATE ROLE` statement - whitelists can only be configured with the `ALTER ROLE` statement
* It is no longer possible to configure whitelists for several resources in one `ALTER ROLE` statement - to whitelist several resources, issue several statements

### Instructions
Representation on disk for role based whitelists have been changed in order to support operation specific whitelists:.
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
cassandra@cqlsh> ALTER ROLE cassandra WITH OPTIONS = { 'drop_v1_audit_whitelists' : 'now' };
```


## To version 1.x.x

Administrators should be aware of the following changes when upgrading from version 0.x.x to 1.0.0 or later.
ecAudit can be upgrade to 1.x.x from any 0.x.x version as long as the upgrade instructions below are observed.

ecAudit now provides a custom authorizer plug-in in.
This is necessary in order to resolve issue (Ericsson/ecaudit#31),
allowing a role to whitelist applicable to a grantee as long as the role has `AUTHORIZE` permission on the resource.

### Instructions

As ecAudit is upgraded, make sure to configure the `authorizer` property in the `cassandra.yaml` according to the instructions in the [setup guide](doc/setup.md).
