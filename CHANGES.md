# Changes

## Version 0.23.0
* Improve logger performance with micro benchmarks

## Version 0.22.0
* Build with Cassandra 3.0.17 (only in ecAudit for C* 3.0.x)
* Fix role based whitelist for non-existing ks/table (Ericsson/ecaudit#10)

## Version 0.21.0
* Public release on Maven Central

## Version 0.11.0
* Build with Cassandra 3.0.16 (only in ecAudit for C* 3.0.x)

## Version 0.10.0
* Add support for combined YAML file and Role Based whitelists
* Improve documentation and setup guide

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

