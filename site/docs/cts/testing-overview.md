<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Testing overview

## Conformance Test Suite (CTS)

The Conformance Test Suite (CTS) measures conformance of the repository with the expected behavior of an Egeria
repository. Conformance indicates that the repository behaves precisely as expected for an Egeria repository.

## Performance Test Suite (PTS)

The Performance Test Suite (PTS) focuses on measuring the performance of the various operations of an Egeria repository.
It does not do as thorough checking of every individual result as the CTS, but instead ensures a consistent volume of
metadata exists in the environment for the duration of the tests and records various metrics about both these volumes
and the individual runtimes of each execution (in milliseconds) of the various metadata repository operations.

<!-- Following is a table of the specific median values for each repository and volume (also including the results for
methods that are only currently implemented by the Crux repository connector, or only return in a sufficiently timely
manner to be included in the tests): -->

??? info "Details on the performance metrics"
    The _median_ of all results for that method across all executions for a given set of volume parameters is given
    (all times in milliseconds) to give an idea of the "typical" result, while limiting potential skew from significant
    outliers.

    A more detailed set of statistics is best reviewed through the Jupyter Notebook provided in each results directory,
    where you can review:

    - the full distributions of execution times (including the outliers)
    - detailed individual outlier results (e.g. the top-10 slowest response times per method)
    - volumes in place during the tests (how many entities, how many relationships, etc)
    
    The volume parameters that were used for each test are specified using the convention `i-s`, where `i` is the value
    for the `instancesPerType` parameter to the PTS and `s` is the value for `maxSearchResults`. For example, `5-2` means
    5 instances will be created for every open metadata type and 2 will be the maximum number of results per page for
    methods that include paging.

    - All tests are run from `5-2` through `20-10` to give a sense of the performance impact of doubling the number
      of instances and search results.
    - Above this, the graph queries are no longer included: they become exponentially more complex as the volumes grow, and
      while they will still return results, the depth of their testing in the PTS means that they can contribute many hours
      (or even days) to the overall suite execution -- they are therefore left out to be able to more quickly produce
      results for the other methods at progressively higher volumes.
    - The page size is left at a maximum of `10` for subsequent tests so that it is only the volume of instances in total
      that are doubling each time, rather than also the number of detailed results.
    - Instance counts range from a few thousand (at `5-2`) up to nearly one hundred thousand (at `80-10`).
    
    In the graphical comparisons, a point plot is used to show the typical execution time of each method at the different
    volumes / by repository. Each point on the plot represents the _median_ execution time for that method, at a given
    volume of metadata. (For the repository comparison plots, `pts` = Crux and `janus` = JanusGraph.) The horizontal lines
    that appear around each point are confidence intervals calculated by a bootstrapping process: in simple terms, the
    larger the horizontal line, the more variability there is for that particular method's execution time (a singular
    median value is insufficient to represent such variability on its own).

## Reproducibility

### Re-running the tests

The `cts/charts` directory contains a Helm chart to automate the execution of these suites against a Crux repository
connector, to reproduce these results.

These use a default configuration for the Crux repository where Lucene is used as a text index and RocksDB is used for
all persistence: index store, document store and transaction log. No additional tuning of any parameters (Crux or RocksDB)
is applied: they use all of their default settings.

### Data points

The `cts/results` directory contains results of running the suites against the Crux connector. For each test suite execution,
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

--8<-- "snippets/abbr.md"
