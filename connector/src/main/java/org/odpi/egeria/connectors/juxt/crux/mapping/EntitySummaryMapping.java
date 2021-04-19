/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.Classification;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntitySummary;

import java.util.List;

/**
 * Maps the properties of EntitySummaries between persistence and objects.
 */
public class EntitySummaryMapping extends InstanceHeaderMapping {

    public static final String INSTANCE_REF_PREFIX = "e";
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
     * @param cruxDoc from which to map
     */
    public EntitySummaryMapping(CruxOMRSRepositoryConnector cruxConnector,
                                CruxDocument cruxDoc) {
        super(cruxConnector, cruxDoc);
    }

    /**
     * Map from Crux to Egeria.
     * @return EntityDetail
     * @see #EntitySummaryMapping(CruxOMRSRepositoryConnector, CruxDocument)
     */
    public EntitySummary toEgeria() {
        if (instanceHeader == null && cruxDoc != null) {
            instanceHeader = new EntitySummary();
            fromDoc();
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
    protected CruxDocument.Builder toDoc() {
        CruxDocument.Builder builder = super.toDoc();
        List<Classification> classifications = ((EntitySummary) instanceHeader).getClassifications();
        ClassificationMapping cm = new ClassificationMapping(cruxConnector, classifications, N_CLASSIFICATIONS);
        cm.addToCruxDoc(builder);
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void fromDoc() {
        super.fromDoc();
        ClassificationMapping cm = new ClassificationMapping(cruxConnector, cruxDoc, N_CLASSIFICATIONS);
        List<Classification> classifications = cm.toEgeria();
        ((EntitySummary) instanceHeader).setClassifications(classifications);
    }

    /**
     * Retrieve the canonical reference to the entity summary with the specified GUID.
     * @param guid of the entity summary to reference
     * @return String giving the Crux reference to this entity summary document
     */
    public static String getReference(String guid) {
        return getGuid(INSTANCE_REF_PREFIX, guid);
    }

}
