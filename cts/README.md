<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Test Suites

This directory contains information regarding the automated Test Suites and the Crux connector, for the latest available
releases. [Historical results, for prior releases, are kept separately, by release.](historical/)

## Charts

The `charts` sub-directory contains a Helm chart to automate the execution of these suites against a Crux repository
connector, to produce a set of repeatable results.

These use a default configuration for the Crux repository where Lucene is used as a text index and RocksDB is used for
all persistence: index store, document store and transaction log. No additional tuning of any parameters (Crux or RocksDB)
is applied: they use all of their default settings.

## Results contents

The `results` sub-directory contains results of running the suites against the Crux connector. For each test suite execution,
you will find the following details:

- `openmetadata_cts_summary.json` - a summary of the results of each profile
- Description of the k8s environment
  - `deployment` - details of the deployed components used for the test
  - `configmap.yaml` - details of the variables used within the components of the test
- The OMAG server configurations:
  - `omag.server.crux.config` - the configuration of the Crux connector (proxy)
  - `omag.server.cts.config` - the configuration of the test workbench
- The cohort registrations:
  - `cohort.coco.crux.local` - the local Crux connector (proxy) cohort registration information
  - `cohort.coco.crux.remote` - the cohort members considered remote from the Crux connector (proxy)'s perspective
  - `cohort.coco.cts.local` - the local test Workbench cohort registration
  - `cohort.coco.cts.remote` - the cohort members considered remote from the test Workbench's perspective
- Detailed results:
  - `pd.tar.gz` - an archive containing the full detailed results of every profile tested
  - `tcd.tar.gz` - an archive containing the full detailed results of every test case executed
- Jupyter Notebooks used to analyze the results:
  - `analyze-performance-results.ipynb` - details about the environment, instance counts, and distribution of elapsed
    times per method, also illustrating how the results can be analyzed more deeply
  - `calculate-medians.ipynb` - used to calculate the medians displayed in the table further below
  - (to run either of these notebooks, you will need to first extract the `pd.tar.gz` file to have the JSON results
    files for analysis)

## Conformance Test Suite (CTS) Results

The CTS measures conformance of the repository with the expected behavior of an Egeria repository. Conformance indicates
that the repository behaves precisely as expected for an Egeria repository.

Profile | Result | Connector | Egeria | Crux
---|---|---|---|---
Metadata sharing | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Reference copies | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Metadata maintenance | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Dynamic types | UNKNOWN_STATUS | 2.10 | 2.10 | 21.05-1.17.0
Graph queries | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Historical search | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Entity proxies | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Soft-delete and restore | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Undo an update | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Reidentify instance | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Retype instance | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Rehome instance | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Entity search | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Relationship search | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Entity advanced search | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0
Relationship advanced search | CONFORMANT_FULL_SUPPORT | 2.10 | 2.10 | 21.05-1.17.0

### Notes

- The entity search tests could fail a particular long-running query pattern unless Lucene is configured:
  typically where a query by value or attribute is done without providing any restriction on the type of instances against
  which the query should run. Configure the connector with Lucene to avoid these timeouts.
- The `Dynamic types` profile currently does not have any tests defined, so will be `UNKNOWN_STATUS` for all repositories
  and connectors.

## Performance Test Suite (PTS) Results

The PTS focuses on measuring the performance of the various operations of an Egeria repository. It does not do as
thorough checking of every individual result as the CTS, but instead ensures a consistent volume of metadata exists in
the environment for the duration of the tests and records various metrics about both these volumes and the individual
runtimes of each execution of the various metadata repository operations.

Following are base statistics on the various repository methods: all times in milliseconds.

The _median_ of all results for that method across all executions for a given set of volume parameters is given below
(all times in milliseconds) to give an idea of the "typical" result, while limiting potential skew from significant
outliers.

A more detailed set of statistics is best reviewed through the Jupyter Notebook provided in each results directory,
where you can review:

- the full distributions of execution times (including the outliers)
- detailed individual outlier results (e.g. the top-10 slowest response times per method)
- volumes in place during the tests (how many entities, how many relationships, etc)

The volume parameters that were used for each test are specified using the following convention:

