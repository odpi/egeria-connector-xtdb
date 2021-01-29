<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

[![GitHub](https://img.shields.io/github/license/odpi/egeria-connector-crux)](LICENSE) [![Azure](https://dev.azure.com/odpi/egeria/_apis/build/status/odpi.egeria-connector-crux)](https://dev.azure.com/odpi/Egeria/_build) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=egeria-connector-crux&metric=alert_status)](https://sonarcloud.io/dashboard?id=egeria-connector-crux) [![Maven Central](https://img.shields.io/maven-central/v/org.odpi.egeria/egeria-connector-crux)](https://mvnrepository.com/artifact/org.odpi.egeria/egeria-connector-crux)

# Crux Connector

[Crux](https://opencrux.com) provides an unbundled database that supports temporal graph queries.

The intention of this connector is to provide an option for a fully-compliant Egeria repository that supports metadata
history (temporal `asOfTime` queries across its entire metadata landscape).

(**NOTE**: this connector is currently a work-in-progress, and should be considered at an experimental stage at best.)

## Getting started

### TL;DR

The quick version (same for all connectors):

1. Download the latest Crux connector from: https://odpi.jfrog.io/odpi/egeria-snapshot-local/org/odpi/egeria/egeria-connector-crux/0.1-SNAPSHOT/egeria-connector-crux-0.1-SNAPSHOT-jar-with-dependencies.jar
1. Download the latest Egeria core from: https://odpi.jfrog.io/odpi/egeria-snapshot-local/org/odpi/egeria/server-chassis-spring/2.6-SNAPSHOT/server-chassis-spring-2.6-SNAPSHOT.jar
1. Rename the downloaded Egeria core file to `egeria-server-chassis-spring.jar`.
1. Download the `truststore.p12` file from: https://github.com/odpi/egeria/blob/master/truststore.p12
1. Run the following command to start Egeria from the command-line, waiting for the final line of output indicating the
    server is running and ready for configuration:
    ```bash
    $ export STRICT_SSL=false
    $ java -Dloader.path=. -jar egeria-server-chassis-spring.jar
     ODPi Egeria
        ____   __  ___ ___    ______   _____                                 ____   _         _     ___
       / __ \ /  |/  //   |  / ____/  / ___/ ___   ____ _   __ ___   ____   / _  \ / / __    / /  / _ /__   ____ _  _
      / / / // /|_/ // /| | / / __    \__ \ / _ \ / __/| | / // _ \ / __/  / /_/ // //   |  / _\ / /_ /  | /  _// || |
     / /_/ // /  / // ___ |/ /_/ /   ___/ //  __// /   | |/ //  __// /    /  __ // // /  \ / /_ /  _// / // /  / / / /
     \____//_/  /_//_/  |_|\____/   /____/ \___//_/    |___/ \___//_/    /_/    /_/ \__/\//___//_/   \__//_/  /_/ /_/
    
     :: Powered by Spring Boot (v2.2.2.RELEASE) ::
    
    
    No OMAG servers listed in startup configuration
    Thu Jan 02 11:30:10 GMT 2020 OMAG server platform ready for more configuration
    ```
1. Follow the detailed instructions for configuring the connector (below).

## Configure the connector

### Detailed steps for configuring the Crux connector

You will need to configure the OMAG Server Platform as follows (order is important) to make use of the Crux connector.
For example payloads and endpoints, see the [Postman samples](samples).

1. Configure your event bus for Egeria, by POSTing a payload like the following (replace the `localhost:9092` with the
   hostname and port number where your Kafka bus is running, and assuming you are running the OMAG Server Platform
   locally at its default port of `9443`):

    ```json
    {
        "producer": {
            "bootstrap.servers":"localhost:9092"
        },
        "consumer": {
            "bootstrap.servers":"localhost:9092"
        }
    }
    ```

   to:

    ```
    POST https://localhost:9443/open-metadata/admin-services/users/admin/servers/myserver/event-bus?connectorProvider=org.odpi.openmetadata.adapters.eventbus.topic.kafka.KafkaOpenMetadataTopicProvider&topicURLRoot=OMRSTopic
    ```

1. Configure the cohort, by POSTing something like the following:

    ```
    POST https://localhost:9443/open-metadata/admin-services/users/admin/servers/myserver/cohorts/mycohort
    ```

1. Configure the Crux connector, by POSTing a payload like the following:

    ```json
    {
        "class": "Connection",
        "connectorType": {
            "class": "ConnectorType",
            "connectorProviderClassName": "org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider"
        }
    }
    ```

    to:

    ```
    POST https://localhost:9443/open-metadata/admin-services/users/admin/servers/myserver/local-repository/mode/repository-proxy/connection
    ```

    Note that you must provide the `connectorProvider` parameter, set to the name of the Crux
    connectorProvider class (value as given above).

1. The connector should now be configured, and you should now be able
   to start the instance by POSTing something like the following:

   ```
   POST https://localhost:9443/open-metadata/admin-services/users/admin/servers/myserver/instance
   ```

After following these instructions, your Crux instance will be participating in the Egeria cohort.

## Detailed instructions

### Obtain the connectors

You can either download the latest released or snapshot version of the connector directly from ODPi, or build the
connector yourself. In both cases, once you have the jar file for the connector
(`egeria-connector-crux-VERSION-jar-with-dependencies.jar`) this needs to be copied to a
location where it can be run alongside the OMAG Server Platform from Egeria core itself. For example, this could be
placing the file into the `/lib` directory as `/lib/egeria-connector-crux-VERSION-jar-with-dependencies.jar`.

#### Download from ODPi

To download a pre-built version of the connector, use either of the following URLs (depending on whether you want an
officially-released version or the latest snapshot):

- Release: https://odpi.jfrog.io/odpi/egeria-release-local/org/odpi/egeria/egeria-connector-crux/0.1/egeria-connector-crux-0.1-jar-with-dependencies.jar
- Snapshot: https://odpi.jfrog.io/odpi/egeria-snapshot-local/org/odpi/egeria/egeria-connector-crux/0.1-SNAPSHOT/egeria-connector-crux-0.1-SNAPSHOT-jar-with-dependencies.jar

#### Building the connectors yourself

Alternatively, you can build the connector yourself. To do this, you'll need to first clone this repository and then
build through Maven using `mvn clean install`. After building, the connector is available as:

```text
target/egeria-connector-crux-VERSION-jar-with-dependencies.jar
```

### Configure security

There are [multiple options to configure the security of your environment](docs/security/README.md) for this connector,
but this must be done prior to starting up the connector itself (step below).

If you simply want to test things out, and are not concerned about security, the simplest (but most insecure) option
is to set the environment variable `STRICT_SSL` to `false` using something like the following prior to starting
up the OMAG Server Platform:

```bash
export STRICT_SSL=false
```

Note that this will disable all certificate validation for SSL connections made between Egeria and your Crux
connector, so is inherently insecure.

Note that in any case, having a `truststore.p12` file available to the server chassis is required -- the simplest is to
ensure that Egeria's own (https://github.com/odpi/egeria/blob/master/truststore.p12) is placed in the directory in which
you are running the server chassis.

### Startup the OMAG Server Platform

You can startup the OMAG Server Platform with this connector ready-to-be-configured by running the following
(this example assumes you've placed the connector jar file in the `/lib` directory, if you are using a different
location simply change the `-Dloader.path=` to point to the location you have used):

```bash
$ java -Dloader.path=/lib -jar server-chassis-spring-VERSION.jar
```

(This command will startup the OMAG Server Platform, including all libraries
in the `/lib` directory as part of the classpath of the OMAG Server Platform.)

----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.
