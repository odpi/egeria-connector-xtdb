<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the Egeria project. -->

# Release 3.0

## CTS results

!!! success "The 3.0 release is fully conformant with all Egeria profiles"

Profile | Result | Connector | Egeria | Crux
---|---|---|---|---
Metadata sharing | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Reference copies | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Metadata maintenance | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Dynamic types | UNKNOWN_STATUS | 3.0 | 3.0 | 1.18.1
Graph queries | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Historical search | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Entity proxies | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Soft-delete and restore | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Undo an update | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Reidentify instance | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Retype instance | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Rehome instance | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Entity search | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Relationship search | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Entity advanced search | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1
Relationship advanced search | CONFORMANT_FULL_SUPPORT | 3.0 | 3.0 | 1.18.1

??? info "Additional notes"
    - The entity search tests could fail a particular long-running query pattern unless Lucene is configured: typically where a query by value or attribute is done without providing any restriction on the type of instances against which the query should run. Configure the connector with Lucene to avoid these timeouts.
    - The `Dynamic types` profile currently does not have any tests defined, so will be `UNKNOWN_STATUS` for all repositories and connectors.

## PTS results

### Crux at varying volumes

!!! summary
    The retrieval and write operations remain very consistent, with almost no variability, throughout the growth in volume. The search operations, however, begin to clearly degrade at the highest volumes tested. Further investigation into other optimized settings for the search operations for these larger volumes is likely warranted as the next step to continue to improve performance.

![Graphical comparison](volume_comparison.svg)

Profile | Method | 05-02 (4,630) | 10-05 (9,260)
---|---|---|---
Entity creation | addEntity | 51.0 | 45.0
... | saveEntityReferenceCopy | 48.0 | 42.0
Entity search | findEntities | 48.0 | 50.5
... | findEntitiesByProperty | 34.0 | 31.0
... | findEntitiesByPropertyValue | 52.0 | 60.0
Relationship creation | addRelationship | 47.0 | 42.0
... | saveRelationshipReferenceCopy | 49.0 | 44.0
Relationship search | findRelationships | 26.0 | 26.0
... | findRelationshipsByProperty | 34.0 | 29.0
... | findRelationshipsByPropertyValue | 42.0 | 53.0
Entity classification | classifyEntity | 74.0 | 69.0
... | saveClassificationReferenceCopy | 65.0 | 56.0
Classification search | findEntitiesByClassification | 37.0 | 39.0
Entity update | reTypeEntity | 51.0 | 40.0
... | updateEntityProperties | 54.0 | 49.0
Relationship update | updateRelationshipProperties | 60.0 | 50.0
Classification update | updateEntityClassification | 92.0 | 83.0
Entity undo | undoEntityUpdate | 52.0 | 43.0
Relationship undo | undoRelationshipUpdate | 57.0 | 48.0
Entity retrieval | getEntityDetail | 16.0 | 14.0
... | getEntitySummary | 17.0 | 15.0
... | isEntityKnown | 17.0 | 15.0
Entity history retrieval | getEntityDetail | 20.0 | 17.0
... | getEntityDetailHistory | 22.0 | 19.0
Relationship retrieval | getRelationship | 18.0 | 15.0
... | isRelationshipKnown | 18.0 | 16.0
Relationship history retrieval | getRelationship | 21.0 | 19.0
... | getRelationshipHistory | 22.0 | 20.0
Entity history search | findEntities | 64.0 | 76.5
... | findEntitiesByProperty | 31.0 | 31.0
... | findEntitiesByPropertyValue | 54.0 | 69.0
Relationship history search | findRelationships | 28.0 | 30.0
... | findRelationshipsByProperty | 31.0 | 37.0
... | findRelationshipsByPropertyValue | 55.0 | 75.0
Graph queries | getEntityNeighborhood | 27.0 | 24.0
... | getLinkingEntities | 21.0 | 23.0
... | getRelatedEntities | 546.0 | 871.0
... | getRelationshipsForEntity | 26.0 | 24.0
Graph history queries | getEntityNeighborhood | 27.0 | 23.0
... | getLinkingEntities | 21.0 | 23.0
... | getRelatedEntities | 548.5 | 875.0
... | getRelationshipsForEntity | 24.0 | 22.0
Entity re-home | reHomeEntity | 58.5 | 43.0
Relationship re-home | reHomeRelationship | 56.0 | 39.0
Entity declassify | declassifyEntity | 80.0 | 56.0
... | purgeClassificationReferenceCopy | 65.0 | 50.0
Entity re-identify | reIdentifyEntity | 61.0 | 48.0
Relationship re-identify | reIdentifyRelationship | 50.0 | 40.0
Relationship delete | deleteRelationship | 48.0 | 37.0
Entity delete | deleteEntity | 56.0 | 42.0
Entity restore | restoreEntity | 44.0 | 37.0
Relationship restore | restoreRelationship | 43.0 | 33.0
Relationship purge | purgeRelationship | 38.0 | 28.0
... | purgeRelationshipReferenceCopy | 28.0 | 21.0
Entity purge | purgeEntity | 55.0 | 35.0
... | purgeEntityReferenceCopy | 32.0 | 21.0

### Crux vs JanusGraph

!!! summary
    In almost all cases, the Crux repository is *significantly* faster than JanusGraph: at most volumes completing all methods in less than 100ms and with very little variability. For JanusGraph, on the other hand, there is significant variability (in particular for methods like `findEntitiesByClassification`), and there are numerous examples of the median execution time taking more than multiple seconds.

    Even at 8 times the volume of metadata the Crux connector still outperforms the JanusGraph connector in almost every method (the only exceptions being a few of the find methods, where the performance is approximately even at 2-4 times the volume).

