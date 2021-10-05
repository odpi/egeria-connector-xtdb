/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.readops;

import clojure.lang.IPersistentMap;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.Constants;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntitySummaryMapping;
import org.odpi.egeria.connectors.juxt.xtdb.model.search.XtdbQuery;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.IXtdbDatasource;

import java.util.Collection;
import java.util.List;

/**
 * Implements the 'getRelationshipsForEntity' operation of the OMRS metadata collection interface.
 */
public class GetRelationshipsForEntity extends AbstractRelationshipSearchOperation {

    private static final Logger log = LoggerFactory.getLogger(GetRelationshipsForEntity.class);

    private final String entityGUID;

    /**
     * Create a new 'getRelationshipsForEntity' executable.
     * @param xtdb connectivity to XTDB
     * @param entityGUID of the entity for which to find relationships
     * @param relationshipTypeGUID to limit the relationship types to retrieve (optional)
     * @param fromRelationshipElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param existingDB already-opened datasource against which to do the retrievals
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @param userId of the user running the query
     */
    public GetRelationshipsForEntity(XtdbOMRSRepositoryConnector xtdb,
                                     String entityGUID,
                                     String relationshipTypeGUID,
                                     int fromRelationshipElement,
                                     List<InstanceStatus> limitResultsByStatus,
                                     IXtdbDatasource existingDB,
                                     String sequencingProperty,
                                     SequencingOrder sequencingOrder,
                                     int pageSize,
                                     String userId) {
        super(xtdb,
                relationshipTypeGUID,
                fromRelationshipElement,
                limitResultsByStatus,
                existingDB,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId);
        this.entityGUID = entityGUID;
    }

    /**
     * Interface that must be implemented to actually execute the query logic.
     * @param db the datasource against which to run the query
     * @return {@code Collection<List<?>>} of internal XT references (IDs) that match the query
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    @Override
    protected Collection<List<?>> runQuery(IXtdbDatasource db) throws TypeErrorException {
        XtdbQuery query = new XtdbQuery();
        query.addRelationshipEndpointConditions(EntitySummaryMapping.getReference(entityGUID));
        updateQuery(query,
                TypeDefCategory.RELATIONSHIP_DEF,
                typeGUID,
                null,
                null,
                limitResultsByStatus,
                null,
                sequencingProperty,
                sequencingOrder,
                null,
                userId);
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = db.query(q);
        // Note: we de-duplicate and apply paging here, against the full set of results from XTDB
        return deduplicateAndPage(results, fromElement, pageSize);
    }

}
