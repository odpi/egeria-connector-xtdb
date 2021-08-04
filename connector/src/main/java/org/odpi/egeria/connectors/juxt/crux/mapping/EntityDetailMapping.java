/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

/**
 * Maps the properties of EntityDetails between persistence and objects.
 */
public class EntityDetailMapping extends EntitySummaryMapping {

    public static final String ENTITY_PROPERTIES_NS = "entityProperties";

    /**
     * Construct a mapping from an EntityDetail (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param entityDetail from which to map
     */
    public EntityDetailMapping(CruxOMRSRepositoryConnector cruxConnector,
                               EntityDetail entityDetail) {
        super(cruxConnector, entityDetail);
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxDoc from which to map
     */
    public EntityDetailMapping(CruxOMRSRepositoryConnector cruxConnector,
                               CruxDocument cruxDoc) {
        super(cruxConnector, cruxDoc);
    }

    /**
     * Map from Crux to Egeria.
     * @return EntityDetail
     * @see #EntityDetailMapping(CruxOMRSRepositoryConnector, CruxDocument)
     */
    @Override
    public EntityDetail toEgeria() {
        if (instanceHeader == null && cruxDoc != null) {
            instanceHeader = new EntityDetail();
            fromDoc();
        }
        if (instanceHeader != null) {
            return (EntityDetail) instanceHeader;
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
        // overwrite any internal marker that this is only a proxy
        builder.put(EntityProxyMapping.ENTITY_PROXY_ONLY_MARKER, false);
        InstancePropertiesMapping.addToDoc(cruxConnector, builder, instanceHeader.getType(), ((EntityDetail) instanceHeader).getProperties(), ENTITY_PROPERTIES_NS);
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void fromDoc() {
        super.fromDoc();
        InstanceProperties ip = InstancePropertiesMapping.getFromDoc(cruxConnector, instanceHeader.getType(), cruxDoc, ENTITY_PROPERTIES_NS);
        ((EntityDetail) instanceHeader).setProperties(ip);
    }

    /**
     * Translate the provided GUID into a Crux reference.
     * @param guid to translate
     * @return String reference for Crux
     */
    public static String getReference(String guid) {
        return EntitySummaryMapping.getReference(guid);
    }

}
