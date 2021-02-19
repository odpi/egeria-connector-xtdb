/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

import java.util.Map;

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
     * @param cruxMap from which to map
     */
    public EntityDetailMapping(CruxOMRSRepositoryConnector cruxConnector,
                               Map<Keyword, Object> cruxMap) {
        super(cruxConnector, cruxMap);
    }

    /**
     * Map from Crux to Egeria.
     * @return EntityDetail
     * @see #EntityDetailMapping(CruxOMRSRepositoryConnector, Map)
     */
    @Override
    public EntityDetail toEgeria() {
        if (instanceHeader == null && cruxMap != null) {
            instanceHeader = new EntityDetail();
            fromMap();
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
    protected void toMap() {
        super.toMap();
        // overwrite any internal marker that this is only a proxy
        cruxMap.put(EntityProxyMapping.ENTITY_PROXY_ONLY_MARKER, false);
        InstancePropertiesMapping ipm = new InstancePropertiesMapping(cruxConnector, instanceHeader.getType(), ((EntityDetail) instanceHeader).getProperties(), ENTITY_PROPERTIES_NS);
        Map<Keyword, Object> propertyMap = ipm.toCrux();
        if (propertyMap != null) {
            cruxMap.putAll(propertyMap);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void fromMap() {
        super.fromMap();
        InstancePropertiesMapping ipm = new InstancePropertiesMapping(cruxConnector, cruxMap, ENTITY_PROPERTIES_NS);
        ((EntityDetail) instanceHeader).setProperties(ipm.toEgeria());
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
