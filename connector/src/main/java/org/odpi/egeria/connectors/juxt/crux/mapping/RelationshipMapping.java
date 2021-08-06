/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.IPersistentVector;
import clojure.lang.PersistentVector;
import crux.api.CruxDocument;
import crux.api.ICruxDatasource;
import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

/**
 * Maps the properties of Relationships between persistence and objects.
 */
public class RelationshipMapping extends InstanceHeaderMapping {

    public static final String INSTANCE_REF_PREFIX = "r";

    public static final String RELATIONSHIP_PROPERTIES_NS = "relationshipProperties";
    private static final String N_ENTITY_PROXIES = "entityProxies";

    public static final String ENTITY_PROXIES = getKeyword(N_ENTITY_PROXIES);

    private ICruxDatasource db;

    /**
     * Construct a mapping from a Relationship (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param relationship from which to map
     */
    public RelationshipMapping(CruxOMRSRepositoryConnector cruxConnector,
                               Relationship relationship) {
        super(cruxConnector, relationship);
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxDoc from which to map
     * @param db an open database connection for a point-in-time appropriate to the mapping
     */
    public RelationshipMapping(CruxOMRSRepositoryConnector cruxConnector,
                               CruxDocument cruxDoc,
                               ICruxDatasource db) {
        super(cruxConnector, cruxDoc);
        this.db = db;
    }

    /**
     * Map from Crux to Egeria.
     * @return EntityDetail
     * @see #RelationshipMapping(CruxOMRSRepositoryConnector, CruxDocument, ICruxDatasource)
     */
    public Relationship toEgeria() {
        if (instanceHeader == null && cruxDoc != null) {
            instanceHeader = new Relationship();
            fromDoc();
        }
        if (instanceHeader != null) {
            return (Relationship) instanceHeader;
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
        Relationship relationship = (Relationship) instanceHeader;
        EntityProxy one = relationship.getEntityOneProxy();
        EntityProxy two = relationship.getEntityTwoProxy();
        builder.put(ENTITY_PROXIES, PersistentVector.create(EntityProxyMapping.getReference(one.getGUID()), EntityProxyMapping.getReference(two.getGUID())));
        InstancePropertiesMapping.addToDoc(cruxConnector, builder, relationship.getType(), relationship.getProperties(), RELATIONSHIP_PROPERTIES_NS);
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void fromDoc() {
        super.fromDoc();
        Object proxies = cruxDoc.get(ENTITY_PROXIES);
        if (proxies instanceof IPersistentVector) {
            IPersistentVector v = (IPersistentVector) proxies;
            if (v.length() == 2) {
                String oneRef = (String) v.nth(0);
                String twoRef = (String) v.nth(1);
                EntityProxy one = getEntityProxyFromRef(oneRef);
                EntityProxy two = getEntityProxyFromRef(twoRef);
                if (one != null && two != null) {
                    ((Relationship) instanceHeader).setEntityOneProxy(one);
                    ((Relationship) instanceHeader).setEntityTwoProxy(two);
                } else {
                    cruxConnector.logProblem(this.getClass().getName(),
                            "fromDoc",
                            CruxOMRSAuditCode.FAILED_RETRIEVAL,
                            null,
                            "relationship",
                            instanceHeader.getGUID(),
                            "one or both of the entity proxies were not found -- 1:" + oneRef + ", 2:" + twoRef);
                    instanceHeader = null;
                    return;
                }
            }
        }
        InstanceProperties ip = InstancePropertiesMapping.getFromDoc(cruxConnector, instanceHeader.getType(), cruxDoc, RELATIONSHIP_PROPERTIES_NS);
        ((Relationship) instanceHeader).setProperties(ip);
    }

    /**
     * Retrieve the entity proxy details from the provided reference.
     * @param ref to the entity proxy
     * @return EntityProxy
     */
    private EntityProxy getEntityProxyFromRef(String ref) {
        CruxDocument epDoc = cruxConnector.getCruxObjectByReference(db, ref);
        return EntityProxyMapping.getFromDoc(cruxConnector, epDoc);
    }

    /**
     * Retrieve the canonical reference to the relationship with the specified GUID.
     * @param guid of the relationship to reference
     * @return String giving the Crux reference to this relationship document
     */
    public static String getReference(String guid) {
        return getReference(INSTANCE_REF_PREFIX, guid);
    }

}
