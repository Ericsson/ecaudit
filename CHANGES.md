# Changes

## Version 2.7.1
* Obfuscate passwords properly - #170
* Extend grant based audit to system tables - #172
* Always write audit entry on whitelist exception - #168

## Version 2.7.0
* Build with Cassandra 3.11.10 (only flavor ecaudit_c3.11)
* Build with Cassandra 3.0.24 (only flavor ecaudit_c3.0)
* Build with Cassandra 2.2.19 (only flavor ecaudit_c2.2)

## Version 2.6.0
* Support prepared batch statements - #164
* Support pure query logger deployment without authentication/authorization - #77

## Version 2.5.0
* Add support for custom authenticator and the Subject audit record field - #146

## Version 2.4.0
* Optimizing cache performance for role audit filter
* Make sure authorized user can whitelist himself - #145
* Introduce grants-based role white-listing

## Version 2.3.0
* Fix announcement of internal table (only flavor ecaudit_c3.0 and ecaudit_c3.11) - #137
* Build with Cassandra 3.11.6 (only flavor ecaudit_c3.11)
* Build with Cassandra 3.0.20 (only flavor ecaudit_c3.0)
* Build with Cassandra 2.2.16 (only flavor ecaudit_c2.2)
* Log only keys - #125
* Avoid logging BLOB values - #126
* Configurable format of printed records with eclog tool - #102

## Version 2.2.2
* Backport ecAudit to Cassandra 2.2.x - #111
* Configurable fields in Chronicle backend - #101
* Improve batch-id (UUID) performance - #108

## Version 2.2.1
Incomplete release - Don't use.

## Version 2.2.0
Incomplete release - Don't use.

## Version 2.1.0
* Make configuration updateable from tests - #98
* Make wrapped authorizer backend configurable - #104
* Optionally skip values when logging prepared statements - #92
* Add support for client port in log messages - #90
* Add support for post-logging - #24
* Add support for host address in log message - #28
* Add support for Chronicle-Queue backend - #62
* Add metrics for filtering and logging - #72
* Add support for system timestamp in log message - #27
* Fix typo of java property for custom audit.yaml path - #59
* Build with Cassandra 3.11.4 (only flavor ecaudit_c3.11)
* Build with Cassandra 3.0.18 (only flavor ecaudit_c3.0)
* Introduce configurable log message format - #55
* Make the audit whitelist table a protected resource in Cassandra

## Version 2.0.0
* __NOTE__: This version is breaking backwards compatibility - consult detailed instructions in the [upgrade guide](UPGRADING.md)
* Add support for whitelists based on specific operations
* Make whitelist operations case insensitive
* Remove support for whitelist management in CREATE ROLE statement
* Limit whitelist management to one operation per statement
* Backport ecAudit to Cassandra 3.0.11 (only flavor ecaudit_c3.0.11)

## Version 1.0.0
* __NOTE__: This version is breaking backwards compatibility - consult detailed instructions in the [upgrade guide](UPGRADING.md)
* Fix ability to grant whitelist to all other roles as long as granter has AUTHORIZE permission on the resource (Ericsson/ecaudit#31)
* Improve logger performance with micro benchmarks

## Version 0.22.0
* Build with Cassandra 3.11.3 (only flavor ecaudit_c3.11)
* Build with Cassandra 3.0.17 (only flavor ecaudit_c3.0)
* Fix role based whitelist for non-existing ks/table (Ericsson/ecaudit#10)

## Version 0.21.0
* Public release on Maven Central

## Version 0.11.0
* Build with Cassandra 3.0.16 (only flavor ecaudit_c3.0)

## Version 0.10.0
* Add support for combined YAML file and Role Based whitelists
* Improve documentation and setup guide
* Reduced memory footprint further for prepared statements (only flavor ecaudit_c3.11)

## Version 0.9.0
* Explicitly ignoring RPC (Thrift) requests
* Reduced memory footprint of prepared statement cache mapping

## Version 0.8.0
* Replace String.replaceFirst with comma separated list at the end of prepared statment log
* Authentication operation with YAML whitelist is always audit logged
* Replace string format with string builder for better performance
* Use one single adapter instance to minimize memory footprint
* Align name of connections resource with other resources in Cassandra
* Use role based whitelist filter by default

## Version 0.7.0
* Convert whitelist cache to IResource types for improved performance
* Only obfuscate password on role management statements for improved performance
* Add support for whitelisting roles and non-root resources

## Version 0.6.0
* Fix NPE if getPrepared(statementId) returns null
* Lazy bind of values to prepared statement for improved performance of whitelisted operations
* Fix value binding to same prepared statement in a batch
* Announce whitelist filter type in system log at start-up
* Fix quote of string literals in prepared statement logs

## Version 0.5.0
* Add support for role based whitelist management

## Version 0.2.0
* First development release

