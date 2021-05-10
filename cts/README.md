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
Metadata sharing | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Reference copies | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Metadata maintenance | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Dynamic types | UNKNOWN_STATUS | 2.9 | 2.9 | 21.04-1.16.0
Graph queries | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Historical search | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Entity proxies | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Soft-delete and restore | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Undo an update | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Reidentify instance | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Retype instance | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Rehome instance | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Entity search | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Relationship search | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Entity advanced search | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0
Relationship advanced search | CONFORMANT_FULL_SUPPORT | 2.9 | 2.9 | 21.04-1.16.0

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

### Crux (connector 2.9, Egeria 2.9, Crux 21.04-1.16.0)

![Graphical comparison](results/2.9-21.04-1.16.0-beta/volume_comparison.png)

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

Profile | Method | 05-02 (4,410) | 10-05 (8,820) | 20-10 (17,640) | 40-10 (35,280) | 80-10 (70,560)
---|---|---|---|---|---|---
Entity creation | addEntity | 95.0 | 94.0 | 92.0 | 92.0 | 93.0
... | saveEntityReferenceCopy | 88.0 | 90.0 | 89.0 | 88.0 | 90.0
Entity search | findEntities | 43.0 | 72.0 | 108.5 | 221.0 | 511.0
... | findEntitiesByProperty | 32.0 | 45.0 | 54.0 | 97.0 | 194.0
... | findEntitiesByPropertyValue | 41.0 | 70.0 | 103.0 | 126.0 | 255.5
Relationship creation | addRelationship | 98.0 | 96.0 | 95.0 | 98.0 | 102.0
... | saveRelationshipReferenceCopy | 99.0 | 95.0 | 95.0 | 97.0 | 100.0
Relationship search | findRelationships | 27.0 | 33.0 | 41.0 | 40.0 | 45.0
... | findRelationshipsByProperty | 32.0 | 46.0 | 53.0 | 106.0 | 218.0
... | findRelationshipsByPropertyValue | 41.0 | 52.0 | 70.5 | 104.0 | 209.0
Entity classification | classifyEntity | 129.0 | 132.0 | 133.0 | 127.0 | 134.0
... | saveClassificationReferenceCopy | 121.0 | 119.0 | 125.0 | 119.0 | 125.0
Classification search | findEntitiesByClassification | 38.0 | 50.0 | 66.0 | 98.0 | 141.0
Entity update | reTypeEntity | 91.0 | 104.0 | 85.0 | 92.0 | 95.0
... | updateEntityProperties | 114.0 | 113.0 | 113.0 | 110.0 | 117.0
Relationship update | updateRelationshipProperties | 127.0 | 121.0 | 122.0 | 119.0 | 122.0
Classification update | updateEntityClassification | 150.0 | 150.0 | 157.0 | 147.5 | 157.0
Entity undo | undoEntityUpdate | 111.0 | 107.0 | 110.0 | 109.0 | 112.0
Relationship undo | undoRelationshipUpdate | 123.0 | 122.0 | 120.0 | 117.0 | 123.0
Entity retrieval | getEntityDetail | 15.0 | 15.0 | 16.0 | 14.0 | 16.0
... | getEntitySummary | 15.0 | 15.0 | 16.0 | 14.0 | 15.0
... | isEntityKnown | 15.0 | 15.0 | 16.0 | 14.0 | 16.0
Entity history retrieval | getEntityDetail | 18.0 | 18.0 | 19.0 | 17.0 | 18.0
... | getEntityDetailHistory | 20.0 | 19.0 | 20.0 | 19.0 | 20.0
Relationship retrieval | getRelationship | 17.0 | 16.0 | 17.0 | 15.0 | 17.0
... | isRelationshipKnown | 17.0 | 16.0 | 17.0 | 15.0 | 17.0
Relationship history retrieval | getRelationship | 20.0 | 18.0 | 20.0 | 18.0 | 19.0
... | getRelationshipHistory | 21.0 | 19.0 | 21.0 | 19.0 | 21.0
Entity history search | findEntities | 66.0 | 96.0 | 173.0 | 577.5 | 1586.0
... | findEntitiesByProperty | 36.0 | 40.0 | 50.0 | 61.0 | 90.0
... | findEntitiesByPropertyValue | 51.0 | 66.0 | 108.0 | 195.0 | 502.0
Relationship history search | findRelationships | 33.0 | 41.0 | 50.0 | 52.0 | 60.0
... | findRelationshipsByProperty | 38.0 | 50.0 | 61.0 | 65.0 | 76.0
... | findRelationshipsByPropertyValue | 52.5 | 86.0 | 113.0 | 157.0 | 305.5
Graph queries | getEntityNeighborhood | 32.0 | 35.0 | 35.0 | -- | --
... | getLinkingEntities | 20.0 | 38.0 | 3230.0 | -- | --
... | getRelatedEntities | 1909.0 | 4028.5 | 9125.5 | -- | --
... | getRelationshipsForEntity | 29.0 | 33.0 | 33.0 | -- | --
Graph history queries | getEntityNeighborhood | 33.0 | 35.0 | 35.0 | -- | --
... | getLinkingEntities | 20.0 | 37.0 | 3218.5 | -- | --
... | getRelatedEntities | 1904.0 | 4044.0 | 9058.5 | -- | --
... | getRelationshipsForEntity | 28.0 | 31.0 | 31.0 | -- | --
Entity re-home | reHomeEntity | 107.0 | 104.0 | 103.0 | 112.0 | 115.0
Relationship re-home | reHomeRelationship | 100.0 | 98.0 | 98.0 | 106.0 | 111.0
Entity declassify | declassifyEntity | 120.0 | 131.0 | 120.0 | 132.0 | 139.0
... | purgeClassificationReferenceCopy | 109.0 | 119.0 | 110.0 | 118.0 | 121.0
Entity re-identify | reIdentifyEntity | 126.0 | 154.0 | 122.0 | 133.0 | 139.0
Relationship re-identify | reIdentifyRelationship | 112.0 | 127.0 | 107.0 | 119.0 | 126.0
Relationship delete | deleteRelationship | 101.0 | 108.0 | 97.0 | 110.0 | 119.0
Entity delete | deleteEntity | 108.0 | 115.0 | 106.0 | 119.0 | 128.0
Entity restore | restoreEntity | 97.0 | 102.0 | 93.0 | 101.0 | 108.0
Relationship restore | restoreRelationship | 99.0 | 105.0 | 94.0 | 104.0 | 116.0
Relationship purge | purgeRelationship | 106.0 | 118.0 | 101.0 | 115.0 | 120.0
... | purgeRelationshipReferenceCopy | 95.0 | 103.0 | 93.0 | 104.0 | 110.0
Entity purge | purgeEntity | 113.0 | 119.0 | 107.0 | 120.0 | 131.0
... | purgeEntityReferenceCopy | 91.0 | 97.0 | 89.0 | 100.0 | 108.0

