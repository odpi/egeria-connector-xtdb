/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.mapping;

import xtdb.api.XtdbDocument;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
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
     * Construct a mapping from an EntityDetail (to map to a XTDB representation).
     * @param xtdbConnector connectivity to XTDB
     * @param entityProxy from which to map
     */
    public EntityProxyMapping(XtdbOMRSRepositoryConnector xtdbConnector,
                              EntityProxy entityProxy) {
        super(xtdbConnector, entityProxy);
    }

    /**
     * Construct a mapping from a XTDB map (to map to an Egeria representation).
     * @param xtdbConnector connectivity to XTDB
     * @param xtdbDoc from which to map
     */
    public EntityProxyMapping(XtdbOMRSRepositoryConnector xtdbConnector,
                              XtdbDocument xtdbDoc) {
        super(xtdbConnector, xtdbDoc);
    }

    /**
     * Map from XTDB to Egeria.
     * @return EntityProxy
     * @see #EntityProxyMapping(XtdbOMRSRepositoryConnector, XtdbDocument)
     */
    @Override
    public EntityProxy toEgeria() {
        if (instanceHeader == null && xtdbDoc != null) {
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
     * Translate the provided Egeria representation into a XTDB map.
     */
    @Override
    protected XtdbDocument.Builder toDoc() {
        XtdbDocument.Builder builder = super.toDoc();
        builder.put(ENTITY_PROXY_ONLY_MARKER, true); // set an internal marker that this is only a proxy
        InstancePropertiesMapping.addToDoc(xtdbConnector, builder, instanceHeader.getType(), ((EntityProxy) instanceHeader).getUniqueProperties(), UNIQUE_PROPERTIES_NS);
        return builder;
    }

    /**
     * Translate the provided XTDB representation into an Egeria representation.
     */
    @Override
    protected void fromDoc() {
        super.fromDoc();
        InstanceProperties uniqueProperties = InstancePropertiesMapping.getFromDoc(xtdbConnector, instanceHeader.getType(), xtdbDoc, UNIQUE_PROPERTIES_NS);
        ((EntityProxy) instanceHeader).setUniqueProperties(uniqueProperties);
    }

    /**
     * Translate the provided XTDB representation of an entity into an EntityProxy.
     * @param xtdbConnector connectivity to the XTDB environment
     * @param doc containing the XTDB representation of the entity
     * @return EntityProxy
     */
    public static EntityProxy getFromDoc(XtdbOMRSRepositoryConnector xtdbConnector,
                                         XtdbDocument doc) {
        final String methodName = "getFromDoc";
        if (doc == null) {
            return null;
        } else if (isOnlyAProxy(doc)) {
            // if this is only a proxy, do a direct EntityProxyMapping
            EntityProxyMapping epm = new EntityProxyMapping(xtdbConnector, doc);
            return epm.toEgeria();
        } else {
            // otherwise, we'll retrieve a full EntityDetail anyway so use the helper to translate to an EntityProxy
            // (which ensures that we populate uniqueProperties accordingly)
            EntityDetailMapping edm = new EntityDetailMapping(xtdbConnector, doc);
            try {
                return xtdbConnector.getRepositoryHelper().getNewEntityProxy(xtdbConnector.getRepositoryName(), edm.toEgeria());
            } catch (RepositoryErrorException e) {
                xtdbConnector.logProblem(EntityProxyMapping.class.getName(),
                        methodName,
                        XtdbOMRSAuditCode.FAILED_RETRIEVAL,
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
     * @return String giving the XTDB reference to this entity summary document
     */
    public static String getReference(String guid) {
        return EntitySummaryMapping.getReference(guid);
    }

    /**
     * Indicates whether the provided map represents only an EntityProxy (true) or a full EntityDetail (false).
     * @param doc containing the XTDB representation
     * @return boolean
     */
    public static boolean isOnlyAProxy(XtdbDocument doc) {
        Boolean only = (Boolean) doc.get(ENTITY_PROXY_ONLY_MARKER);
        return only != null && only;
    }

}
