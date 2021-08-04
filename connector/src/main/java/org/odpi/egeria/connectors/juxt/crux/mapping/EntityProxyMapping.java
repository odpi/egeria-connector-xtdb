/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;

/**
 * Maps the properties of EntityProxies between persistence and objects.
 */
public class EntityProxyMapping extends EntitySummaryMapping {

    private static final String ENTITY_PROXY = "EntityProxy";

    private static final String UNIQUE_PROPERTIES_NS = "uniqueProperties";

    public static final String ENTITY_PROXY_ONLY_MARKER = getKeyword(EntitySummaryMapping.INSTANCE_REF_PREFIX, "proxy");

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
     * @param cruxDoc from which to map
     */
    public EntityProxyMapping(CruxOMRSRepositoryConnector cruxConnector,
                              CruxDocument cruxDoc) {
        super(cruxConnector, cruxDoc);
    }

    /**
     * Map from Crux to Egeria.
     * @return EntityProxy
     * @see #EntityProxyMapping(CruxOMRSRepositoryConnector, CruxDocument)
     */
    @Override
    public EntityProxy toEgeria() {
        if (instanceHeader == null && cruxDoc != null) {
            instanceHeader = new EntityProxy();
            fromDoc();
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
    protected CruxDocument.Builder toDoc() {
        CruxDocument.Builder builder = super.toDoc();
        builder.put(ENTITY_PROXY_ONLY_MARKER, true); // set an internal marker that this is only a proxy
        InstancePropertiesMapping.addToDoc(cruxConnector, builder, instanceHeader.getType(), ((EntityProxy) instanceHeader).getUniqueProperties(), UNIQUE_PROPERTIES_NS);
        return builder;
    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     */
    @Override
    protected void fromDoc() {
        super.fromDoc();
        InstanceProperties uniqueProperties = InstancePropertiesMapping.getFromDoc(cruxConnector, instanceHeader.getType(), cruxDoc, UNIQUE_PROPERTIES_NS);
        ((EntityProxy) instanceHeader).setUniqueProperties(uniqueProperties);
    }

    /**
     * Translate the provided Crux representation of an entity into an EntityProxy.
     * @param cruxConnector connectivity to the Crux environment
     * @param doc containing the Crux representation of the entity
     * @return EntityProxy
     */
    public static EntityProxy getFromDoc(CruxOMRSRepositoryConnector cruxConnector,
                                         CruxDocument doc) {
        final String methodName = "getFromDoc";
        if (doc == null) {
            return null;
        } else if (isOnlyAProxy(doc)) {
            // if this is only a proxy, do a direct EntityProxyMapping
            EntityProxyMapping epm = new EntityProxyMapping(cruxConnector, doc);
            return epm.toEgeria();
        } else {
            // otherwise, we'll retrieve a full EntityDetail anyway so use the helper to translate to an EntityProxy
            // (which ensures that we populate uniqueProperties accordingly)
            EntityDetailMapping edm = new EntityDetailMapping(cruxConnector, doc);
            try {
                return cruxConnector.getRepositoryHelper().getNewEntityProxy(cruxConnector.getRepositoryName(), edm.toEgeria());
            } catch (RepositoryErrorException e) {
                cruxConnector.logProblem(EntityProxyMapping.class.getName(),
                        methodName,
                        CruxOMRSAuditCode.FAILED_RETRIEVAL,
                        e,
                        ENTITY_PROXY,
                        doc.getId().toString(),
                        e.getClass().getName());
            }
            return null;
        }
    }

    /**
     * Retrieve the canonical reference to the entity summary with the specified GUID.
     * @param guid of the entity summary to reference
     * @return String giving the Crux reference to this entity summary document
     */
    public static String getReference(String guid) {
        return EntitySummaryMapping.getReference(guid);
    }

    /**
     * Indicates whether the provided map represents only an EntityProxy (true) or a full EntityDetail (false).
     * @param doc containing the Crux representation
     * @return boolean
     */
    public static boolean isOnlyAProxy(CruxDocument doc) {
        Boolean only = (Boolean) doc.get(ENTITY_PROXY_ONLY_MARKER);
        return only != null && only;
    }

}
