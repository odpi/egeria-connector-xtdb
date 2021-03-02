<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Conformance Test Suite

This directory contains information regarding the Conformance Test Suite (CTS) and the Crux connector.

## Charts

The `charts` sub-directory contains a Helm chart to automate the execution of the CTS against a Crux repository
connector, to produce a set of repeatable CTS results.

These use a default configuration for the Crux repository where RocksDB is used for all persistence: index store,
document store and transaction log. No additional tuning of any parameters (Crux or RocksDB) is applied: they use
all of their default settings.

## Results

The `results` sub-directory contains results of running the CTS against the Crux connector. For each release, you
will find the following details:

- `openmetadata_cts_summary.json` - a summary of conformance against each profile
- Description of the k8s environment
    - `deployment` - details of the deployed components used for the test
    - `configmap.yaml` - details of the variables used within the components of the test
- The OMAG server configurations:
    - `omag.server.crux.config` - the configuration of the Crux connector (proxy)
    - `omag.server.cts.config` - the configuration of the CTS workbench
- The cohort registrations:
    - `cohort.coco.crux.local` - the local Crux connector (proxy) cohort registration information
    - `cohort.coco.crux.remote` - the cohort members considered remote from the Crux connector (proxy)'s perspective
    - `cohort.coco.cts.local` - the local CTS Workbench cohort registration
    - `cohort.coco.cts.remote` - the cohort members considered remote from the CTS Workbench's perspective
- Detailed results:
    - `pd.tar.gz` - an archive containing the full detailed results of every profile tested
    - `tcd.tar.gz` - an archive containing the full detailed results of every test case executed

## Egeria 2.7, Crux 21.02-1.15.0

Profile | Result
---|---
Metadata sharing | CONFORMANT_FULL_SUPPORT
Reference copies | CONFORMANT_FULL_SUPPORT
Metadata maintenance | CONFORMANT_FULL_SUPPORT
Dynamic types | UNKNOWN_STATUS
Graph queries | CONFORMANT_FULL_SUPPORT
Historical search | CONFORMANT_FULL_SUPPORT
Entity proxies | CONFORMANT_FULL_SUPPORT
Soft-delete and restore | CONFORMANT_FULL_SUPPORT
Undo an update | CONFORMANT_FULL_SUPPORT
Reidentify instance | CONFORMANT_FULL_SUPPORT
Retype instance | CONFORMANT_FULL_SUPPORT
Rehome instance | CONFORMANT_FULL_SUPPORT
Entity search | CONFORMANT_FULL_SUPPORT
Relationship search | CONFORMANT_FULL_SUPPORT
Entity advanced search | CONFORMANT_FULL_SUPPORT
Relationship advanced search | CONFORMANT_FULL_SUPPORT

## Notes

- The entity search tests may currently fail particular long-running query pattern: for example, an ends-with or contains
  regex search against all instances. Even search indexes (Lucene, Elastic, etc) strongly discourage searching by such
  patterns, so simply be aware that wherever possible you should continue to limit your searches based on types of
  interest and generally avoid sweeping queries along the lines of "everything that contains the letter 'a'".

## Performance notes

The CTS is not intended as a performance benchmark, as its activities are unlikely to represent the actual mix
of work that anyone would put against a repository in real-world use.

However, in lieu of any other means of doing some level of scale / performance testing, it may serve as a very rough
proxy to compare one repository to another -- given the extensive amount of metadata reading and writing that it
does throughout its tests.

Following are base statistics on the various repository methods: all times in milliseconds.

Every time the CTS calls a particular repository method its elapsed time is captured. We do this 3 times for each scale
level, and take the _median_ of all results for that method across all 3 executions. The tests themselves are entirely
automated via a Kubernetes cluster, ensuring that all components of the test (Egeria server chassis, connector, repository
persistence and event bus) are all co-located on the same node of the Kubernetes cluster. (This should limit any
potential skew from significant outliers, as well as any potential performance impacts from hardware / network /
virtualization on a given machine at a given time.)

In each test, Crux is configured with RocksDB for all persistence and Lucene for text indexing, and no
other configuration changes from the default are made. (For the JanusGraph and In-memory repository comparisons,
all default settings were used.)

### Crux, as volume increases

