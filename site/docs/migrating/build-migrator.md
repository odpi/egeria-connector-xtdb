<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Build migrator

You may also want to build the migrator yourself, particularly if you are extending it or helping
to develop new capabilities within it.

## Clone

Begin by cloning this repository to your build machine.

```shell
$ git clone https://github.com/odpi/egeria-connector-crux.git
```

??? success "Clone output"
    ```text
    Cloning into 'egeria-connector-crux'...
    remote: Enumerating objects: 531, done.
    remote: Counting objects: 100% (531/531), done.
    remote: Compressing objects: 100% (193/193), done.
    remote: Total 531 (delta 168), reused 446 (delta 101), pack-reused 0
    Receiving objects: 100% (531/531), 162.39 KiB | 1.01 MiB/s, done.
    Resolving deltas: 100% (168/168), done.
    ```

## Build

Run the build through Maven.

!!! attention "Minimum version requirements"
    You will need to have at least Java version 11 installed as well as Apache Maven (at least version 3.6).

```shell
mvn clean install
```

??? success "Build output"
    ```text
    [INFO] Scanning for projects...
    [INFO] ------------------------------------------------------------------------
    [INFO] Reactor Build Order:
    [INFO]
    [INFO] Overall Crux Connector Module                                      [pom]
    [INFO] Egeria Connector for Crux                                          [jar]
    [INFO] Migration utilities for Crux Connector                             [jar]
    [INFO]
    [INFO] ------------< org.odpi.egeria:egeria-connector-crux-module >------------
    [INFO] Building Overall Crux Connector Module 2.9-SNAPSHOT                [1/3]
    [INFO] --------------------------------[ pom ]---------------------------------
    [INFO]
    [INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ egeria-connector-crux-module ---
    [INFO] Deleting .../egeria-connector-crux/target
    [INFO]
    [INFO] --- maven-enforcer-plugin:3.0.0-M3:enforce (enforce-versions) @ egeria-connector-crux-module ---
    [INFO]
    [INFO] --- apache-rat-plugin:0.13:check (rat-check) @ egeria-connector-crux-module ---
    [INFO] Added 3 additional default licenses.
    [INFO] Enabled default license matchers.
    [INFO] Added 2 custom approved licenses.
    [INFO] Will parse SCM ignores for exclusions...
    [INFO] Parsing exclusions from .../egeria-connector-crux/.gitignore
    [INFO] Finished adding exclusions from SCM ignore files.
    [INFO] 103 implicit excludes (use -debug for more details).
    [INFO] 30 explicit excludes (use -debug for more details).
    [INFO] 86 resources included (use -debug for more details)
    [INFO] Rat check: Summary over all files. Unapproved: 0, unknown: 0, generated: 0, approved: 54 licenses.
    [INFO]
    [INFO] --- jacoco-maven-plugin:0.8.6:prepare-agent (agent) @ egeria-connector-crux-module ---
    [INFO] argLine set to -javaagent:/Users/cgrote/.m2/repository/org/jacoco/org.jacoco.agent/0.8.6/org.jacoco.agent-0.8.6-runtime.jar=destfile=.../egeria-connector-crux/target/jacoco.exec
    [INFO]
    [INFO] --- maven-source-plugin:3.2.1:jar-no-fork (attach-sources) @ egeria-connector-crux-module ---
    [INFO]
    [INFO] --- maven-javadoc-plugin:3.2.0:jar (attach-javadocs) @ egeria-connector-crux-module ---
    [INFO] Not executing Javadoc as the project is not a Java classpath-capable package
    [INFO]
    [INFO] --- jacoco-maven-plugin:0.8.6:report (report) @ egeria-connector-crux-module ---
    [INFO] Skipping JaCoCo execution due to missing execution data file.
    [INFO]
    [INFO] --- maven-dependency-plugin:3.1.2:analyze-only (analyze) @ egeria-connector-crux-module ---
    [INFO] Skipping pom project
    [INFO]
    [INFO] --- maven-install-plugin:2.4:install (default-install) @ egeria-connector-crux-module ---
    [INFO] Installing .../egeria-connector-crux/pom.xml to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux-module/2.9-SNAPSHOT/egeria-connector-crux-module-2.9-SNAPSHOT.pom
    [INFO]
    [INFO] ---------------< org.odpi.egeria:egeria-connector-crux >----------------
    [INFO] Building Egeria Connector for Crux 2.9-SNAPSHOT                    [2/3]
    [INFO] --------------------------------[ jar ]---------------------------------
    [INFO]
    [INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ egeria-connector-crux ---
    [INFO] Deleting .../egeria-connector-crux/connector/target
    [INFO]
    [INFO] --- maven-enforcer-plugin:3.0.0-M3:enforce (enforce-versions) @ egeria-connector-crux ---
    [INFO]
    [INFO] --- apache-rat-plugin:0.13:check (rat-check) @ egeria-connector-crux ---
    [INFO] Added 3 additional default licenses.
    [INFO] Enabled default license matchers.
    [INFO] Added 2 custom approved licenses.
    [INFO] Will parse SCM ignores for exclusions...
    [INFO] Finished adding exclusions from SCM ignore files.
    [INFO] 62 implicit excludes (use -debug for more details).
    [INFO] 30 explicit excludes (use -debug for more details).
    [INFO] 29 resources included (use -debug for more details)
    [INFO] Rat check: Summary over all files. Unapproved: 0, unknown: 0, generated: 0, approved: 29 licenses.
    [INFO]
    [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ egeria-connector-crux ---
    [INFO] Using 'UTF-8' encoding to copy filtered resources.
    [INFO] Copying 1 resource
    [INFO]
    [INFO] --- maven-compiler-plugin:3.8.1:compile (default-compile) @ egeria-connector-crux ---
    [INFO] Changes detected - recompiling the module!
    [INFO] Compiling 26 source files to .../egeria-connector-crux/connector/target/classes
    [INFO]
    [INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ egeria-connector-crux ---
    [INFO] Using 'UTF-8' encoding to copy filtered resources.
    [INFO] Copying 1 resource
    [INFO]
    [INFO] --- jacoco-maven-plugin:0.8.6:prepare-agent (agent) @ egeria-connector-crux ---
    [INFO] argLine set to -javaagent:/Users/cgrote/.m2/repository/org/jacoco/org.jacoco.agent/0.8.6/org.jacoco.agent-0.8.6-runtime.jar=destfile=.../egeria-connector-crux/connector/target/jacoco.exec
    [INFO]
    [INFO] --- maven-compiler-plugin:3.8.1:testCompile (default-testCompile) @ egeria-connector-crux ---
    [INFO] Changes detected - recompiling the module!
    [INFO] Compiling 1 source file to .../egeria-connector-crux/connector/target/test-classes
    [INFO]
    [INFO] --- maven-surefire-plugin:3.0.0-M5:test (default-test) @ egeria-connector-crux ---
    [INFO]
    [INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ egeria-connector-crux ---
    [INFO] Building jar: .../egeria-connector-crux/connector/target/egeria-connector-crux-2.9-SNAPSHOT.jar
    [INFO]
    [INFO] --- maven-source-plugin:3.2.1:jar-no-fork (attach-sources) @ egeria-connector-crux ---
    [INFO] Building jar: .../egeria-connector-crux/connector/target/egeria-connector-crux-2.9-SNAPSHOT-sources.jar
    [INFO]
    [INFO] --- maven-javadoc-plugin:3.2.0:jar (attach-javadocs) @ egeria-connector-crux ---
    [INFO] No previous run data found, generating javadoc.
    [INFO] Building jar: .../egeria-connector-crux/connector/target/egeria-connector-crux-2.9-SNAPSHOT-javadoc.jar
    [INFO]
    [INFO] --- maven-assembly-plugin:3.3.0:single (default) @ egeria-connector-crux ---
    [INFO] Building jar: .../egeria-connector-crux/connector/target/egeria-connector-crux-2.9-SNAPSHOT-jar-with-dependencies.jar
    [INFO]
    [INFO] --- jacoco-maven-plugin:0.8.6:report (report) @ egeria-connector-crux ---
    [INFO] Skipping JaCoCo execution due to missing execution data file.
    [INFO]
    [INFO] --- maven-dependency-plugin:3.1.2:analyze-only (analyze) @ egeria-connector-crux ---
    [INFO] No dependency problems found
    [INFO]
    [INFO] --- maven-install-plugin:2.4:install (default-install) @ egeria-connector-crux ---
    [INFO] Installing .../egeria-connector-crux/connector/target/egeria-connector-crux-2.9-SNAPSHOT.jar to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux/2.9-SNAPSHOT/egeria-connector-crux-2.9-SNAPSHOT.jar
    [INFO] Installing .../egeria-connector-crux/connector/pom.xml to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux/2.9-SNAPSHOT/egeria-connector-crux-2.9-SNAPSHOT.pom
    [INFO] Installing .../egeria-connector-crux/connector/target/egeria-connector-crux-2.9-SNAPSHOT-sources.jar to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux/2.9-SNAPSHOT/egeria-connector-crux-2.9-SNAPSHOT-sources.jar
    [INFO] Installing .../egeria-connector-crux/connector/target/egeria-connector-crux-2.9-SNAPSHOT-javadoc.jar to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux/2.9-SNAPSHOT/egeria-connector-crux-2.9-SNAPSHOT-javadoc.jar
    [INFO] Installing .../egeria-connector-crux/connector/target/egeria-connector-crux-2.9-SNAPSHOT-jar-with-dependencies.jar to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux/2.9-SNAPSHOT/egeria-connector-crux-2.9-SNAPSHOT-jar-with-dependencies.jar
    [INFO]
    [INFO] -----------< org.odpi.egeria:egeria-connector-crux-migrator >-----------
    [INFO] Building Migration utilities for Crux Connector 2.9-SNAPSHOT       [3/3]
    [INFO] --------------------------------[ jar ]---------------------------------
    [INFO]
    [INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ egeria-connector-crux-migrator ---
    [INFO] Deleting .../egeria-connector-crux/migrator/target
    [INFO]
    [INFO] --- maven-enforcer-plugin:3.0.0-M3:enforce (enforce-versions) @ egeria-connector-crux-migrator ---
    [INFO]
    [INFO] --- apache-rat-plugin:0.13:check (rat-check) @ egeria-connector-crux-migrator ---
    [INFO] Added 3 additional default licenses.
    [INFO] Enabled default license matchers.
    [INFO] Added 2 custom approved licenses.
    [INFO] Will parse SCM ignores for exclusions...
    [INFO] Finished adding exclusions from SCM ignore files.
    [INFO] 62 implicit excludes (use -debug for more details).
    [INFO] 30 explicit excludes (use -debug for more details).
    [INFO] 4 resources included (use -debug for more details)
    [INFO] Rat check: Summary over all files. Unapproved: 0, unknown: 0, generated: 0, approved: 4 licenses.
    [INFO]
    [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ egeria-connector-crux-migrator ---
    [INFO] Using 'UTF-8' encoding to copy filtered resources.
    [INFO] Copying 0 resource
    [INFO]
    [INFO] --- maven-compiler-plugin:3.8.1:compile (default-compile) @ egeria-connector-crux-migrator ---
    [INFO] Changes detected - recompiling the module!
    [INFO] Compiling 3 source files to .../egeria-connector-crux/migrator/target/classes
    [INFO]
    [INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ egeria-connector-crux-migrator ---
    [INFO] Using 'UTF-8' encoding to copy filtered resources.
    [INFO] skip non existing resourceDirectory .../egeria-connector-crux/migrator/src/test/resources
    [INFO]
    [INFO] --- jacoco-maven-plugin:0.8.6:prepare-agent (agent) @ egeria-connector-crux-migrator ---
    [INFO] argLine set to -javaagent:/Users/cgrote/.m2/repository/org/jacoco/org.jacoco.agent/0.8.6/org.jacoco.agent-0.8.6-runtime.jar=destfile=.../egeria-connector-crux/migrator/target/jacoco.exec
    [INFO]
    [INFO] --- maven-compiler-plugin:3.8.1:testCompile (default-testCompile) @ egeria-connector-crux-migrator ---
    [INFO] Changes detected - recompiling the module!
    [INFO]
    [INFO] --- maven-surefire-plugin:3.0.0-M5:test (default-test) @ egeria-connector-crux-migrator ---
    [INFO]
    [INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ egeria-connector-crux-migrator ---
    [INFO] Building jar: .../egeria-connector-crux/migrator/target/egeria-connector-crux-migrator-2.9-SNAPSHOT.jar
    [INFO]
    [INFO] --- maven-source-plugin:3.2.1:jar-no-fork (attach-sources) @ egeria-connector-crux-migrator ---
    [INFO] Building jar: .../egeria-connector-crux/migrator/target/egeria-connector-crux-migrator-2.9-SNAPSHOT-sources.jar
    [INFO]
    [INFO] --- maven-javadoc-plugin:3.2.0:jar (attach-javadocs) @ egeria-connector-crux-migrator ---
    [INFO] No previous run data found, generating javadoc.
    [INFO] Building jar: .../egeria-connector-crux/migrator/target/egeria-connector-crux-migrator-2.9-SNAPSHOT-javadoc.jar
    [INFO]
    [INFO] --- maven-assembly-plugin:3.3.0:single (default) @ egeria-connector-crux-migrator ---
    [INFO] Building jar: .../egeria-connector-crux/migrator/target/egeria-connector-crux-migrator-2.9-SNAPSHOT-jar-with-dependencies.jar
    [INFO]
    [INFO] --- jacoco-maven-plugin:0.8.6:report (report) @ egeria-connector-crux-migrator ---
    [INFO] Skipping JaCoCo execution due to missing execution data file.
    [INFO]
    [INFO] --- maven-dependency-plugin:3.1.2:analyze-only (analyze) @ egeria-connector-crux-migrator ---
    [INFO] No dependency problems found
    [INFO]
    [INFO] --- maven-install-plugin:2.4:install (default-install) @ egeria-connector-crux-migrator ---
    [INFO] Installing .../egeria-connector-crux/migrator/target/egeria-connector-crux-migrator-2.9-SNAPSHOT.jar to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux-migrator/2.9-SNAPSHOT/egeria-connector-crux-migrator-2.9-SNAPSHOT.jar
    [INFO] Installing .../egeria-connector-crux/migrator/pom.xml to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux-migrator/2.9-SNAPSHOT/egeria-connector-crux-migrator-2.9-SNAPSHOT.pom
    [INFO] Installing .../egeria-connector-crux/migrator/target/egeria-connector-crux-migrator-2.9-SNAPSHOT-sources.jar to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux-migrator/2.9-SNAPSHOT/egeria-connector-crux-migrator-2.9-SNAPSHOT-sources.jar
    [INFO] Installing .../egeria-connector-crux/migrator/target/egeria-connector-crux-migrator-2.9-SNAPSHOT-javadoc.jar to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux-migrator/2.9-SNAPSHOT/egeria-connector-crux-migrator-2.9-SNAPSHOT-javadoc.jar
    [INFO] Installing .../egeria-connector-crux/migrator/target/egeria-connector-crux-migrator-2.9-SNAPSHOT-jar-with-dependencies.jar to /Users/cgrote/.m2/repository/org/odpi/egeria/egeria-connector-crux-migrator/2.9-SNAPSHOT/egeria-connector-crux-migrator-2.9-SNAPSHOT-jar-with-dependencies.jar
    [INFO] ------------------------------------------------------------------------
    [INFO] Reactor Summary for Overall Crux Connector Module 2.9-SNAPSHOT:
    [INFO]
    [INFO] Overall Crux Connector Module ...................... SUCCESS [  3.187 s]
    [INFO] Egeria Connector for Crux .......................... SUCCESS [  8.959 s]
    [INFO] Migration utilities for Crux Connector ............. SUCCESS [ 18.382 s]
    [INFO] ------------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time:  30.652 s
    [INFO] Finished at: 2021-04-19T13:26:54+01:00
    [INFO] ------------------------------------------------------------------------
    ```

The build creates the file `.../egeria-connector-crux-migrator/target/egeria-connector-crux-migrator-{version}-jar-with-dependencies.jar`.
This is the migrator, which you can use just as if you had downloaded it in the first step of the Migration instructions.

You can now proceed with the migration [from step 2](../run-migration/#2-configure-repository) onwards using this build migrator.
