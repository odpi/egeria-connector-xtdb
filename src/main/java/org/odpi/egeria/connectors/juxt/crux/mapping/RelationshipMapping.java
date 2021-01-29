/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import crux.api.ICruxDatasource;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Maps the properties of Relationships between persistence and objects.
 */
public class RelationshipMapping extends InstanceHeaderMapping {

    private static final Logger log = LoggerFactory.getLogger(RelationshipMapping.class);

    public static final String INSTANCE_REF_PREFIX = "relationship";

    public static final String RELATIONSHIP_PROPERTIES_NS = "relationshipProperties";
    public static final String N_ENTITY_ONE_PROXY = "entityOneProxy";
    public static final String N_ENTITY_TWO_PROXY = "entityTwoProxy";

    public static final Keyword ENTITY_ONE_PROXY = Keyword.intern(N_ENTITY_ONE_PROXY);
    public static final Keyword ENTITY_TWO_PROXY = Keyword.intern(N_ENTITY_TWO_PROXY);

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
     * @param cruxMap from which to map
     * @param db an open database connection for a point-in-time appropriate to the mapping
     */
    public RelationshipMapping(CruxOMRSRepositoryConnector cruxConnector,
                               Map<Keyword, Object> cruxMap,
                               ICruxDatasource db) {
        super(cruxConnector, cruxMap);
        this.db = db;
    }

    /**
     * Map from Crux to Egeria.
     * @return EntityDetail
     * @see #RelationshipMapping(CruxOMRSRepositoryConnector, Map, ICruxDatasource)
     */
    public Relationship toEgeria() {
        if (instanceHeader == null && cruxMap != null) {
            instanceHeader = new Relationship();
            fromMap();
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
    protected void toMap() {
        super.toMap();
        Relationship relationship = (Relationship) instanceHeader;
        EntityProxy one = relationship.getEntityOneProxy();
        EntityProxy two = relationship.getEntityTwoProxy();
        cruxMap.put(ENTITY_ONE_PROXY, EntityProxyMapping.getReference(one.getGUID()));
        cruxMap.put(ENTITY_TWO_PROXY, EntityProxyMapping.getReference(two.getGUID()));
        InstancePropertiesMapping ipm = new InstancePropertiesMapping(cruxConnector, relationship.getProperties(), RELATIONSHIP_PROPERTIES_NS);
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
        try {
            Object oneRef = cruxMap.getOrDefault(ENTITY_ONE_PROXY, null);
            if (oneRef instanceof Keyword) {
                EntityProxy one = getEntityProxyFromRef((Keyword) oneRef);
                ((Relationship) instanceHeader).setEntityOneProxy(one);
            }
            Object twoRef = cruxMap.getOrDefault(ENTITY_TWO_PROXY, null);
            if (twoRef instanceof Keyword) {
                EntityProxy two = getEntityProxyFromRef((Keyword) twoRef);
                ((Relationship) instanceHeader).setEntityTwoProxy(two);
            }
            InstancePropertiesMapping ipm = new InstancePropertiesMapping(cruxConnector, cruxMap, RELATIONSHIP_PROPERTIES_NS);
            ((Relationship) instanceHeader).setProperties(ipm.toEgeria());
        } catch (RepositoryErrorException e) {
            log.error("Unable to retrieve entity proxy, nullifying the relationship.", e);
            instanceHeader = null;
        }
    }

    /**
     * Retrieve the entity proxy details from the provided reference.
     * @param ref to the entity proxy
     * @return EntityProxy
     * @throws RepositoryErrorException logic error in the repository with corrupted entity proxy
     */
    private EntityProxy getEntityProxyFromRef(Keyword ref) throws RepositoryErrorException {
        Map<Keyword, Object> epMap = cruxConnector.getCruxObjectByReference(db, ref);
        return EntityProxyMapping.getFromMap(cruxConnector, epMap);
    }

    /**
     * Retrieve the canonical reference to the relationship with the specified GUID.
     * @param guid of the relationship to reference
     * @return Keyword giving the Crux reference to this relationship document
     */
    public static Keyword getReference(String guid) {
        return getGuid(INSTANCE_REF_PREFIX, guid);
    }

}