- `i-s`, where `i` is the value for the `instancesPerType` parameter to the PTS and `s` is the value for
  `maxSearchResults`. For example, `5-2` means 5 instances will be created for every open metadata type and 2 will be
  the maximum number of results per page for methods that include paging.
  - All tests are run from `5-2` through `20-10` to give a sense of the performance impact of doubling the number
    of instances and search results.
  - Above this, the graph queries are no longer included: they become exponentially more complex as the volumes grow, and
    while they will still return results, the depth of their testing in the PTS means that they can contribute many hours
    (or even days) to the overall suite execution -- they are therefore left out to be able to more quickly produce
    results for the other methods at progressively higher volumes.
  - The page size is left at a maximum of `10` for subsequent tests so that it is only the volume of instances in total
    that are doubling each time, rather than also the number of detailed results.
  - Instance counts range from 4,410 (at `5-2`) up to 70,560 (at `80-10`).

### Crux (connector 2.10, Egeria 2.10, Crux 21.05-1.17.0)

![Graphical comparison](results/2.10-21.05-1.17.0-beta/volume_comparison.png)

> Graphical comparison of volumes

In the graphical comparison, a point plot is used to show the typical execution time of each method at the different
volumes. Each point on the plot represents the _median_ execution time for that method, at a given volume of metadata.
The horizontal lines that appear around each point are confidence intervals calculated by a bootstrapping process: in
simple terms, the larger the horizontal line, the more variability there is for that particular method's execution time
(a singular median value is insufficient to represent such variability).

We can see that the retrieval and write operations remain very consistent, with almost no variability, throughout the
growth in volume. The search operations, however, begin to clearly degrade at the highest volumes tested. Further
investigation into other optimized settings for the search operations for these larger volumes is likely warranted
as the next step to continue to improve performance.

Profile | Method | 05-02 (4,470) | 10-05 (8,940) | 20-10 (17,880) | 40-10 (35,760) | 80-10 (71,520)
---|---|---|---|---|---|---
Entity creation | addEntity | 103.0 | 108.0
... | saveEntityReferenceCopy | 96.0 | 104.0
Entity search | findEntities | 48.5 | 76.0
... | findEntitiesByProperty | 33.0 | 40.0
... | findEntitiesByPropertyValue | 49.0 | 74.0
Relationship creation | addRelationship | 104.0 | 105.0
... | saveRelationshipReferenceCopy | 107.0 | 107.0
Relationship search | findRelationships | 29.0 | 35.0
... | findRelationshipsByProperty | 30.0 | 36.0
... | findRelationshipsByPropertyValue | 48.0 | 62.0
Entity classification | classifyEntity | 163.0 | 172.0
... | saveClassificationReferenceCopy | 120.0 | 118.0
Classification search | findEntitiesByClassification | 40.0 | 54.0
Entity update | reTypeEntity | 86.0 | 82.0
... | updateEntityProperties | 118.0 | 120.0
Relationship update | updateRelationshipProperties | 140.0 | 129.0
Classification update | updateEntityClassification | 218.0 | 270.5
Entity undo | undoEntityUpdate | 112.0 | 112.0
Relationship undo | undoRelationshipUpdate | 130.0 | 127.0
Entity retrieval | getEntityDetail | 17.0 | 16.0
... | getEntitySummary | 17.0 | 16.0
... | isEntityKnown | 17.0 | 16.0
Entity history retrieval | getEntityDetail | 20.0 | 19.0
... | getEntityDetailHistory | 23.0 | 21.0
Relationship retrieval | getRelationship | 18.0 | 18.0
... | isRelationshipKnown | 18.0 | 18.0
Relationship history retrieval | getRelationship | 21.0 | 21.0
... | getRelationshipHistory | 22.0 | 22.0
Entity history search | findEntities | 64.0 | 84.0
... | findEntitiesByProperty | 34.0 | 35.0
... | findEntitiesByPropertyValue | 50.0 | 62.0
Relationship history search | findRelationships | 29.0 | 37.0
... | findRelationshipsByProperty | 37.0 | 45.5
... | findRelationshipsByPropertyValue | 54.0 | 79.5
Graph queries | getEntityNeighborhood | 31.0 | 28.0
... | getLinkingEntities | 21.0 | 30.0
... | getRelatedEntities | 641.0 | 1116.0
... | getRelationshipsForEntity | 28.0 | 28.0
Graph history queries | getEntityNeighborhood | 30.0 | 28.0
... | getLinkingEntities | 21.0 | 30.0
... | getRelatedEntities | 637.0 | 1120.0
... | getRelationshipsForEntity | 26.0 | 27.0
Entity re-home | reHomeEntity | 113.0 | 99.0
Relationship re-home | reHomeRelationship | 100.0 | 93.0
Entity declassify | declassifyEntity | 138.0 | 126.0
... | purgeClassificationReferenceCopy | 116.0 | 120.0
Entity re-identify | reIdentifyEntity | 118.0 | 114.0
Relationship re-identify | reIdentifyRelationship | 106.0 | 103.0
Relationship delete | deleteRelationship | 93.0 | 93.0
Entity delete | deleteEntity | 99.0 | 98.0
Entity restore | restoreEntity | 94.0 | 91.0
Relationship restore | restoreRelationship | 91.0 | 91.0
Relationship purge | purgeRelationship | 80.0 | 80.0
... | purgeRelationshipReferenceCopy | 66.0 | 66.0
Entity purge | purgeEntity | 94.0 | 91.0
... | purgeEntityReferenceCopy | 67.0 | 67.0