### Crux (connector 2.9, Egeria 2.9, Crux 21.04-1.16.0) vs JanusGraph (Egeria 2.9)

**Note**: the graph queries were disabled for JanusGraph in order to have results in a timely manner.
Unfortunately, the graph queries portion of the suite for a single metadata type had not completed after more than 3
hours in this connector, so the complete graph queries portion would take _more than a month of non-stop execution_ to
complete (even at the lowest volume (5-2) configuration). This portion of the tests was therefore explicitly skipped,
and hence no results are shown for those methods below.

(At the largest test scale of `80-10`, the Janus connector also stopped logging any activity after 5 days of non-stop
execution, but did not complete the battery of tests, so no results are logged.)

![Graphical comparison](results/2.9-21.04-1.16.0-beta/repo_comparison.png)

> Graphical comparison of repositories

In the graphical comparison, a point plot is used to show the typical execution time of each method at the different
volumes for the two repositories. Each point on the plot represents the _median_ execution time for that method, in a
given repository (`pts` = Crux, `janus` = JanusGraph), with a given volume of metadata (`5-2` = 4,410 instances,
`10-5` = 8,820 instances). The horizontal lines that appear around each point are confidence intervals calculated by
a bootstrapping process: in simple terms, the larger the horizontal line, the more variability there is for that
particular method's execution time (a singular median value is insufficient to represent such variability).

The Crux results can be difficult to see in detail due to the skew from the Janus results, so it may be easier to look
at this more granular comparison that drops the higher scales of Janus for readability of the Crux results:

![Graphical comparison without large Janus volumes](results/2.9-21.04-1.16.0-beta/repo_comparison_granular.png)

> Graphical comparison of repositories (without large Janus volumes)

Note that in almost all cases, the Crux repository is _significantly_ faster than JanusGraph: at most volumes completing
all methods in less than 200ms and with very little variability (horizontal confidence intervals are not even visible
for most methods). For JanusGraph, on the other hand, there is significant variability (in particular for methods like
`findEntitiesByClassification`), and there are numerous examples of the median execution time taking more than multiple
seconds.

In fact, from this second chart, you can see that even at 8 times the volume of metadata the Crux connector still
outperforms the JanusGraph connector in almost every method (the only exceptions being a few of the find methods, where
the performance is approximately even at 2-4 times the volume).

Following is a table of the specific median values for each repository and volume (also including the results for
methods that are only currently implemented by the Crux repository connector, or only return in a sufficiently timely
manner to be included in the tests):

