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

- The entity search tests may currently fail particular long-running query pattern: typically where a query by value or
  attribute is done without providing any restriction on the type of instances against which the query should run. This
  currently results in a broad OR-based query to cover all the potential type and property combinations that would be
  valid, and ends up being very costly as a result.

## Performance notes

The CTS is not intended as a performance benchmark, as its activities are unlikely to represent the actual mix
of work that anyone would put against a repository in real-world use.

However, in lieu of any other means of doing some level of scale / performance testing, it may serve as a very rough
proxy to compare one repository to another -- given the extensive amount of metadata reading and writing that it
does throughout its tests.

Following are base statistics on the various repository methods: all times in milliseconds.

Every time the CTS calls a particular repository method its elapsed time is captured. We do this 3 times for each scale
level (up to @20, beyond that just a single test), and take the _median_ of all results for that method across all executions.
This should limit any potential skew of the "typical" result by significant outliers (less of a concern with the larger
scale factors due to the additional volume of method calls overall).

These "scale factors" dictate the volume at which the CTS runs, and roughly translate as:
- 1/2 the total number of instances of a given type that will be created for testing
- Making the total number of instances created ~2x the scale factor * the number of types (~700), so for example @20 would
  be roughly 28 000 instances of metadata
- The size of each page of results that is requested by each of the search tests

The tests themselves are entirely automated via a Kubernetes cluster, ensuring that all components of the test (Egeria
server chassis, connector, repository persistence and event bus) are all co-located on the same node of the Kubernetes
cluster. (This should limit any potential performance impacts from hardware / network / virtualization on a given
machine at a given time.)

In each test, Crux is configured with RocksDB for all persistence and Lucene for text indexing, and no
other configuration changes from the default are made. (For the JanusGraph and In-memory repository comparisons,
all default settings were used.)

### Crux, as volume increases

Method | @2 | @5 | @10 | @20 | @40* | @80 | @160
---|---|---|---|---|---|---|---
addEntity | 330 | 205 | 142 | 326 | 308
addEntityProxy | 457 | 164 | 132 | 463 | 444
addRelationship | 107 | 122 | 127 | 126 | 128
classifyEntity | 95 | 94 | 96 | 94 | 94
declassifyEntity | 162 | 156 | 156 | 158 | 162
deleteEntity | 93 | 89 | 91 | 90 | 91
deleteRelationship | 94 | 91 | 94 | 92 | 92
findEntities | 43 | 47 | 53 | 54 | 58
findEntitiesByProperty | 61 | 76 | 89 | 98 | *115*
findEntitiesByPropertyValue | 77 | 92 | 109 | 114 | 128
findRelationships | 60 | 68 | 84 | 105 | 143
findRelationshipsByProperty | 59 | 60 | 60 | 72 | *95*
findRelationshipsByPropertyValue | 64 | 71 | 73 | 84 | 109
findTypesByName | 16 | 16 | 17 | 16 | 14
getAllTypes | 280 | 454 | 932 | 360 | 681
getAttributeTypeDefByGUID | 26 | 24 | 27 | 22 | 22
getAttributeTypeDefByName | 27 | 24 | 29 | 23 | 23
getEntityDetail | 17 | 17 | 17 | 17 | 17
getEntityNeighborhood | 44 | 46 | 55 | 52 | 69
getEntitySummary | 18 | 18 | 18 | 17 | 17
getLinkingEntities | 67 | 84 | 87 | 98 | 210
getRelatedEntities | 82 | 118 | 110 | 120 | 246
getRelationship | 19 | 19 | 19 | 19 | 19
getTypeDefByGUID | 15 | 16 | 16 | 15 | 13
getTypeDefByName | -- | -- | -- | -- | --
isEntityKnown | 18 | 18 | 18 | 18 | 18
isRelationshipKnown | 19 | 18 | 18 | 17 | 18
purgeEntity | 338 | 344 | 357 | 346 | 304
purgeEntityReferenceCopy | 738 | 684 | 686 | 683 | 642
purgeRelationship | 310 | 295 | 304 | 300 | 293
purgeRelationshipReferenceCopy | 87 | 87 | 88 | 88 | 88
reHomeEntity | 166 | 161 | 166 | 163 | 167
reHomeRelationship | 114 | 114 | 119 | 113 | 115
reIdentifyEntity | 1,567 | 1,439 | 1,292 | 1,540 | 1,444
reIdentifyRelationship | 328 | 315 | 317 | 340 | 333
reTypeEntity | 124 | 124 | 122 | 122 | 134
refreshEntityReferenceCopy | 136 | 135 | 132 | 132 | 125
refreshRelationshipReferenceCopy | 22 | 23 | 22 | 22 | 23
restoreEntity | 78 | 74 | 76 | 74 | 74
restoreRelationship | 81 | 78 | 78 | 77 | 79
saveEntityReferenceCopy | 313 | 304 | 286 | 305 | 292
saveRelationshipReferenceCopy | 99 | 98 | 102 | 97 | 99
undoEntityUpdate | 87 | 81 | 84 | 83 | 86
undoRelationshipUpdate | 90 | 84 | 85 | 84 | 89
updateEntityClassification | 169 | 164 | 167 | 168 | 170
updateEntityProperties | 148 | 139 | 143 | 142 | 148
updateEntityStatus | 82 | 77 | 80 | 79 | 80
updateRelationshipProperties | 143 | 135 | 136 | 138 | 145
updateRelationshipStatus | 84 | 80 | 82 | 80 | 84
verifyAttributeTypeDef | 28 | 29 | 31 | 27 | 27
verifyTypeDef | 17 | 18 | 18 | 17 | 16

#### Notes

- At scale factor 40 and beyond, there began to be consistent query timeouts for the
  `find[Entities|Relationships]ByProperty` methods when they query did not contain any type
  condition (searching across all types). In these circumstances, a broad OR-based query is used to
  ensure that a given property (that could exist in dozens or even 30+ different types) are all captured. Even without
  regular expressions, these queries are therefore very expensive to run. These timed out elapsed times were not
  included in the median calculation, and would most likely be resolved by the changes suggested in: https://github.com/odpi/egeria/issues/4724

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
