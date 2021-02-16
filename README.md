<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

<!-- [![Quality](https://sonarcloud.io/api/project_badges/measure?project=egeria-connector-crux&metric=alert_status)](https://sonarcloud.io/dashboard?id=egeria-connector-crux) -->

[![License](https://img.shields.io/github/license/odpi/egeria-connector-crux)](LICENSE)
[![Build](https://github.com/odpi/egeria-connector-crux/workflows/Maven%20Package/badge.svg)](https://github.com/odpi/egeria-connector-crux/actions?query=workflow%3A%22Maven+Package%22)
[![Snapshot](https://img.shields.io/maven-metadata/v?label=development&metadataUrl=https%3A%2F%2Ftoken%3A82d3aaedcfbb070176d9b30df29ea28768b90691%40maven.pkg.github.com%2Fodpi%2Fegeria-connector-crux%2Forg%2Fodpi%2Fegeria%2Fegeria-connector-crux%2Fmaven-metadata.xml)](https://github.com/odpi/egeria-connector-crux/packages/617324)
[![Release](https://img.shields.io/maven-central/v/org.odpi.egeria/egeria-connector-crux)](https://mvnrepository.com/artifact/org.odpi.egeria/egeria-connector-crux)

# Crux Connector

[Crux](https://opencrux.com) provides an unbundled database that supports temporal graph queries.

The intention of this connector is to provide an option for a fully-compliant Egeria repository that supports metadata
history (temporal `asOfTime` queries across its entire metadata landscape).

The connector is currently undergoing heavier testing to verify it functions and performs as intended. Feel free to use
it in "experimentation" mode, for now.

## Getting started

### TL;DR

The quick version (same for all connectors):

1. Download the latest Crux connector from the [Packages link](https://github.com/orgs/odpi/packages?repo_name=egeria-connector-crux) of the GitHub repo.
1. Download the latest Egeria core from: https://odpi.jfrog.io/odpi/egeria-snapshot-local/org/odpi/egeria/server-chassis-spring/2.7-SNAPSHOT/server-chassis-spring-2.7-SNAPSHOT.jar
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
    connectorProvider class (value as given above), and that this particular configuration will only start an in-memory
    version of the connector. As there are a number of options for persistence, see that additional configuration detail
    in the more detailed instructions below.

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
- Snapshot: https://odpi.jfrog.io/odpi/egeria-snapshot-local/org/odpi/egeria/egeria-connector-crux/1.0-SNAPSHOT/egeria-connector-crux-1.0-SNAPSHOT-jar-with-dependencies.jar

#### Building the connectors yourself

Alternatively, you can build the connector yourself. To do this, you'll need to first clone this repository and then
build through Maven using `mvn clean install`. After building, the connector is available as:

```text
target/egeria-connector-crux-VERSION-jar-with-dependencies.jar
```

### Configure persistence

Crux itself provides [a variety of persistence options](https://opencrux.com/reference/configuration.html), each of
which can be configured for the connector. Below is a non-exhaustive list of some typical examples. Note that if you do
not specify any configuration to the connector, it will only startup an in-memory node by default. While such a
configuration is useful for quick testing and experimentation, it will not persist any information and therefore all
information will be lost when the instance is stopped or restarted.

To ensure that your repository retains its information, make sure you send in a configuration for one of the persistent
storage options that Crux provides. There are generally 3 different persistent stores to consider:

- query indices: used by Crux to quickly and efficiently query for information, and are therefore generally
  are persisted through key-value stores that leverage in-memory caches (eg. RocksDB, LMDB)
- document store: used by Crux to store the "master" copy of persisted data, supporting eviction for compliance, and
  therefore typically leveraging robust persistent storage (eg. data stores via JDBC like PostgreSQL or object storage
  like S3)
- transaction log: used by Crux to store an immutable log of hashed data, therefore leveraging fast streaming
  technologies (eg. Kafka)

Note that you can choose a combination of components, that is, a different persistence back-end for each of these
persistent stores.

The specific jars necessary for leveraging these persistence mechanisms are _not_ currently distributed with the jar
file for the connector: you will need to ensure that you obtain these independently (eg. via [https://clojars.org](https://clojars.org/search?q=group-id%3Ajuxt+AND+artifact-id%3Acrux-*))
and place them into the same location as the connector itself (ie. the `LOADER_PATH` location, such as `/lib` in the examples above).

(For a direct download, use a URL like: https://clojars.org/repo/juxt/crux-rocksdb/21.01-1.14.0-beta/crux-rocksdb-21.01-1.14.0-beta.jar)

You should send through the configuration using the JSON format listed on Crux's site, directly within the
`configurationProperties` map of the connector. As an example, the following will configure the Crux repository
with RocksDB as both the index store and document store (sharing an LRU block cache of 1 GB), and Kafka as the
transaction log (using the topic `crux-tx-log` and a poll-time of 1 second):

```json
{
  "class": "Connection",
  "connectorType": {
    "class": "ConnectorType",
    "connectorProviderClassName": "org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider"
  },
  "configurationProperties": {
    "crux.rocksdb/block-cache": {
      "crux/module": "crux.rocksdb/->lru-block-cache",
      "cache-size": 1073741824
    },
    "crux/index-store": {
      "kv-store": {
        "crux/module": "crux.rocksdb/->kv-store",
        "db-dir": "data/servers/crux/rdb-index",
        "block-cache": "crux.rocksdb/block-cache"
      }
    },
    "crux/document-store": {
      "kv-store": {
        "crux/module": "crux.rocksdb/->kv-store",
        "db-dir": "data/servers/crux/rdb-docs",
        "block-cache": "crux.rocksdb/block-cache"
      }
    },
    "crux/tx-log": {
      "crux/module": "crux.kafka/->tx-log",
      "kafka-config": {
        "bootstrap-servers": "localhost:9092"
      },
      "tx-topic-opts": {
        "topic-name": "crux-tx-log"
      },
      "poll-wait-duration": "PT1S"
    }
  }
}
```

#### RocksDB

RocksDB is often used as the data store for Crux's query indices, [but can also be used for other persistence](https://opencrux.com/reference/rocksdb.html).

To use RocksDB persistence, you will need to ensure you have all the following jar files available in the same
directory as the connector:

- [juxt:crux-rocksdb](https://clojars.org/juxt/crux-rocksdb)
- [org.rocksdb:rocksdbjni](https://search.maven.org/artifact/org.rocksdb/rocksdbjni)
- [com.github.jnr:jnr-ffi](https://search.maven.org/artifact/com.github.jnr/jnr-ffi)

#### Kafka

Kafka is often used as the transaction log for Crux, [but can also be used for other persistence](https://opencrux.com/reference/kafka.html).

To use Kafka persistence, you will need to ensure you have all the following jar files available in the same
directory as the connector:

- [juxt:crux-kafka](https://clojars.org/juxt/crux-kafka)
- [org.apache.kafka:kafka-clients](https://search.maven.org/artifact/org.apache.kafka/kafka-clients)
- [cheshire](https://clojars.org/cheshire)
- [com.cognitect:transit-clj](https://search.maven.org/artifact/com.cognitect/transit-clj)

### Configure metrics collection

Crux is also able to collect metrics on its operation, which can be useful for monitoring and tuning areas like
performance. Basic metrics collection via the console (stdout) and CSV log file are built-in to the connector, and can
be configured using options like the following:

```json
{
  "class": "Connection",
  "connectorType": {
    "class": "ConnectorType",
    "connectorProviderClassName": "org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider"
  },
  "configurationProperties": {
    "crux.metrics/metrics": {
      "with-index-store-metrics?": true,
      "with-query-metrics?": true
    },
    "crux.metrics.csv/reporter": {
      "output-file": "data/servers/crux/metrics",
      "report-frequency": "PT1S",
      "rate-unit": "seconds",
      "duration-unit": "seconds"
    },
    "crux/index-store": { },
    "crux/document-store": { },
    "crux/tx-log": { }
  }
}
```

**Important note**: the directory specified for the metrics reporter (`data/servers/crux/metrics` in the example above)
must exist prior to starting the instance with the given configuration.

If you want to enable additional metrics collection (Prometheus, JMX, CloudWatch, etc) you will need to ensure all the
[jar files for those additional dependencies](https://opencrux.com/reference/monitoring.html) are available in the same
directory as the connector.

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
