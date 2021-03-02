<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

<!-- [![Quality](https://sonarcloud.io/api/project_badges/measure?project=egeria-connector-crux&metric=alert_status)](https://sonarcloud.io/dashboard?id=egeria-connector-crux) -->

[![License](https://img.shields.io/github/license/odpi/egeria-connector-crux)](LICENSE)
[![Build](https://github.com/odpi/egeria-connector-crux/workflows/Maven%20Package/badge.svg)](https://github.com/odpi/egeria-connector-crux/actions?query=workflow%3A%22Maven+Package%22)
[![Snapshot](https://img.shields.io/maven-metadata/v?label=development&metadataUrl=https%3A%2F%2Ftoken%3A82d3aaedcfbb070176d9b30df29ea28768b90691%40maven.pkg.github.com%2Fodpi%2Fegeria-connector-crux%2Forg%2Fodpi%2Fegeria%2Fegeria-connector-crux%2Fmaven-metadata.xml)](https://github.com/odpi/egeria-connector-crux/packages/617324)
[![Release](https://img.shields.io/maven-central/v/org.odpi.egeria/egeria-connector-crux)](https://mvnrepository.com/artifact/org.odpi.egeria/egeria-connector-crux)

# Crux Plugin Repository Connector

This repository houses the code for a plugin repository connector using [Crux](https://opencrux.com) as its back-end.
[Crux](https://opencrux.com) provides an unbundled database that supports temporal graph queries, and is itself built
on a pluggable architecture supporting a variety of underlying storage back-ends.

Of particular interest for Egeria is that it has native support for storing historical information and temporal queries
-- making it one of the first repositories to support the use of the `asOfTime` parameter across all metadata.

## Quick links

- See our [Getting Started](https://odpi.github.io/egeria-connector-crux/getting-started/) guide for step-by-step
  instructions on using this connector as an Egeria repository.
- See the [CTS Results](cts/README.md) for details on its conformance and rough performance numbers.

## How it works

The connector itself is implemented as a plugin repository connector: providing an option for an alternative metadata
repository for Egeria. Primarily this is a matter of integrating into the Open Connector Framework (OCF) and implementing
repository-level methods that adhere to those required by a Metadata Collection. These then communicate with Crux via
Crux's own API to read and write information to the underlying Crux node.

Crux itself is started as an embedded process within the connector. It can be configured to use any of the various
pluggable persistence layers supported by Crux itself, and communication between the Java code of the connector and
Crux itself (which is implemented in Clojure) occurs through the Crux Java API (not via REST).

![Overview](docs/overview.png)

> Overview of the connector implementation

Crux itself handles write transactions and persistence guarantees via these APIs, ensuring that all data is at least
recorded into the transaction log and document store prior to any write method returning.

By default, the repository connector further awaits confirmation that any write has been index (and is therefore
available for read operations) prior to returning. However, it is also possible to configure the connector in an
"ingest-optimized" mode that allows the indexing to occur asynchronously, and can therefore improve the speed of
write operations significantly (e.g. potentially useful for bulk ingest). This mode can be enabled by setting the
`syncIndex` configuration option to `false` as part of the connector's configuration.

----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.
