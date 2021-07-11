<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Limitations

There are a number of limitations to be aware of with the high availability configuration:

- Write operations will only be consistent when using a non-embedded Crux back-end: e.g. Kafka, S3, or JDBC.
- Since the indexes are local to each pod, read operations will be eventually consistent: the specific pod
  to which a query is routed may not yet have updated its embedded index with the results of the very
  latest write operations from some other pod.
- Currently configuration of Egeria requires making a number of REST API calls, which limits how dynamic
  we can be in adding or removing pods to an already-running cluster (in particular:
  [we cannot rely on a readiness probe to indicate pod readiness to process actual work, but only its
  readiness to be configured](https://github.com/odpi/egeria-connector-crux/issues/127#issuecomment-841678140)).
  We hope to address this soon by allowing configuration and startup to be done without relying on REST
  calls, at which point we should be able to also support dynamically adding and removing pods from
  the cluster.