### Crux (connector 2.10, Egeria 2.10, Crux 21.05-1.17.0) vs JanusGraph (Egeria 2.10)

**Note**: the graph queries were disabled for JanusGraph in order to have results in a timely manner.
Unfortunately, the graph queries portion of the suite for a single metadata type had not completed after more than 3
hours in this connector, so the complete graph queries portion would take _more than a month of non-stop execution_ to
complete (even at the lowest volume (5-2) configuration). This portion of the tests was therefore explicitly skipped,
and hence no results are shown for those methods below.

In the graphical comparison, a point plot is used to show the typical execution time of each method at the different
volumes for the two repositories. Each point on the plot represents the _median_ execution time for that method, in a
given repository (`pts` = Crux, `janus` = JanusGraph), with a given volume of metadata (`5-2` = 4,470 instances,
`10-5` = 8,940 instances). The horizontal lines that appear around each point are confidence intervals calculated by
a bootstrapping process: in simple terms, the larger the horizontal line, the more variability there is for that
particular method's execution time (a singular median value is insufficient to represent such variability).

The Crux results can be difficult to see in detail due to the skew from the Janus results, so it may be easier to look
at this more granular comparison that drops the higher scales of Janus for readability of the Crux results:

![Graphical comparison without large Janus volumes](results/2.10-21.05-1.17.0-beta/repo_comparison_granular.png)

> Graphical comparison of repositories (without large Janus volumes)

Note that in almost all cases, the Crux repository is _significantly_ faster than JanusGraph: at most volumes completing
all methods in less than 200ms and with very little variability (horizontal confidence intervals are not even visible
for most methods). For JanusGraph, on the other hand, there is significant variability (in particular for methods like
`findEntitiesByClassification`), and there are numerous examples of the median execution time taking more than multiple
seconds.

Following is a table of the specific median values for each repository and volume (also including the results for
methods that are only currently implemented by the Crux repository connector, or only return in a sufficiently timely
manner to be included in the tests):

