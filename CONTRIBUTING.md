# Contribute


## Flavours

Since there are different flavours of ecAudit for different Cassandra versions,
new patch sets should be created on the oldest maintained release branch.
Once incorporated on the release branch the patch set will be merged forward
on to more resent release branches (if any) and then finally into the master branch.

This way feature compatibility will be maintained on the different flavours of ecAudit.

At the moment the oldest maintained release branch is ```release/c3.0```
which is tracking the latest release of Cassandra 3.0.x.
The ```master``` branch is tracking the latest release of Cassandra 3.11.x.


## Code Style

Code style settings are included in the repository for Intellij IDEA.
They should be picked up automatically when you import the Maven project at the root of the repository.
The intention is to use the same settings as those of Apache Cassandra.


## Build & Test

If you're looking to contribute to ecAudit you should sign up on [Travis](https://travis-ci.org/) and [Coveralls](https://coveralls.io/),
and enable builds and reports on your own fork of ecAudit.
This allow you to verify your patch before you create a pull request.

You can also execute Mutation Tests on your local machine based on the [Pitest](http://pitest.org/) framework.
To run them, execute:
```bash
mvn clean compile test org.pitest:pitest-maven:mutationCoverage
```

The report will be available in the ```target/pit-reports/``` directory.
