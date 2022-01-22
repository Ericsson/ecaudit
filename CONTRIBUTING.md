# Contributing to ecAudit

We're really glad that you want to contribute to ecAudit!

All kinds of improvements are welcome.
Be it bug fixes, improved automation, new features, better documentation or new tests cases.
We're accepting contributions as Pull Requests on GitHub.
Before you dig into the code, consider to create a ticket on the [issue tracker](https://github.com/Ericsson/ecaudit/issues)
and discuss your improvement with the project maintainers.

Below you'll find a few things to be aware of when you're working with this project.


## Flavors

Since there are different flavors of ecAudit for different Cassandra versions,
new feature patches should be created on the oldest supported release branch.
Critical fixes should be created on the older maintained release branch.
Once incorporated on the release branch the patch set will be merged forward
on to more resent release branches (if any) and then finally into the master branch.

This way feature compatibility will be maintained on the different flavors of ecAudit.

At the moment the oldest maintained release branch is ```release/c3.0```
which is tracking the latest release of Cassandra 3.0.x.
Then comes ```release/3.11```
which is tracking the latest release of Cassandra 3.11.x.
The ```master``` branch is tracking the latest release of Cassandra 4.0.x.

Pull Requests with new features should typically target ```release/c3.11```.
Merge order is then ```PR``` -> ```release/c3.11``` -> ```master```

Pull Requests with critical fixes should typically target ```release/c3.0```.
Merge order is then ```PR``` -> ```release/c3.0``` -> ```release/c3.11``` -> ```master```

It is encouraged to encapsulate differences between flavors in Flavor Adapters.
This simplifies maintenance and merging between flavors.
Examples are the FieldFilterFlavorAdapter and the CqlLiteralFlavorAdapter.


## Design Environment

With Intellij IDEA you'll get a good setup, more or less, out of the box.
From a fresh clone of the repository, navigate to the project root and do ```idea .&```
This way Intellij will pick up some project settings which are included in git repository.
The rest will be generated as need by Intellij.

It is of course possible to set up the project with other IDEs as well,
but you'll have to figure it out on your own.

In general, we're adopting the same code style and project settings as Apache Cassandra itself.


## Build & Test

The project is integrated with GitHub Actions and [Codecov](https://app.codecov.io/gh).
This allows you to verify your patch before you create a pull request.

The project is using Maven for build, test and deployment.
The repository is divided in a few different Maven modules that make out the ecAudit plug-in and tools.
Modules named ```integration-test-*``` contain integration tests which are executed with the ```maven-failsafe-plugin```.

We're using unit tests, integration tests and mutation tests to make sure that we're testing the right behavior of ecAudit.
On top of this we verify with PMD to get a consistent, maintainable and predictable code base.
We're only using Javadoc sparsely since ecAudit don't have a public Java API.
In those cases where Javadoc is used, it should be valid and complete.
The build cycle is also verifying that all source files have a valid license header.

At the end of this document you'll find a shortlist of useful commands to trigger the different test suites on your local machine. 


## CCM

If you have [CCM](https://github.com/riptano/ccm) installed on your workstation you can easily verify parts of ecAudit in a local test cluster.
Prepare by building the ecAudit project with maven and initialize a ccm cluster using a matching Cassandra version.

To install ecAudit with SLF4J backend into the ccm cluster, execute:
```bash
./bin/configure_ccm_audit_slf4j.sh
```

To install ecAudit with Chronicle backend into the ccm cluster, execute:
```bash
./bin/configure_ccm_audit_chronicle.sh
```

Now you can operate your ccm cluster as always and try out ecAudit.
Remember to provide the standard credentials when you log in:
```bash
ccm node1 cqlsh -u cassandra -p cassandra
```


## Useful commands

To compile and do static code analysis, execute;
```bash
mvn compile
```

To build jar files while skipping tests, execute;
```bash
mvn package -DskipTests
```

To run unit tests; execute:
```bash
mvn test
```

To run integration tests; execute:
```bash
mvn test-compile failsafe:integration-test failsafe:verify
```

To run unit tests and integration tests; execute:
```bash
mvn verify
```

You can also execute Mutation Tests on your local machine based on the [Pitest](http://pitest.org/) framework.
The report will be available in the ```target/pit-reports/``` directory of each module.
To run them; execute:
```bash
mvn compile test org.pitest:pitest-maven:mutationCoverage
```
