<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Setup

## 1. Obtain connector

Start by downloading the Crux repository connector:

=== "Latest release"
    [![Release](https://img.shields.io/maven-central/v/org.odpi.egeria/egeria-connector-crux?label=release)](http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.odpi.egeria&a=egeria-connector-crux&v=RELEASE&c=jar-with-dependencies)

=== "Latest snapshot"
    [![Development](https://img.shields.io/nexus/s/org.odpi.egeria/egeria-connector-crux?label=development&server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/org/odpi/egeria/egeria-connector-crux/)

The connector is: `egeria-connector-crux-{version}-jar-with-dependencies.jar`

## 2. Obtain server chassis

Download Egeria's server chassis:

=== "Latest release"
    [![Release](https://img.shields.io/maven-central/v/org.odpi.egeria/server-chassis-spring?label=release)](http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.odpi.egeria&a=server-chassis-spring&v=RELEASE)

=== "Latest snapshot"
    [![Development](https://img.shields.io/nexus/s/org.odpi.egeria/server-chassis-spring?label=development&server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/org/odpi/egeria/server-chassis-spring/)

!!! tip "The connector version indicates the minimum required Egeria version"

The server chassis is: `server-chassis-spring-{version}.jar`

## 3. Configure security

To get an initial environment up-and-running just download the `truststore.p12`
file from: [https://github.com/odpi/egeria/raw/master/truststore.p12](https://github.com/odpi/egeria/raw/master/truststore.p12).

??? question "Transport-level security"
    The [truststore.p12](https://github.com/odpi/egeria/raw/master/truststore.p12)
    file provides a local truststore for Java. This allows the self-signed certificate embedded
    within the server chassis (by default) to be trusted.

    Without this trust, interactions with the server chassis (such as the REST calls that are made
    through Java to handle interaction between the chassis and the connector) will result in an
    `SSLHandshakeException`.

    While this `truststore.p12` file allows SSL-encrypted communication, the fact that
    it relies on a self-signed certificate means that there is no independent source of trust
    in the interactions (which would typically be achieved through an independent Certificate
    Authority).

    Additional details on TLS for Egeria can be found in [the Egeria documentation](https://egeria.ai/open-metadata-implementation/admin-services/docs/user/omag-server-platform-transport-level-security.html).

## 4. Start the server chassis

Ensure the 3 files are in the same directory, and run:

```shell
java -Dloader.path=. -jar server-chassis-spring-*.jar
```

??? question "Explanation of the command"
    The `-Dloader.path=.` indicates that the server chassis should look for any
    connectors in the current directory. If you wanted to instead place the connector in a
    separate location (for example `/lib`), you could change the `.` to
    point to that other location (`-Dloader.path=/lib`).

??? success "Output for server chassis startup"
    ```text
     ODPi Egeria
        ____   __  ___ ___    ______   _____                                 ____   _         _     ___
       / __ \ /  |/  //   |  / ____/  / ___/ ___   ____ _   __ ___   ____   / _  \ / / __    / /  / _ /__   ____ _  _
      / / / // /|_/ // /| | / / __    \__ \ / _ \ / __/| | / // _ \ / __/  / /_/ // //   |  / _\ / /_ /  | /  _// || |
     / /_/ // /  / // ___ |/ /_/ /   ___/ //  __// /   | |/ //  __// /    /  __ // // /  \ / /_ /  _// / // /  / / / /
     \____//_/  /_//_/  |_|\____/   /____/ \___//_/    |___/ \___//_/    /_/    /_/ \__/\//___//_/   \__//_/  /_/ /_/
    
     :: Powered by Spring Boot (v2.3.3.RELEASE) ::
    
    2021-02-12 20:16:37.243  INFO 53787 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port(s): 9443 (https)
    2021-02-12 20:16:49.162  INFO 53787 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 9443 (https) with context path ''
    
    Fri Feb 12 20:16:40 GMT 2021 No OMAG servers listed in startup configuration
    Fri Feb 12 20:16:49 GMT 2021 OMAG server platform ready for more configuration
    ```

!!! attention "Wait for these two final lines of output to appear before proceeding"
    ```text
    {timestamp} No OMAG servers listed in startup configuration
    {timestamp} OMAG server platform ready for more configuration
    ```

    These final two lines of output indicate that the server chassis has completed starting up
    and is now ready to be configured.

    Any attempt to configure the server chassis before these lines are output is likely to
    result in failures.

## 5. Configure the connector

### a. Configure the event bus

```shell
curl -k -X POST -H "Content-Type: application/json" \
  --data '{"producer":{"bootstrap.servers":"localhost:9092"},"consumer":{"bootstrap.servers":"localhost:9092"}}' \
  "https://localhost:9443/open-metadata/admin-services/users/admin/servers/crux/event-bus?connectorProvider=org.odpi.openmetadata.adapters.eventbus.topic.kafka.KafkaOpenMetadataTopicProvider"
```

??? question "Detailed explanation"
    The event bus is how Egeria coordinates communication amongst its various servers and
    repositories: for example, ensuring that any new type definitions are registered with each
    repository capable of handling them, notifying other repositories when the metadata in one
    repository changes, etc.

    The URL parameter `connectorProvider` defines the type of event bus to use (in
	this case Apache Kafka).

    The JSON payload gives details about how to connect to Apache Kafka, in this case assuming
    it is running on local machine (`localhost`) on its default port (`9092`).

??? success "Response from event bus configuration"
    ```json
    {"class":"VoidResponse","relatedHTTPCode":200}
    ```

### b. Configure the cohort

```shell
curl -k -X POST \
  "https://localhost:9443/open-metadata/admin-services/users/admin/servers/crux/cohorts/mycohort"
```

??? question "Detailed explanation"
    The cohort is a network of interacting metadata servers, which could be some subset of the
    overall Egeria landscape.

    We only need to supply a name for the cohort, which forms part of the URL itself: in this
    example using `mycohort`.

??? success "Response from cohort configuration"
    ```json
    {"class":"VoidResponse","relatedHTTPCode":200}
    ```

### c. Configure Crux connector

```shell
curl -k -X POST -H "Content-Type: application/json" \
  --data '{"class":"Connection","connectorType":{"class":"ConnectorType","connectorProviderClassName":"org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider"}}' \
  "https://localhost:9443/open-metadata/admin-services/users/admin/servers/crux/local-repository/mode/plugin-repository/connection"
```

??? question "Detailed explanation"
    This final call to the API configures the behavior of the Crux repository itself.

    The URL to which we post indicates that we will use the Egeria server chassis's built-in
    repository plugin capability to access the Crux repository connector.

    The JSON payload's contents define how this plugin itself should be configured: specifically,
    which Java class should be used for the repository connection. Here we can see the payload
    refers to the `CruxOMRSRepositoryConnectorProvider`, which therefore tells the
    plugin to use this class -- specific to the Crux repository connector -- in order to configure
    its repository connection.

??? success "Response from connector configuration"
    ```json
    {"class":"VoidResponse","relatedHTTPCode":200}
    ```

!!! attention "The default configuration is for an in-memory (non-persistent) repository"
    The default configuration used in this example will startup the Crux repository in
    a completely embedded, in-memory-only mode. No data will be persisted, so if you stop or
    restart the server chassis all data will be lost.

    While this is actually beneficial for quick experimentation and testing purposes, it is
    naturally not desirable for a "real" repository: you'd want your metadata to stick around
    even when the server goes offline.

    [Additional options for configuring Crux](#options-for-configuring-crux), including with
    persistence, can be found at the end of this page.

## 6. Start the connector instance

```shell
curl -k -X POST "https://localhost:9443/open-metadata/admin-services/users/admin/servers/crux/instance"
```

??? question "Detailed explanation"
    Up to this point we have only configured the connector, but have not actually started it.

    This final API call tells Egeria to start the connector, based on the configuration the previous
    API calls defined.

??? success "Response from connector instance startup"
    ```json
    {
      "class": "SuccessMessageResponse",
      "relatedHTTPCode": 200,
      "successMessage": "Fri Feb 12 20:34:16 GMT 2021 crux is running the following services: [Open Metadata Repository Services (OMRS)]"
    }
    ```

    It may take 10-15 seconds to complete, but the example response above indicates that the
    connector instance is now running.

??? info "Other startup information of potential interest"
    Back in the console where the server chassis is running, you should see the audit log
    printing out a large amount of information as the startup is running. Most of this is
    related to the registration of type definition details with the repository.

    ```text hl_lines="49"
     ODPi Egeria
        ____   __  ___ ___    ______   _____                                 ____   _         _     ___
       / __ \ /  |/  //   |  / ____/  / ___/ ___   ____ _   __ ___   ____   / _  \ / / __    / /  / _ /__   ____ _  _
      / / / // /|_/ // /| | / / __    \__ \ / _ \ / __/| | / // _ \ / __/  / /_/ // //   |  / _\ / /_ /  | /  _// || |
     / /_/ // /  / // ___ |/ /_/ /   ___/ //  __// /   | |/ //  __// /    /  __ // // /  \ / /_ /  _// / // /  / / / /
     \____//_/  /_//_/  |_|\____/   /____/ \___//_/    |___/ \___//_/    /_/    /_/ \__/\//___//_/   \__//_/  /_/ /_/
    
     :: Powered by Spring Boot (v2.3.3.RELEASE) ::
    
    2021-02-12 20:27:40.812  INFO 61953 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port(s): 9443 (https)
    2021-02-12 20:27:48.963  INFO 61953 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 9443 (https) with context path ''
    
    Fri Feb 12 20:27:42 GMT 2021 No OMAG servers listed in startup configuration
    Fri Feb 12 20:27:49 GMT 2021 OMAG server platform ready for more configuration
    Fri Feb 12 20:34:04 GMT 2021 crux Startup OMRS-AUDIT-0064 The Open Metadata Repository Services (OMRS) has initialized the audit log for the Metadata Server called crux
    Fri Feb 12 20:34:04 GMT 2021 crux Startup OMAG-ADMIN-0001 The crux server is configured with a max page size of 1000
    Fri Feb 12 20:34:04 GMT 2021 crux Startup OMRS-AUDIT-0001 The Open Metadata Repository Services (OMRS) is initializing the subsystems to support a new server
    Fri Feb 12 20:34:04 GMT 2021 crux Startup OMRS-AUDIT-0003 The local repository is initializing the metadata collection named crux with an id of aa1f9067-7bba-4f65-ac0d-bd64e7ac7e6e
    Fri Feb 12 20:34:04 GMT 2021 crux Startup OMRS-AUDIT-0029 The local repository outbound event manager is initializing
    Fri Feb 12 20:34:04 GMT 2021 crux Information OMRS-CRUX-REPOSITORY-0001 The Crux repository is starting a new server instance
    Fri Feb 12 20:34:04 GMT 2021 crux Information OMRS-CRUX-REPOSITORY-0003 The Crux repository is starting a new in-memory instance
    Fri Feb 12 20:34:14 GMT 2021 crux Information OMRS-CRUX-REPOSITORY-0004 The Crux repository has started a new instance for server crux, detected Crux version 21.02-1.15.0-beta
    Fri Feb 12 20:34:14 GMT 2021 crux Information OMRS-AUDIT-0050 The Open Metadata Repository Services (OMRS) is about to process open metadata archive Open Metadata Types
    Fri Feb 12 20:34:14 GMT 2021 crux Types OMRS-AUDIT-0301 The local server has added a new type called object with a unique identifier of 1c4b21f4-0b67-41a7-a6ed-2af185eb9b3b and a version number of 1 from ODPi Egeria (2.6)
    ...
    Fri Feb 12 20:34:14 GMT 2021 crux Types OMRS-AUDIT-0303 The local server has updated an existing type called SupportedDiscoveryService with a unique identifier of dff45aeb-c65e-428c-9ab3-d756bc5d8dbb to version number of 3 using a patch from ODPi Egeria (2.6)
    Fri Feb 12 20:34:14 GMT 2021 crux Information OMRS-AUDIT-0053 The Open Metadata Repository Services (OMRS) has processed 698 types and 0 instances from open metadata archive Open Metadata Types
    Fri Feb 12 20:34:14 GMT 2021 crux Startup OMRS-AUDIT-0004 Connecting to the metadata highway
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0005 Connecting to open metadata repository cohort mycohort
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0029 The mycohort cohort inbound event manager is initializing
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0030 Registering the mycohort event consumer with the local repository outbound event manager
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0030 Registering the Local Repository Content (TypeDef) Manager event consumer with the mycohort cohort inbound event manager
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0030 Registering the Local Repository Inbound Instance Events event consumer with the mycohort cohort inbound event manager
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0031 The mycohort cohort inbound event manager is starting with 1 type definition event consumer(s) and 1 instance event consumer(s)
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0026 Initializing listener for cohort mycohort
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0019 An OMRS Topic Connector has registered with an event bus connector for topic OMRSTopic.openmetadata.repositoryservices.cohort.mycohort.OMRSTopic
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OCF-KAFKA-TOPIC-CONNECTOR-0001 Connecting to Apache Kafka Topic OMRSTopic.openmetadata.repositoryservices.cohort.mycohort.OMRSTopic with a server identifier of 1e0c241c-d4d0-48b2-92c7-e4aa0115b15f
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OCF-KAFKA-TOPIC-CONNECTOR-0015 The local server is attempting to connect to Kafka, attempt 1
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OCF-KAFKA-TOPIC-CONNECTOR-0003 10 properties passed to the Apache Kafka Consumer for topic OMRSTopic.openmetadata.repositoryservices.cohort.mycohort.OMRSTopic
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OCF-KAFKA-TOPIC-CONNECTOR-0002 10 properties passed to the Apache Kafka Producer for topic OMRSTopic.openmetadata.repositoryservices.cohort.mycohort.OMRSTopic
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OCF-KAFKA-TOPIC-CONNECTOR-0010 The Apache Kafka producer for topic OMRSTopic.openmetadata.repositoryservices.cohort.mycohort.OMRSTopic is starting up with 0 buffered messages
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0015 The listener thread for an OMRS Topic Connector for topic OMRSTopic.openmetadata.repositoryservices.cohort.mycohort.OMRSTopic has started
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0020 An OMRS Topic Connector is ready to send and receive events on topic OMRSTopic.openmetadata.repositoryservices.cohort.mycohort.OMRSTopic
    Fri Feb 12 20:34:15 GMT 2021 crux Cohort OMRS-AUDIT-0060 Registering with open metadata repository cohort mycohort using metadata collection id aa1f9067-7bba-4f65-ac0d-bd64e7ac7e6e
    Fri Feb 12 20:34:15 GMT 2021 crux Cohort OMRS-AUDIT-0062 Requesting registration information from other members of the open metadata repository cohort mycohort
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0031 The local repository outbound event manager is starting with 1 type definition event consumer(s) and 1 instance event consumer(s)
    Fri Feb 12 20:34:15 GMT 2021 crux Startup OMRS-AUDIT-0032 The local repository outbound event manager is sending out the 698 type definition events that were generated and buffered during server initialization
    Fri Feb 12 20:34:16 GMT 2021 crux Startup OMRS-AUDIT-0007 The Open Metadata Repository Services (OMRS) has initialized
    Fri Feb 12 20:34:16 GMT 2021 crux Startup OMAG-ADMIN-0004 The crux server has successfully completed start up.  The following services are running: [Open Metadata Repository Services (OMRS)]
    ```

    This final line indicates that the server has now started up and completed its registration with the cohort.

## Options for configuring Crux

### Pluggable persistence

There are many options for configuring Crux itself.

A list of overall persistence modules and deeper configuration options for each can be found
through [Crux's own documentation](https://opencrux.com/reference/configuration.html).

To enable persistence, send in the JSON document configuration outlined in
[Crux's own documentation](https://opencrux.com/reference/configuration.html)
directly to the `cruxConfig` key of the `configurationProperties`
property of Egeria's connector configuration:

!!! example "Example persistent configuration"
    ```json linenums="1" hl_lines="8-30"
    {
      "class": "Connection",
      "connectorType": {
        "class": "ConnectorType",
        "connectorProviderClassName": "org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider"
      },
      "configurationProperties": {
        "cruxConfig": {
          "egeria.crux.lucene/lucene-store": {
            "db-dir": "data/servers/crux/lucene"
          },
          "crux/index-store": {
            "kv-store": {
              "crux/module": "crux.rocksdb/->kv-store",
              "db-dir": "data/servers/crux/rdb-index"
            }
          },
          "crux/document-store": {
            "kv-store": {
              "crux/module": "crux.rocksdb/->kv-store",
              "db-dir": "data/servers/crux/rdb-docs"
            }
          },
          "crux/tx-log": {
            "kv-store": {
              "crux/module": "crux.rocksdb/->kv-store",
              "db-dir": "data/servers/crux/rdb-tx"
            }
          }
        }
      }
    }
    ```

    The one exception to simply using Crux's JSON configuration as-is are the lines 9-11
    above: these configure a Lucene index built and maintained directly as part of the connector's
	code-base, and therefore in a separate namespace from Crux's own configuration. It is highly
	recommended to use this Lucene index as it offers significant performance improvements for
	any text-based queries.

    The remainder of the configuration in this example defines RocksDB to act as the persistence
    layer for Crux's index and document stores, as well as its transaction log.

!!! tip "You may need to download additional dependencies"
    Depending on the persistence you choose to use, you may need to download additional
    dependencies and place them into the same directory as the connector.

    For example, when using RocksDB you will need the following:

    - [pro.juxt.crux:crux-rocksdb](https://search.maven.org/artifact/pro.juxt.crux/crux-rocksdb)
    - [org.rocksdb:rocksdbjni](https://search.maven.org/artifact/org.rocksdb/rocksdbjni)
    - [com.github.jnr:jnr-ffi](https://search.maven.org/artifact/com.github.jnr/jnr-ffi)

    You can generally determine the additional dependencies you will need by looking at the
    `project.clj` file of relevant Crux module -- specifically its `:dependencies`
    section. For example, sticking with RocksDB, here is the [project.clj](https://github.com/juxt/crux/blob/master/crux-rocksdb/project.clj):

    ```clojure linenums="1" hl_lines="1 13-14"
    (defproject pro.juxt.crux/crux-rocksdb "crux-git-version"
      :description "Crux RocksDB"
      :plugins [[lein-parent "0.3.8"]]
      :parent-project {:path "../project.clj"
                       :inherit [:repositories :deploy-repositories
                                 :managed-dependencies
                                 :pedantic? :global-vars
                                 :license :url :pom-addition]}
      :scm {:dir ".."}
      :dependencies [[org.clojure/clojure "1.10.3"]
                     [pro.juxt.crux/crux-core "crux-git-version"]
                     [pro.juxt.crux/crux-metrics "crux-git-version" :scope "provided"]
                     [org.rocksdb/rocksdbjni "6.12.7"]
                     [com.github.jnr/jnr-ffi "2.1.12"]]
      :middleware [leiningen.project-version/middleware])
    ```

    Note that its dependencies include `org.rocksdb:rocksdbjni` and `com.github.jnr:jnr-ffi`.
    The third dependency given in the previous slide is this Crux persistence module itself
    (`pro.juxt.crux:crux-rocksdb`).

### Connector options

There are currently two configuration options for the connector itself:

| Option | Description |
|---|---|
| `luceneRegexes` | Controls whether the connector will interpret unquoted regexes as Lucene-compatible (true) or not (false): in the latter case ensuring that we fallback to full Java regex checking (which will be significantly slower). |
| `syncIndex` | Controls whether the connector will wait for the Crux indexes to be updated before returning from write operations (true) or only that they are transactionally persisted (false). |

!!! example "Example configuration showing the default settings"
    ```json
    {
      "class": "Connection",
      "connectorType": {
        "class": "ConnectorType",
        "connectorProviderClassName": "org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider"
      },
      "configurationProperties": {
        "cruxConfig": { },
        "luceneRegexes": true,
        "syncIndex": true
      }
    }
    ```
