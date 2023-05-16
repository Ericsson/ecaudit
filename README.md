# ecAudit

[![tests](https://github.com/Ericsson/ecaudit/actions/workflows/test.yml/badge.svg?branch=master)](https://github.com/Ericsson/ecaudit/actions/workflows/test.yml?query=branch%3Amaster)
[![coverage](https://codecov.io/gh/ericsson/ecaudit/branch/master/graph/badge.svg?token=f42z31Yqr3)](https://codecov.io/gh/Ericsson/ecaudit/branch/master)

With ecAudit you get auditing and query logger functionality for Apache Cassandra 3.0, 3.11 and 4.0.

Features include:
* Detailed audit records for CQL operations and login attempts
* Customizable audit record format
* Obfuscation of sensitive password information
* Powerful filtering with centralized or local whitelists
* Logger backends with SLF4J/Logback or Chronicle

Example of audit records created by ecAudit:
```
15:42:41.644 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[1, '1', 'valid']'
15:42:41.646 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'SELECT * FROM ecks.ectbl WHERE partk = ?[1]'
15:42:41.650 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'DELETE FROM ecks.ectbl WHERE partk = ?[1]'
15:42:41.651 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'INSERT INTO ecks.ectbl (partk, clustk, value) VALUES (?, ?, ?)[2, '2', 'valid']'
15:42:41.653 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'SELECT * FROM ecks.ectbl WHERE partk = ?[2]'
15:42:41.655 - client:'127.0.0.1'|user:'cassandra'|status:'ATTEMPT'|operation:'DELETE FROM ecks.ectbl WHERE partk = ?[2]'
```

Checkout the detailed [description](doc/description.md) for a more comprehensive list of features, limitations and operational impact.


## Getting Started

ecAudit integrates with Apache Cassandra using its existing plug-in points.


### Download

Official releases of ecAudit can be downloaded from Maven Central.
Get the ecAudit flavor for your Cassandra version.

[![ecAudit for Cassandra 4.1.<latest>](https://img.shields.io/maven-central/v/com.ericsson.bss.cassandra.ecaudit/ecaudit_c4.1.svg?label=ecAudit%20for%20Cassandra%204.1.<latest>)](https://search.maven.org/search?q=g:%22com.ericsson.bss.cassandra.ecaudit%22%20AND%20a:%22ecaudit_c4.1%22)
[![ecAudit for Cassandra 4.0.<latest>](https://img.shields.io/maven-central/v/com.ericsson.bss.cassandra.ecaudit/ecaudit_c4.0.svg?label=ecAudit%20for%20Cassandra%204.0.<latest>)](https://search.maven.org/search?q=g:%22com.ericsson.bss.cassandra.ecaudit%22%20AND%20a:%22ecaudit_c4.0%22)
[![ecAudit for Cassandra 3.11.<latest>](https://img.shields.io/maven-central/v/com.ericsson.bss.cassandra.ecaudit/ecaudit_c3.11.svg?label=ecAudit%20for%20Cassandra%203.11.<latest>)](https://search.maven.org/search?q=g:%22com.ericsson.bss.cassandra.ecaudit%22%20AND%20a:%22ecaudit_c3.11%22)

For a detailed description of compatible Cassandra versions, refer to the [Cassandra Compatibility Matrix](doc/cassandra_compatibility.md).


#### Maintenance

The following flavors of ecAudit are in maintenance mode and will get critical fixes, but no new features.

[![ecAudit for Cassandra 3.0.<latest>](https://img.shields.io/maven-central/v/com.ericsson.bss.cassandra.ecaudit/ecaudit_c3.0.svg?label=ecAudit%20for%20Cassandra%203.0.<latest>)](https://search.maven.org/search?q=g:%22com.ericsson.bss.cassandra.ecaudit%22%20AND%20a:%22ecaudit_c3.0%22)


#### Archive

The following flavors of ecAudit are no longer maintained.

[![ecAudit for Cassandra 3.0.11](https://img.shields.io/maven-central/v/com.ericsson.bss.cassandra.ecaudit/ecaudit_c3.0.11.svg?label=ecAudit%20for%20Cassandra%203.0.11)](https://search.maven.org/search?q=g:%22com.ericsson.bss.cassandra.ecaudit%22%20AND%20a:%22ecaudit_c3.0.11%22)
[![ecAudit for Cassandra 2.2.<latest>](https://img.shields.io/maven-central/v/com.ericsson.bss.cassandra.ecaudit/ecaudit_c2.2.svg?label=ecAudit%20for%20Cassandra%202.2)](https://search.maven.org/search?q=g:%22com.ericsson.bss.cassandra.ecaudit%22%20AND%20a:%22ecaudit_c2.2%22)


### Setup

Install and configure ecAudit using the setup guide for your Cassandra version.

* [ecAudit Setup Guide for Cassandra 4.1.\<latest>](https://github.com/Ericsson/ecaudit/blob/master/doc/setup.md)
* [ecAudit Setup Guide for Cassandra 4.0.\<latest>](https://github.com/Ericsson/ecaudit/blob/release/c4.0/doc/setup.md)
* [ecAudit Setup Guide for Cassandra 3.11.\<latest>](https://github.com/Ericsson/ecaudit/blob/release/c3.11/doc/setup.md)
* [ecAudit Setup Guide for Cassandra 3.0.\<latest>](https://github.com/Ericsson/ecaudit/blob/release/c3.0/doc/setup.md)


### Upgrade

Upgrade ecAudit using then upgrade guide for your Cassandra version

* [ecAudit Upgrade Guide for Cassandra 4.1.\<latest>](https://github.com/Ericsson/ecaudit/blob/master/UPGRADING.md)
* [ecAudit Upgrade Guide for Cassandra 4.0.\<latest>](https://github.com/Ericsson/ecaudit/blob/release/c4.0/UPGRADING.md)
* [ecAudit Upgrade Guide for Cassandra 3.11.\<latest>](https://github.com/Ericsson/ecaudit/blob/release/c3.11/UPGRADING.md)
* [ecAudit Upgrade Guide for Cassandra 3.0.\<latest>](https://github.com/Ericsson/ecaudit/blob/release/c3.0/UPGRADING.md)


## Issues & Contributions

Report an issue if you're having trouble to use ecAudit or have an idea for an improvement.

Want to contribute to ecAudit?
Check out our [contribution guide](CONTRIBUTING.md).


## Credits

The following developers have contributed to the ecAudit project:

* Per Otterström
* Tobias Eriksson
* Laxmikant Upadhyay
* Anuj Wadhera
* Marcus Olsson
* Ted Petersson
* Pushpendra Rajpoot
* Tommy Stendahl


## License

Copyright 2018-22 Telefonaktiebolaget LM Ericsson

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied.
See the License for the specific language governing permissions and limitations under the License.
