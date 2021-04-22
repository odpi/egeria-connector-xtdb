<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Test Suites

This directory contains information regarding the automated Test Suites and the Crux connector.

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
Metadata sharing | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Reference copies | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Metadata maintenance | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Dynamic types | UNKNOWN_STATUS | 2.8 | 2.8 | 21.04-1.16.0
Graph queries | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Historical search | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Entity proxies | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Soft-delete and restore | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Undo an update | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Reidentify instance | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Retype instance | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Rehome instance | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Entity search | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Relationship search | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Entity advanced search | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0
Relationship advanced search | CONFORMANT_FULL_SUPPORT | 2.8 | 2.8 | 21.04-1.16.0

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

### Crux (connector 2.8, Egeria 2.8, Crux 21.04-1.16.0)

**Note**: the results below show a near-linear growth in the elapsed time of search operations, which is of course
not desirable. This should be [fixed](https://github.com/odpi/egeria-connector-crux/issues/89) in the 2.9 release,
along with knock-on improvements to other areas that rely on the search operations (e.g. graph queries,
entity re-identification, and entity deletes).

Profile | Method | 5-2 | 10-5 | 20-10 | 40-10 | 80-10
---|---|---|---|---|---|---
Entity creation | addEntity | 95.0 | 93.0 | 94.0 | 93.0
... | saveEntityReferenceCopy | 88.0 | 89.0 | 91.0 | 90.0
Entity search | findEntities | 242.0 | 677.0 | 1167.0 | 2140.0
... | findEntitiesByProperty | 208.0 | 424.0 | 823.0 | 2018.0
... | findEntitiesByPropertyValue | 79.0 | 176.0 | 273.5 | 453.5
Relationship creation | addRelationship | 96.0 | 97.0 | 97.0 | 95.0
... | saveRelationshipReferenceCopy | 98.0 | 98.0 | 98.0 | 96.0
Relationship search | findRelationships | 462.0 | 1186.0 | 2203.5 | 5195.0
... | findRelationshipsByProperty | 354.0 | 760.0 | 1458.0 | 3554.0
... | findRelationshipsByPropertyValue | 58.5 | 99.0 | 206.5 | 363.5
Entity classification | classifyEntity | 126.0 | 139.5 | 132.0 | 129.0
... | saveClassificationReferenceCopy | 116.0 | 129.0 | 125.0 | 120.0
Classification search | findEntitiesByClassification | 39.0 | 55.0 | 73.0 | 97.0
Entity update | reTypeEntity | 97.0 | 95.0 | 96.0 | 94.0
... | updateEntityProperties | 109.0 | 116.0 | 115.0 | 111.0
Relationship update | updateRelationshipProperties | 118.0 | 125.0 | 123.0 | 115.0
Classification update | updateEntityClassification | 143.5 | 159.0 | 153.0 | 152.0
Entity undo | undoEntityUpdate | 105.0 | 112.0 | 113.0 | 109.0
Relationship undo | undoRelationshipUpdate | 116.0 | 124.0 | 124.0 | 112.0
Entity retrieval | getEntityDetail | 16.0 | 16.0 | 16.0 | 15.0
... | getEntitySummary | 15.5 | 16.0 | 16.0 | 15.0
... | isEntityKnown | 16.0 | 17.0 | 16.0 | 15.0
Entity history retrieval | getEntityDetail | 19.0 | 20.0 | 19.0 | 18.0
... | getEntityDetailHistory | 22.0 | 22.0 | 21.0 | 20.0
Relationship retrieval | getRelationship | 17.0 | 18.0 | 17.0 | 16.0
... | isRelationshipKnown | 17.0 | 18.0 | 17.0 | 16.0
Relationship history retrieval | getRelationship | 20.0 | 21.0 | 20.0 | 19.0
... | getRelationshipHistory | 22.0 | 22.0 | 21.0 | 20.0
Entity history search | findEntities | 265.0 | 674.0 | 1157.0 | 2365.0
... | findEntitiesByProperty | 43.0 | 60.0 | 81.0 | 117.0
... | findEntitiesByPropertyValue | 82.5 | 171.5 | 271.5 | 593.0
Relationship history search | findRelationships | 509.0 | 1299.0 | 2268.0 | 4608.5
... | findRelationshipsByProperty | 38.0 | 52.0 | 68.5 | 71.0
... | findRelationshipsByPropertyValue | 71.0 | 131.0 | 223.0 | 413.5
Graph queries | getEntityNeighborhood | 35.0 | 39.0 | 36.0 | --
... | getLinkingEntities | 21.0 | 37.0 | 34.0 | --
... | getRelatedEntities | 4019.0 | 9743.0 | 17602.5 | --
... | getRelationshipsForEntity | 197.0 | 459.0 | 781.0 | --
Graph history queries | getEntityNeighborhood | 34.5 | 38.0 | 35.0 | --
... | getLinkingEntities | 21.0 | 36.0 | 34.0 | --
... | getRelatedEntities | 4026.5 | 9829.0 | 17668.0 | --
... | getRelationshipsForEntity | 196.0 | 456.0 | 774.0 | --
Entity re-home | reHomeEntity | 111.0 | 104.0 | 118.0 | 112.0
Relationship re-home | reHomeRelationship | 102.0 | 104.0 | 111.0 | 104.0
Entity declassify | declassifyEntity | 127.0 | 133.0 | 136.0 | 130.0
... | purgeClassificationReferenceCopy | 118.0 | 122.0 | 131.0 | 114.0
Entity re-identify | reIdentifyEntity | 301.0 | 513.0 | 858.0 | 2080.0
Relationship re-identify | reIdentifyRelationship | 115.0 | 120.0 | 119.0 | 119.0
Relationship delete | deleteRelationship | 102.0 | 106.0 | 110.0 | 114.0
Entity delete | deleteEntity | 336.0 | 582.0 | 1028.5 | 2726.0
Entity restore | restoreEntity | 97.0 | 97.0 | 100.0 | 97.0
Relationship restore | restoreRelationship | 100.0 | 105.0 | 106.0 | 104.0
Relationship purge | purgeRelationship | 111.0 | 106.0 | 104.0 | 119.0
... | purgeRelationshipReferenceCopy | 95.0 | 95.0 | 95.0 | 106.0
Entity purge | purgeEntity | 113.0 | 112.0 | 110.0 | 130.0
... | purgeEntityReferenceCopy | 91.0 | 92.0 | 93.0 | 103.0

### Crux (connector 2.8, Egeria 2.8, Crux 21.04-1.16.0) vs JanusGraph (Egeria 2.8)

**Note**: the graph queries were disabled for JanusGraph in order to have results in a timely manner.
Unfortunately, the graph queries portion of the suite for a single metadata type had not completed after more than 3
hours in this connector, so the complete graph queries portion would take _more than a month of non-stop execution_ to
complete (even at the lowest volume (5-2) configuration). This portion of the tests was therefore explicitly skipped,
and hence no results are shown for those methods below.

Profile | Method | 5-2 (Crux) | 5-2 (Janus) | 10-5 (Crux) | 10-5 (Janus)
---|---|---|---|---|---
Entity creation | addEntity | 92.0 | 449.5 | 92.0 | 
... | saveEntityReferenceCopy | 85.0 | 447.0 | 88.0 | 
Entity search | findEntities | 240.0 | 238.5 | 558.0 | 
... | findEntitiesByProperty | 188.5 | 36.0 | 396.0 | 
... | findEntitiesByPropertyValue | 79.0 | 78.0 | 146.0 | 
Relationship creation | addRelationship | 93.0 | 153.0 | 94.0 | 
... | saveRelationshipReferenceCopy | 95.0 | 442.0 | 95.0 |  
Relationship search | findRelationships | 441.0 | 41.0 | 906.5 | 
... | findRelationshipsByProperty | 320.5 | 41.0 | 704.0 | 
... | findRelationshipsByPropertyValue | 56.5 | 66.0 | 90.5 | 
Entity classification | classifyEntity | 128.0 | 920.5 | 126.0 | 
... | saveClassificationReferenceCopy | 116.0 | 772.0 | 119.0 | 
Classification search | findEntitiesByClassification | 37.0 | 451.0 | 50.0 | 
Entity update | reTypeEntity | 89.0 | 350.0 | 94.0 | 
... | updateEntityProperties | 107.0 | 698.0 | 112.0 | 
Relationship update | updateRelationshipProperties | 120.0 | 430.0 | 119.0 | 
Classification update | updateEntityClassification | 193.0 | 1106.5 | 152.5 | 
Entity undo | undoEntityUpdate | 104.0 | -- | 106.0 | --
Relationship undo | undoRelationshipUpdate | 115.0 | -- | 117.0 | --
Entity retrieval | getEntityDetail | 15.0 | 17.0 | 15.0 | 
... | getEntitySummary | 15.0 | 16.0 | 15.0 | 
... | isEntityKnown | 15.0 | 17.0 | 15.0 | 
Entity history retrieval | getEntityDetail | 18.0 | -- | 18.0 | --
... | getEntityDetailHistory | 20.0 | -- | 20.0 | --
Relationship retrieval | getRelationship | 16.0 | 19.0 | 16.0 | 
... | isRelationshipKnown | 17.0 | 19.0 | 16.0 | 
Relationship history retrieval | getRelationship | 19.0 | -- | 19.0 | --
... | getRelationshipHistory | 20.0 | -- | 20.0 | --
Entity history search | findEntities | 256.0 | -- | 580.0 | --
... | findEntitiesByProperty | 41.0 | -- | 55.0 | --
... | findEntitiesByPropertyValue | 83.0 | -- | 148.5 | --
Relationship history search | findRelationships | 479.0 | -- | 1113.0 | --
... | findRelationshipsByProperty | 34.0 | -- | 47.0 | --
... | findRelationshipsByPropertyValue | 73.5 | -- | 126.5 | --
Graph queries | getEntityNeighborhood | 34.0 | -- | 35.0 | --
... | getLinkingEntities | 20.0 | -- | 33.0 | --
... | getRelatedEntities | 3988.0 | -- | 8401.0 | --
... | getRelationshipsForEntity | 188.0 | -- | 424.0 | --
Graph history queries | getEntityNeighborhood | 34.0 | -- | 35.0 | --
... | getLinkingEntities | 20.0 | -- | 32.0 | --
... | getRelatedEntities | 3977.0 | -- | 8422.5 | --
... | getRelationshipsForEntity | 184.0 | -- | 417.0 | --
Entity re-home | reHomeEntity | 101.0 | 699.0 | 110.0 | 
Relationship re-home | reHomeRelationship | 94.0 | 347.0 | 102.0 | 
Entity declassify | declassifyEntity | 121.0 | 1247.5 | 125.0 | 
... | purgeClassificationReferenceCopy | 109.0 | -- | 120.0 | --
Entity re-identify | reIdentifyEntity | 276.0 | 1583.5 | 493.0 | 
Relationship re-identify | reIdentifyRelationship | 105.0 | 682.0 | 118.0 | 
Relationship delete | deleteRelationship | 95.0 | 335.0 | 106.0 | 
Entity delete | deleteEntity | 299.0 | 668.5 | 566.0 | 
Entity restore | restoreEntity | 90.0 | 719.0 | 96.0 | 
Relationship restore | restoreRelationship | 94.0 | 376.0 | 103.0 | 
Relationship purge | purgeRelationship | 99.0 | 121.0 | 104.0 | 
... | purgeRelationshipReferenceCopy | 90.0 | 98.0 | 94.0 | 
Entity purge | purgeEntity | 103.0 | 206.0 | 110.0 | 
... | purgeEntityReferenceCopy | 86.0 | 235.0 | 90.0 | 

----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.