Method | @2 | @5 | @10
---|---|---|---
addEntity | 330 | 205
addEntityProxy | 457 | 164
addRelationship | 107 | 122
classifyEntity | 95 | 94
declassifyEntity | 162 | 156
deleteEntity | 93 | 89
deleteRelationship | 94 | 91
findEntities | 43 | 47
findEntitiesByProperty | 61 | 76
findEntitiesByPropertyValue | 77 | 92
findRelationships | 60 | 68
findRelationshipsByProperty | 59 | 60
findRelationshipsByPropertyValue | 64 | 71
findTypesByName | 16 | 16
getAllTypes | 280 | 454
getAttributeTypeDefByGUID | 26 | 24
getAttributeTypeDefByName | 27 | 24
getEntityDetail | 17 | 17
getEntityNeighborhood | 44 | 46
getEntitySummary | 18 | 18
getLinkingEntities | 67 | 84
getRelatedEntities | 82 | 118
getRelationship | 19 | 19
getTypeDefByGUID | 15 | 16
getTypeDefByName | -- | --
isEntityKnown | 18 | 18
isRelationshipKnown | 19 | 18
purgeEntity | 338 | 344
purgeEntityReferenceCopy | 738 | 684
purgeRelationship | 310 | 295
purgeRelationshipReferenceCopy | 87 | 87
reHomeEntity | 166 | 161
reHomeRelationship | 114 | 114
reIdentifyEntity | 1,567 | 1,439
reIdentifyRelationship | 328 | 315
reTypeEntity | 124 | 124
refreshEntityReferenceCopy | 136 | 135
refreshRelationshipReferenceCopy | 22 | 23
restoreEntity | 78 | 74
restoreRelationship | 81 | 78
saveEntityReferenceCopy | 313 | 304
saveRelationshipReferenceCopy | 99 | 98
undoEntityUpdate | 87 | 81
undoRelationshipUpdate | 90 | 84
updateEntityClassification | 169 | 164
updateEntityProperties | 148 | 139
updateEntityStatus | 82 | 77
updateRelationshipProperties | 143 | 135
updateRelationshipStatus | 84 | 80
verifyAttributeTypeDef | 28 | 29
verifyTypeDef | 17 | 18

### Compared to other plugin repositories (scale factor of 2)

Method | In-memory | Crux | JanusGraph
---|---|---|---
addEntity | 20 | 330 | 485
addEntityProxy | 18 | 457 | 183
addRelationship | 21 | 107 | 176
classifyEntity | 22 | 95 | 780
declassifyEntity | 21 | 162 | 715
deleteEntity | 20 | 93 | 622
deleteRelationship | 21 | 94 | 266
findEntities | 20 | 43 | 27
findEntitiesByProperty | 20 | 61 | 27
findEntitiesByPropertyValue | 20 | 77 | 69
findRelationships | 19 | 60 | 29
findRelationshipsByProperty | 19 | 59 | 30
findRelationshipsByPropertyValue | 19 | 64 | 46
findTypesByName | 17 | 16 | 18
getAllTypes | 596 | 280 | 555
getAttributeTypeDefByGUID | 24 | 26 | 31
getAttributeTypeDefByName | 24 | 27 | 32
getEntityDetail | 15 | 17 | 18
getEntityNeighborhood | 23 | 44 | 41
getEntitySummary | 16 | 18 | 17
getLinkingEntities | (not implemented) | 67 | 32
getRelatedEntities | (not implemented) | 82 | 53
getRelationship | 16 | 19 | 21
getTypeDefByGUID | 16 | 15 | 17
getTypeDefByName | -- | -- | --
isEntityKnown | 16 | 18 | 20
isRelationshipKnown | 15 | 19 | 17
purgeEntity | 20 | 338 | 212
purgeEntityReferenceCopy | 17 | 738 | 144
purgeRelationship | 20 | 310 | 107
purgeRelationshipReferenceCopy | 18 | 87 | 172
reHomeEntity | 20 | 166 | 554
reHomeRelationship | 22 | 114 | 318
reIdentifyEntity | 21 | 1,567 | 767
reIdentifyRelationship | 22 | 328 | 331
reTypeEntity | 21 | 124 | 237
refreshEntityReferenceCopy | 18 | 136 | 19
refreshRelationshipReferenceCopy | 19 | 22 | 25
restoreEntity | 18 | 78 | 612
restoreRelationship | 17 | 81 | 267
saveEntityReferenceCopy | 17 | 313 | 434
saveRelationshipReferenceCopy | 17 | 99 | 197
undoEntityUpdate | 18 | 87 | (not implemented)
undoRelationshipUpdate | 18 | 90 | (not implemented)
updateEntityClassification | 22 | 169 | 1,055
updateEntityProperties | 20 | 148 | 290
updateEntityStatus | 21 | 82 | 589
updateRelationshipProperties | 20 | 143 | 326
updateRelationshipStatus | 20 | 84 | 270
verifyAttributeTypeDef | 32 | 28 | 34
verifyTypeDef | 18 | 17 | 20

----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.
