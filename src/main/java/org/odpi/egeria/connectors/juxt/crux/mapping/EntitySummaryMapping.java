/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.Classification;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntitySummary;

import java.util.List;
import java.util.Map;

/**
 * Maps the properties of EntitySummaries between persistence and objects.
 */
public class EntitySummaryMapping extends InstanceHeaderMapping {

    public static final String INSTANCE_REF_PREFIX = "entity";
    public static final String N_CLASSIFICATIONS = "classifications";

    /**
     * Construct a mapping from an EntityDetail (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param entitySummary from which to map
     */
    public EntitySummaryMapping(CruxOMRSRepositoryConnector cruxConnector,
                                EntitySummary entitySummary) {
        super(cruxConnector, entitySummary);
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxMap from which to map
     */
    public EntitySummaryMapping(CruxOMRSRepositoryConnector cruxConnector,
                                Map<Keyword, Object> cruxMap) {
        super(cruxConnector, cruxMap);
    }

    /**
     * Map from Crux to Egeria.
     * @return EntityDetail
     * @see #EntitySummaryMapping(CruxOMRSRepositoryConnector, Map)
     */
    public EntitySummary toEgeria() {
        if (instanceHeader == null && cruxMap != null) {
            instanceHeader = new EntitySummary();
            fromMap();
        }
        if (instanceHeader != null) {
            return (EntitySummary) instanceHeader;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void toMap() {
        super.toMap();
        List<Classification> classifications = ((EntitySummary) instanceHeader).getClassifications();
        ClassificationMapping cm = new ClassificationMapping(cruxConnector, classifications, N_CLASSIFICATIONS);
        Map<Keyword, Object> mappedClassifications = cm.toCrux();
        if (mappedClassifications != null) {
            cruxMap.putAll(mappedClassifications);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void fromMap() {
        super.fromMap();
        ClassificationMapping cm = new ClassificationMapping(cruxConnector, cruxMap, N_CLASSIFICATIONS);
        List<Classification> classifications = cm.toEgeria();
        ((EntitySummary) instanceHeader).setClassifications(classifications);
    }

    /**
     * Retrieve the canonical reference to the entity summary with the specified GUID.
     * @param guid of the entity summary to reference
     * @return Keyword giving the Crux reference to this entity summary document
     */
    public static Keyword getReference(String guid) {
        return getGuid(INSTANCE_REF_PREFIX, guid);
    }

}