Profile | Method | 05-02 (Crux) | 05-02 (Janus) | 10-05 (Crux) | 10-05 (Janus) | 20-10 (Crux) | 20-10 (Janus) | 40-10 (Crux) | 40-10 (Janus) | 80-10 (Crux) | 80-10 (Janus)
---|---|---|---|---|---|---|---|---|---|---|---
Entity creation | addEntity | 103.0 | 451.5 | 108.0 | 448.5
... | saveEntityReferenceCopy | 96.0 | 448.0 | 104.0 | 447.0
Entity search | findEntities | 48.5 | 258.0 | 76.0 | 497.0
... | findEntitiesByProperty | 33.0 | 36.0 | 40.0 | 50.0
... | findEntitiesByPropertyValue | 49.0 | 87.0 | 74.0 | 112.0
Relationship creation | addRelationship | 104.0 | 153.0 | 105.0 | 145.0
... | saveRelationshipReferenceCopy | 107.0 | 444.0 | 107.0 | 384.0
Relationship search | findRelationships | 29.0 | 46.0 | 35.0 | 68.0
... | findRelationshipsByProperty | 30.0 | 42.0 | 36.0 | 63.0
... | findRelationshipsByPropertyValue | 48.0 | 76.0 | 62.0 | 96.0
Entity classification | classifyEntity | 163.0 | 948.0 | 172.0 | 860.5
... | saveClassificationReferenceCopy | 120.0 | 798.0 | 118.0 | 808.0
Classification search | findEntitiesByClassification | 40.0 | 579.0 | 54.0 | 1301.5
Entity update | reTypeEntity | 86.0 | 379.5 | 82.0 | 341.5
... | updateEntityProperties | 118.0 | 696.0 | 120.0 | 741.0
Relationship update | updateRelationshipProperties | 140.0 | 428.0 | 129.0 | 448.0
Classification update | updateEntityClassification | 218.0 | 1185.0 | 270.5 | 1244.0
Entity undo | undoEntityUpdate | 112.0 | -- | 112.0 | --
Relationship undo | undoRelationshipUpdate | 130.0 | -- | 127.0 | --
Entity retrieval | getEntityDetail | 17.0 | 17.0 | 16.0 | 17.0
... | getEntitySummary | 17.0 | 16.0 | 16.0 | 16.0
... | isEntityKnown | 17.0 | 18.0 | 16.0 | 17.0
Entity history retrieval | getEntityDetail | 20.0 | -- | 19.0 | --
... | getEntityDetailHistory | 23.0 | -- | 21.0 | --
Relationship retrieval | getRelationship | 18.0 | 19.0 | 18.0 | 19.0
... | isRelationshipKnown | 18.0 | 19.0 | 18.0 | 19.0
Relationship history retrieval | getRelationship | 21.0 | -- | 21.0 | --
... | getRelationshipHistory | 22.0 | -- | 22.0 | --
Entity history search | findEntities | 64.0 | -- | 84.0 | --
... | findEntitiesByProperty | 34.0 | -- | 35.0 | --
... | findEntitiesByPropertyValue | 50.0 | -- | 62.0 | --
Relationship history search | findRelationships | 29.0 | -- | 37.0 | --
... | findRelationshipsByProperty | 37.0 | -- | 45.5 | --
... | findRelationshipsByPropertyValue | 54.0 | -- | 79.5 | --
Graph queries | getEntityNeighborhood | 31.0 | -- | 28.0 | --
... | getLinkingEntities | 21.0 | -- | 30.0 | --
... | getRelatedEntities | 641.0 | -- | 1116.0 | --
... | getRelationshipsForEntity | 28.0 | -- | 28.0 | --
Graph history queries | getEntityNeighborhood | 30.0 | -- | 28.0 | --
... | getLinkingEntities | 21.0 | -- | 30.0 | --
... | getRelatedEntities | 637.0 | -- | 1120.0 | --
... | getRelationshipsForEntity | 26.0 | -- | 27.0 | --
Entity re-home | reHomeEntity | 113.0 | 700.0 | 99.0 | 679.0
Relationship re-home | reHomeRelationship | 100.0 | 370.5 | 93.0 | 382.0
Entity declassify | declassifyEntity | 138.0 | 1256.0 | 126.0 | 1320.0
... | purgeClassificationReferenceCopy | 116.0 | -- | 120.0 | --
Entity re-identify | reIdentifyEntity | 118.0 | 1623.0 | 114.0 | 1987.0
Relationship re-identify | reIdentifyRelationship | 106.0 | 751.0 | 103.0 | 840.5
Relationship delete | deleteRelationship | 93.0 | 349.0 | 93.0 | 380.0
Entity delete | deleteEntity | 99.0 | 700.0 | 98.0 | 784.0
Entity restore | restoreEntity | 94.0 | 755.0 | 91.0 | 764.5
Relationship restore | restoreRelationship | 91.0 | 340.0 | 91.0 | 396.0
Relationship purge | purgeRelationship | 80.0 | 121.0 | 80.0 | 167.0
... | purgeRelationshipReferenceCopy | 66.0 | 95.0 | 66.0 | 101.0
Entity purge | purgeEntity | 94.0 | 220.0 | 91.0 | 355.0
... | purgeEntityReferenceCopy | 67.0 | 231.0 | 67.0 | 242.0

----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.
