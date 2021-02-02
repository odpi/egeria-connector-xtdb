/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityProxy;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Maps the properties of EntityProxies between persistence and objects.
 */
public class EntityProxyMapping extends EntitySummaryMapping {

    private static final Logger log = LoggerFactory.getLogger(EntityProxyMapping.class);

    private static final String UNIQUE_PROPERTIES_NS = "uniqueProperties";

    public static final Keyword ENTITY_PROXY_ONLY_MARKER = Keyword.intern(EntitySummaryMapping.INSTANCE_REF_PREFIX, "proxy");

    /**
     * Construct a mapping from an EntityDetail (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param entityProxy from which to map
     */
    public EntityProxyMapping(CruxOMRSRepositoryConnector cruxConnector,
                              EntityProxy entityProxy) {
        super(cruxConnector, entityProxy);
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxMap from which to map
     */
    public EntityProxyMapping(CruxOMRSRepositoryConnector cruxConnector,
                              Map<Keyword, Object> cruxMap) {
        super(cruxConnector, cruxMap);
    }

    /**
     * Map from Crux to Egeria.
     * @return EntityProxy
     * @see #EntityProxyMapping(CruxOMRSRepositoryConnector, Map)
     */
    @Override
    public EntityProxy toEgeria() {
        if (instanceHeader == null && cruxMap != null) {
            instanceHeader = new EntityProxy();
            fromMap();
        }
        if (instanceHeader != null) {
            return (EntityProxy) instanceHeader;
        } else {
            return null;
        }
    }

    /**
     * Translate the provided Egeria representation into a Crux map.
     */
    @Override
    protected void toMap() {
        super.toMap();
        // set an internal marker that this is only a proxy
        cruxMap.put(ENTITY_PROXY_ONLY_MARKER, true);
        InstancePropertiesMapping ipm = new InstancePropertiesMapping(cruxConnector, instanceHeader.getType(), ((EntityProxy) instanceHeader).getUniqueProperties(), UNIQUE_PROPERTIES_NS);
        Map<Keyword, Object> propertyMap = ipm.toCrux();
        if (propertyMap != null) {
            cruxMap.putAll(propertyMap);
        }
    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     */
    protected void fromMap() {
        super.fromMap();
        InstancePropertiesMapping ipm = new InstancePropertiesMapping(cruxConnector, cruxMap, UNIQUE_PROPERTIES_NS);
        ((EntityProxy) instanceHeader).setUniqueProperties(ipm.toEgeria());
    }

    /**
     * Translate the provided Crux representation of an entity into an EntityProxy.
     * @param cruxConnector connectivity to the Crux environment
     * @param map containing the Crux representation of the entity
     * @return EntityProxy
     */
    public static EntityProxy getFromMap(CruxOMRSRepositoryConnector cruxConnector,
                                         Map<Keyword, Object> map) {
        if (map == null) {
            return null;
        } else if (isOnlyAProxy(map)) {
            // if this is only a proxy, do a direct EntityProxyMapping
            EntityProxyMapping epm = new EntityProxyMapping(cruxConnector, map);
            return epm.toEgeria();
        } else {
            // otherwise, we'll retrieve a full EntityDetail anyway so use the helper to translate to an EntityProxy
            // (which ensures that we populate uniqueProperties accordingly)
            EntityDetailMapping edm = new EntityDetailMapping(cruxConnector, map);
            try {
                return cruxConnector.getRepositoryHelper().getNewEntityProxy(cruxConnector.getRepositoryName(), edm.toEgeria());
            } catch (RepositoryErrorException e) {
                log.error("Unable to retrieve proxy from EntityDetail.", e);
            }
            return null;
        }
    }

    /**
     * Retrieve the canonical reference to the entity summary with the specified GUID.
     * @param guid of the entity summary to reference
     * @return Keyword giving the Crux reference to this entity summary document
     */
    public static Keyword getReference(String guid) {
        return EntitySummaryMapping.getReference(guid);
    }

    /**
     * Indicates whether the provided map represents only an EntityProxy (true) or a full EntityDetail (false).
     * @param map containing the Crux representation
     * @return boolean
     */
    public static boolean isOnlyAProxy(Map<Keyword, Object> map) {
        return (Boolean) map.getOrDefault(ENTITY_PROXY_ONLY_MARKER, false);
    }

}