Profile | Method | 05-02 (Crux) | 05-02 (Janus) | 10-05 (Crux) | 10-05 (Janus) | 20-10 (Crux) | 20-10 (Janus) | 40-10 (Crux) | 40-10 (Janus) | 80-10 (Crux) | 80-10 (Janus)
---|---|---|---|---|---|---|---|---|---|---|---
Entity creation | addEntity | 95.0 | 426.5 | 94.0 | 422.0 | 92.0 | 449.0 | 92.0 | 473.0 | 93.0 | DNF
... | saveEntityReferenceCopy | 88.0 | 424.0 | 90.0 | 420.0 | 89.0 | 452.0 | 88.0 | 470.0 | 90.0 | DNF
Entity search | findEntities | 43.0 | 259.0 | 72.0 | 435.0 | 108.5 | 873.0 | 221.0 | 2539.5 | 511.0 | DNF
... | findEntitiesByProperty | 32.0 | 38.0 | 45.0 | 52.0 | 54.0 | 77.0 | 97.0 | 128.0 | 194.0 | DNF
... | findEntitiesByPropertyValue | 41.0 | 80.0 | 70.0 | 106.0 | 103.0 | 128.0 | 126.0 | 186.0 | 255.5 | DNF
Relationship creation | addRelationship | 98.0 | 151.0 | 96.0 | 147.0 | 95.0 | 151.0 | 98.0 | 153.0 | 102.0 | DNF
... | saveRelationshipReferenceCopy | 99.0 | 411.0 | 95.0 | 415.0 | 95.0 | 410.0 | 97.0 | 449.5 | 100.0 | DNF
Relationship search | findRelationships | 27.0 | 46.0 | 33.0 | 70.0 | 41.0 | 109.0 | 40.0 | 186.0 | 45.0 | DNF
... | findRelationshipsByProperty | 32.0 | 44.0 | 46.0 | 64.0 | 53.0 | 99.5 | 106.0 | 174.0 | 218.0 | DNF
... | findRelationshipsByPropertyValue | 41.0 | 72.0 | 52.0 | 98.0 | 70.5 | 132.5 | 104.0 | 225.0 | 209.0 | DNF
Entity classification | classifyEntity | 129.0 | 888.0 | 132.0 | 860.0 | 133.0 | 937.0 | 127.0 | 924.0 | 134.0 | DNF
... | saveClassificationReferenceCopy | 121.0 | 734.0 | 119.0 | 792.0 | 125.0 | 894.5 | 119.0 | 853.0 | 125.0 | DNF
Classification search | findEntitiesByClassification | 38.0 | 649.0 | 50.0 | 1111.5 | 66.0 | 2459.0 | 98.0 | 4272.0 | 141.0 | DNF
Entity update | reTypeEntity | 91.0 | 348.0 | 104.0 | 343.0 | 85.0 | 495.5 | 92.0 | 459.0 | 95.0 | DNF
... | updateEntityProperties | 114.0 | 679.0 | 113.0 | 681.5 | 113.0 | 805.0 | 110.0 | 747.0 | 117.0 | DNF
Relationship update | updateRelationshipProperties | 127.0 | 418.0 | 121.0 | 404.0 | 122.0 | 443.0 | 119.0 | 453.0 | 122.0 | DNF
Classification update | updateEntityClassification | 150.0 | 1164.0 | 150.0 | 1170.5 | 157.0 | 1381.0 | 147.5 | 1308.0 | 157.0 | DNF
Entity undo | undoEntityUpdate | 111.0 | -- | 107.0 | -- | 110.0 | -- | 109.0 | -- | 112.0 | --
Relationship undo | undoRelationshipUpdate | 123.0 | -- | 122.0 | -- | 120.0 | -- | 117.0 | -- | 123.0 | --
Entity retrieval | getEntityDetail | 15.0 | 17.0 | 15.0 | 16.0 | 16.0 | 16.0 | 14.0 | 17.0 | 16.0 | DNF
... | getEntitySummary | 15.0 | 16.0 | 15.0 | 15.0 | 16.0 | 14.0 | 14.0 | 15.0 | 15.0 | DNF
... | isEntityKnown | 15.0 | 17.0 | 15.0 | 16.0 | 16.0 | 16.0 | 14.0 | 17.0 | 16.0 | DNF
Entity history retrieval | getEntityDetail | 18.0 | -- | 18.0 | -- | 19.0 | -- | 17.0 | -- | 18.0 | --
... | getEntityDetailHistory | 20.0 | -- | 19.0 | -- | 20.0 | -- | 19.0 | -- | 20.0 | --
Relationship retrieval | getRelationship | 17.0 | 20.0 | 16.0 | 18.0 | 17.0 | 18.0 | 15.0 | 19.0 | 17.0 | DNF
... | isRelationshipKnown | 17.0 | 20.0 | 16.0 | 18.0 | 17.0 | 18.0 | 15.0 | 19.0 | 17.0 | DNF
Relationship history retrieval | getRelationship | 20.0 | -- | 18.0 | -- | 20.0 | -- | 18.0 | -- | 19.0 | --
... | getRelationshipHistory | 21.0 | -- | 19.0 | -- | 21.0 | -- | 19.0 | -- | 21.0 | --
Entity history search | findEntities | 66.0 | -- | 96.0 | -- | 173.0 | -- | 577.5 | -- | 1586.0 | --
... | findEntitiesByProperty | 36.0 | -- | 40.0 | -- | 50.0 | -- | 61.0 | -- | 90.0 | --
... | findEntitiesByPropertyValue | 51.0 | -- | 66.0 | -- | 108.0 | -- | 195.0 | -- | 502.0 | --
Relationship history search | findRelationships | 33.0 | -- | 41.0 | -- | 50.0 | -- | 52.0 | -- | 60.0 | --
... | findRelationshipsByProperty | 38.0 | -- | 50.0 | -- | 61.0 | -- | 65.0 | -- | 76.0 | --
... | findRelationshipsByPropertyValue | 52.5 | -- | 86.0 | -- | 113.0 | -- | 157.0 | -- | 305.5 | --
Graph queries | getEntityNeighborhood | 32.0 | -- | 35.0 | -- | 35.0 | -- | -- | -- | -- | --
... | getLinkingEntities | 20.0 | -- | 38.0 | -- | 3230.0 | -- | -- | -- | -- | --
... | getRelatedEntities | 1909.0 | -- | 4028.5 | -- | 9125.5 | -- | -- | -- | -- | --
... | getRelationshipsForEntity | 29.0 | -- | 33.0 | -- | 33.0 | -- | -- | -- | -- | --
Graph history queries | getEntityNeighborhood | 33.0 | -- | 35.0 | -- | 35.0 | -- | -- | -- | -- | --
... | getLinkingEntities | 20.0 | -- | 37.0 | -- | 3218.5 | -- | -- | -- | -- | --
... | getRelatedEntities | 1904.0 | -- | 4044.0 | -- | 9058.5 | -- | -- | -- | -- | --
... | getRelationshipsForEntity | 28.0 | -- | 31.0 | -- | 31.0 | -- | -- | -- | -- | --
Entity re-home | reHomeEntity | 107.0 | 700.0 | 104.0 | 659.0 | 103.0 | 764.0 | 112.0 | 781.5 | 115.0 | DNF
Relationship re-home | reHomeRelationship | 100.0 | 348.0 | 98.0 | 341.0 | 98.0 | 427.0 | 106.0 | 383.0 | 111.0 | DNF
Entity declassify | declassifyEntity | 120.0 | 1191.0 | 131.0 | 1249.0 | 120.0 | 1415.5 | 132.0 | 1465.0 | 139.0 | DNF
... | purgeClassificationReferenceCopy | 109.0 | -- | 119.0 | -- | 110.0 | -- | 118.0 | -- | 121.0 | --
Entity re-identify | reIdentifyEntity | 126.0 | 1599.0 | 154.0 | 1606.5 | 122.0 | 1879.5 | 133.0 | 1739.5 | 139.0 | DNF
Relationship re-identify | reIdentifyRelationship | 112.0 | 728.5 | 127.0 | 733.0 | 107.0 | 806.0 | 119.0 | 789.0 | 126.0 | DNF
Relationship delete | deleteRelationship | 101.0 | 352.5 | 108.0 | 341.0 | 97.0 | 383.0 | 110.0 | 377.0 | 119.0 | DNF
Entity delete | deleteEntity | 108.0 | 681.0 | 115.0 | 709.0 | 106.0 | 797.0 | 119.0 | 796.0 | 128.0 | DNF
Entity restore | restoreEntity | 97.0 | 743.0 | 102.0 | 749.5 | 93.0 | 831.0 | 101.0 | 789.0 | 108.0 | DNF
Relationship restore | restoreRelationship | 99.0 | 352.5 | 105.0 | 339.0 | 94.0 | 371.0 | 104.0 | 370.0 | 116.0 | DNF
Relationship purge | purgeRelationship | 106.0 | 124.0 | 118.0 | 153.0 | 101.0 | 161.0 | 115.0 | 168.0 | 120.0 | DNF
... | purgeRelationshipReferenceCopy | 95.0 | 96.0 | 103.0 | 93.0 | 93.0 | 93.0 | 104.0 | 94.0 | 110.0 | DNF
Entity purge | purgeEntity | 113.0 | 223.0 | 119.0 | 316.0 | 107.0 | 334.0 | 120.0 | 345.5 | 131.0 | DNF
... | purgeEntityReferenceCopy | 91.0 | 230.0 | 97.0 | 214.0 | 89.0 | 217.0 | 100.0 | 212.0 | 108.0 | DNF

----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.
