/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.readops;

import org.odpi.egeria.connectors.juxt.xtdb.mapping.RelationshipMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSMetadataCollection;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeErrorException;
import xtdb.api.IXtdbDatasource;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Implements the 'findRelationshipsByPropertyValue' operation of the OMRS metadata collection interface.
 */
public class FindRelationshipsByPropertyValue extends AbstractRelationshipSearchOperation {

    private final String searchCriteria;

    /**
     * Create a new 'findRelationshipsByPropertyValue' executable.
     * @param xtdb connectivity to XTDB
     * @param relationshipTypeGUID see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param searchCriteria see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param fromRelationshipElement see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param limitResultsByStatus see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param asOfTime see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param sequencingProperty see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param sequencingOrder see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param pageSize see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param userId of the user running the query
     * @see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue(String, String, String, int, List, Date, String, SequencingOrder, int)
     */
    public FindRelationshipsByPropertyValue(XtdbOMRSRepositoryConnector xtdb,
                                       String relationshipTypeGUID,
                                       String searchCriteria,
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
        this.searchCriteria = searchCriteria;
    }

    /**
     * Interface that must be implemented to actually execute the query logic.
     * @param db the datasource against which to run the query
     * @return {@code Collection<List<?>>} of internal XT references (IDs) that match the query
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws RepositoryErrorException if there is any issue iterating through the results
     */
    @Override
    protected Collection<List<?>> runQuery(IXtdbDatasource db) throws TypeErrorException, RepositoryErrorException {
        return searchXtdbText(db,
                TypeDefCategory.RELATIONSHIP_DEF,
                typeGUID,
                searchCriteria,
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
