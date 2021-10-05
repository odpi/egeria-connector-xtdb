/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.readops;

import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntityDetailMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSMetadataCollection;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchClassifications;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeErrorException;
import xtdb.api.IXtdbDatasource;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Implements the 'findEntitiesByPropertyValue' operation of the OMRS metadata collection interface.
 */
public class FindEntitiesByPropertyValue extends AbstractEntitySearchOperation {

    private final String searchCriteria;

    /**
     * Create a new 'findEntitiesByPropertyValue' executable.
     * @param xtdb connectivity to XTDB
     * @param entityTypeGUID see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param searchCriteria see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param fromEntityElement see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param limitResultsByStatus see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param matchClassifications see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param asOfTime see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param sequencingProperty see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param sequencingOrder see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param pageSize see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param userId of the user running the query
     * @see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue(String, String, String, int, List, List, Date, String, SequencingOrder, int)
     */
    public FindEntitiesByPropertyValue(XtdbOMRSRepositoryConnector xtdb,
                                       String entityTypeGUID,
                                       String searchCriteria,
                                       int fromEntityElement,
                                       List<InstanceStatus> limitResultsByStatus,
                                       SearchClassifications matchClassifications,
                                       Date asOfTime,
                                       String sequencingProperty,
                                       SequencingOrder sequencingOrder,
                                       int pageSize,
                                       String userId) {
        super(xtdb,
                entityTypeGUID,
                fromEntityElement,
                limitResultsByStatus,
                matchClassifications,
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
     * @throws TimeoutException if the query times out
     */
    @Override
    protected Collection<List<?>> runQuery(IXtdbDatasource db) throws TypeErrorException, TimeoutException {
        return searchXtdbText(db,
                TypeDefCategory.ENTITY_DEF,
                typeGUID,
                searchCriteria,
                fromElement,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                EntityDetailMapping.ENTITY_PROPERTIES_NS,
                userId
        );
    }

}
