<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# How migration works

The connector embeds its own persistence layer (storage) for metadata in Crux. While we try
to keep this underlying storage unchanged as much as possible to ease moving from one version
of the connector to another, this is not always possible.

It may therefore be necessary to occasionally migrate any pre-existing metadata stored in the
embedded Crux repository in order to make use of the latest features and performance benefits of
a new version of the connector.

!!! attention "Persistence layer version must be compatible with connector version"
    To ensure the integrity of the metadata, the connector will validate that the version of the persistence matches
    the version the connector expects before even attempting to run -- if this validation fails, you will see an
    `OMRS-CRUX-REPOSITORY-500-003` error in the audit log to indicate that you must first migrate the metadata before
    running this version of the connector.

    In other words: if migration is needed, the newer version of the connector will not allow you to run against an
    older set of metadata without first running the migration.

    Your only options will be to continue to use an older version of the connector (with which your pre-existing
    metadata is compatible), or to run this offline migration of your repository and then run the newer version of the
    connector.

The migration itself runs outside the connector (while the connector is offline), in order to
maximize the throughput of the in-place upgrade of the repository. The time it takes to run the
migration naturally depends on a number of factors, such as the amount of pre-existing metadata
that must be migrated and the specific changes needed by the upgrade.

As a very approximate metric, we would expect the in-place upgrade to be capable of migrating
60-100 metadata instances (entities or relationships) per second.  So 10 000 instances should
take approximately 2 minutes.

## Change log

There should be no need to actually understand these details, as the connector (and migration)
will handle them for you. However, for the interested reader, the following changes were made to
the storage layer in the specified release:

### 2.9

- `InstanceAuditHeaderMapping` no longer separates the type GUID and supertype GUIDs, but places all such
  information into a single vector (for improved search performance)
- `RelationshipMapping` no longer has separate properties for each entity proxy, but stores them as a vector:
  this retains their ordering, but allows relationships to be more efficiently searched from either related entity
