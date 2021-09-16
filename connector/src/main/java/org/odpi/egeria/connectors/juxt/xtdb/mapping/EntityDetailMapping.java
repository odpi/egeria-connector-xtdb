/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.mapping;

import xtdb.api.XtdbDocument;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

/**
 * Maps the properties of EntityDetails between persistence and objects.
 */
public class EntityDetailMapping extends EntitySummaryMapping {

    public static final String ENTITY_PROPERTIES_NS = "entityProperties";

    /**
     * Construct a mapping from an EntityDetail (to map to a XTDB representation).
     * @param xtdbConnector connectivity to XTDB
     * @param entityDetail from which to map
     */
    public EntityDetailMapping(XtdbOMRSRepositoryConnector xtdbConnector,
                               EntityDetail entityDetail) {
        super(xtdbConnector, entityDetail);
    }

    /**
     * Construct a mapping from a XTDB map (to map to an Egeria representation).
     * @param xtdbConnector connectivity to XTDB
     * @param xtdbDoc from which to map
     */
    public EntityDetailMapping(XtdbOMRSRepositoryConnector xtdbConnector,
                               XtdbDocument xtdbDoc) {
        super(xtdbConnector, xtdbDoc);
    }

    /**
     * Map from XTDB to Egeria.
     * @return EntityDetail
     * @see #EntityDetailMapping(XtdbOMRSRepositoryConnector, XtdbDocument)
     */
    @Override
    public EntityDetail toEgeria() {
        if (instanceHeader == null && xtdbDoc != null) {
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
    protected XtdbDocument.Builder toDoc() {
        XtdbDocument.Builder builder = super.toDoc();
        // overwrite any internal marker that this is only a proxy
        builder.put(EntityProxyMapping.ENTITY_PROXY_ONLY_MARKER, false);
        InstancePropertiesMapping.addToDoc(xtdbConnector, builder, instanceHeader.getType(), ((EntityDetail) instanceHeader).getProperties(), ENTITY_PROPERTIES_NS);
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void fromDoc() {
        super.fromDoc();
        InstanceProperties ip = InstancePropertiesMapping.getFromDoc(xtdbConnector, instanceHeader.getType(), xtdbDoc, ENTITY_PROPERTIES_NS);
        ((EntityDetail) instanceHeader).setProperties(ip);
    }

    /**
     * Translate the provided GUID into a XTDB reference.
     * @param guid to translate
     * @return String reference for XTDB
     */
    public static String getReference(String guid) {
        return EntitySummaryMapping.getReference(guid);
    }

}