!!! attention "Graph queries were disabled for JanusGraph"
    The graph queries were disabled for JanusGraph in order to have results in a timely manner: it would take more than a month to produce results for these queries for the JanusGraph connector.

![Graphical comparison](repo_comparison.svg)

The Crux results can be difficult to see in detail due to the skew from the Janus results, so it may be easier to look at this more granular comparison that drops the higher scales of Janus for readability of the Crux results:

![Graphical comparison without large Janus volumes](repo_comparison_granular.svg)

Profile | Method | 05-02 (Crux) | 05-02 (Janus) | 10-05 (Crux) | 10-05 (Janus)
---|---|---|---|---|---
Entity creation | addEntity | 51.0 | 434.0 | 45.0 | 466.5
... | saveEntityReferenceCopy | 48.0 | 440.5 | 42.0 | 469.0
Entity search | findEntities | 48.0 | 224.0 | 50.5 | 402.0
... | findEntitiesByProperty | 34.0 | 36.0 | 31.0 | 48.0
... | findEntitiesByPropertyValue | 52.0 | 73.0 | 60.0 | 90.0
Relationship creation | addRelationship | 47.0 | 151.0 | 42.0 | 154.5
... | saveRelationshipReferenceCopy | 49.0 | 424.0 | 44.0 | 461.5
Relationship search | findRelationships | 26.0 | 40.0 | 26.0 | 60.0
... | findRelationshipsByProperty | 34.0 | 39.0 | 29.0 | 57.0
... | findRelationshipsByPropertyValue | 42.0 | 66.0 | 53.0 | 88.0
Entity classification | classifyEntity | 74.0 | 873.0 | 69.0 | 865.0
... | saveClassificationReferenceCopy | 65.0 | 731.0 | 56.0 | 801.5
Classification search | findEntitiesByClassification | 37.0 | 545.0 | 39.0 | 917.5
Entity update | reTypeEntity | 51.0 | 382.0 | 40.0 | 369.0
... | updateEntityProperties | 54.0 | 759.0 | 49.0 | 723.0
Relationship update | updateRelationshipProperties | 60.0 | 422.0 | 50.0 | 418.0
Classification update | updateEntityClassification | 92.0 | 1191.0 | 83.0 | 1187.0
Entity undo | undoEntityUpdate | 52.0 | -- | 43.0 | --
Relationship undo | undoRelationshipUpdate | 57.0 | -- | 48.0 | --
Entity retrieval | getEntityDetail | 16.0 | 18.0 | 14.0 | 17.0
... | getEntitySummary | 17.0 | 17.0 | 15.0 | 17.0
... | isEntityKnown | 17.0 | 18.0 | 15.0 | 17.0
Entity history retrieval | getEntityDetail | 20.0 | -- | 17.0 | --
... | getEntityDetailHistory | 22.0 | -- | 19.0 | --
Relationship retrieval | getRelationship | 18.0 | 19.0 | 15.0 | 19.0
... | isRelationshipKnown | 18.0 | 20.0 | 16.0 | 19.0
Relationship history retrieval | getRelationship | 21.0 | -- | 19.0 | --
... | getRelationshipHistory | 22.0 | -- | 20.0 | --
Entity history search | findEntities | 64.0 | -- | 76.5 | --
... | findEntitiesByProperty | 31.0 | -- | 31.0 | --
... | findEntitiesByPropertyValue | 54.0 | -- | 69.0 | --
Relationship history search | findRelationships | 28.0 | -- | 30.0 | --
... | findRelationshipsByProperty | 31.0 | -- | 37.0 | --
... | findRelationshipsByPropertyValue | 55.0 | -- | 75.0 | --
Graph queries | getEntityNeighborhood | 27.0 | -- | 24.0 | --
... | getLinkingEntities | 21.0 | -- | 23.0 | --
... | getRelatedEntities | 546.0 | -- | 871.0 | --
... | getRelationshipsForEntity | 26.0 | -- | 24.0 | --
Graph history queries | getEntityNeighborhood | 27.0 | -- | 23.0 | --
... | getLinkingEntities | 21.0 | -- | 23.0 | --
... | getRelatedEntities | 548.5 | -- | 875.0 | --
... | getRelationshipsForEntity | 24.0 | -- | 22.0 | --
Entity re-home | reHomeEntity | 58.5 | 731.5 | 43.0 | 722.0
Relationship re-home | reHomeRelationship | 56.0 | 363.0 | 39.0 | 358.0
Entity declassify | declassifyEntity | 80.0 | 1225.5 | 56.0 | 1308.0
... | purgeClassificationReferenceCopy | 65.0 | -- | 50.0 | --
Entity re-identify | reIdentifyEntity | 61.0 | 1711.0 | 48.0 | 1725.5
Relationship re-identify | reIdentifyRelationship | 50.0 | 779.0 | 40.0 | 799.5
Relationship delete | deleteRelationship | 48.0 | 362.0 | 37.0 | 375.0
Entity delete | deleteEntity | 56.0 | 738.0 | 42.0 | 763.0
Entity restore | restoreEntity | 44.0 | 769.0 | 37.0 | 815.0
Relationship restore | restoreRelationship | 43.0 | 339.0 | 33.0 | 380.0
Relationship purge | purgeRelationship | 38.0 | 125.0 | 28.0 | 162.5
... | purgeRelationshipReferenceCopy | 28.0 | 104.0 | 21.0 | 100.0
Entity purge | purgeEntity | 55.0 | 234.0 | 35.0 | 315.0
... | purgeEntityReferenceCopy | 32.0 | 243.0 | 21.0 | 218.0

--8<-- "snippets/abbr.md"
