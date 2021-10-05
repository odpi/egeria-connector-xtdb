/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.readops;

import org.odpi.egeria.connectors.juxt.xtdb.mapping.RelationshipMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSMetadataCollection;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeErrorException;
import xtdb.api.IXtdbDatasource;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Implements the 'findRelationships' operation of the OMRS metadata collection interface.
 */
public class FindRelationships extends AbstractRelationshipSearchOperation {

    private final List<String> relationshipSubtypeGUIDs;
    private final SearchProperties matchProperties;

    /**
     * Create a new 'findRelationships' executable.
     * @param xtdb connectivity to XTDB
     * @param relationshipTypeGUID see XtdbOMRSMetadataCollection#findRelationships
     * @param relationshipSubtypeGUIDs see XtdbOMRSMetadataCollection#findRelationships
     * @param matchProperties see XtdbOMRSMetadataCollection#findRelationships
     * @param fromRelationshipElement see XtdbOMRSMetadataCollection#findRelationships
     * @param limitResultsByStatus see XtdbOMRSMetadataCollection#findRelationships
     * @param asOfTime see XtdbOMRSMetadataCollection#findRelationships
     * @param sequencingProperty see XtdbOMRSMetadataCollection#findRelationships
     * @param sequencingOrder see XtdbOMRSMetadataCollection#findRelationships
     * @param pageSize see XtdbOMRSMetadataCollection#findRelationships
     * @param userId of the user running the query
     * @see XtdbOMRSMetadataCollection#findRelationships(String, String, List, SearchProperties, int, List, Date, String, SequencingOrder, int)
     */
    public FindRelationships(XtdbOMRSRepositoryConnector xtdb,
                             String relationshipTypeGUID,
                             List<String> relationshipSubtypeGUIDs,
                             SearchProperties matchProperties,
                             int fromRelationshipElement,
                             List<InstanceStatus> limitResultsByStatus,
                             Date asOfTime,
                             String sequencingProperty,
                             SequencingOrder sequencingOrder,
                             int pageSize,
                             String userId) {
        super(xtdb,
                relationshipTypeGUID,
                fromRelationshipElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId);
        this.relationshipSubtypeGUIDs = relationshipSubtypeGUIDs;
        this.matchProperties = matchProperties;
    }

    /**
     * Interface that must be implemented to actually execute the query logic.
     * @param db the datasource against which to run the query
     * @return {@code Collection<List<?>>} of internal XT references (IDs) that match the query
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws TimeoutException if the query times out
     */
    @Override
    protected Collection<List<?>> runQuery(IXtdbDatasource db) throws TypeErrorException, TimeoutException {
        return searchXtdb(db,
                TypeDefCategory.RELATIONSHIP_DEF,
                typeGUID,
                relationshipSubtypeGUIDs,
                matchProperties,
                fromElement,
                limitResultsByStatus,
                null,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                RelationshipMapping.RELATIONSHIP_PROPERTIES_NS,
                userId
        );
    }

}
