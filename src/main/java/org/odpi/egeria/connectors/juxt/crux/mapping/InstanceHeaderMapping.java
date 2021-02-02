/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceHeader;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps the properties of InstanceHeaders between persistence and objects.
 * (Note that this is the first level of mapping that can be instantiated, as it is the first level
 * in which a Crux ID (GUID) is mapped.)
 */
public class InstanceHeaderMapping extends InstanceAuditHeaderMapping {

    private static final Logger log = LoggerFactory.getLogger(InstanceHeaderMapping.class);

    private static final Keyword GUID = Constants.CRUX_PK;
    private static final Keyword INSTANCE_URL = Keyword.intern("instanceURL");

    private static final Set<Keyword> KNOWN_PROPERTIES = createKnownProperties();
    private static Set<Keyword> createKnownProperties() {
        Set<Keyword> set = new HashSet<>();
        set.add(GUID);
        set.add(INSTANCE_URL);
        return set;
    }

    protected InstanceHeader instanceHeader;
    protected Map<Keyword, Object> cruxMap;

    /**
     * Construct a mapping from an InstanceAuditHeader (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param instanceHeader from which to map
     */
    public InstanceHeaderMapping(CruxOMRSRepositoryConnector cruxConnector,
                                 InstanceHeader instanceHeader) {
        super(cruxConnector);
        this.instanceHeader = instanceHeader;
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxMap from which to map
     */
    public InstanceHeaderMapping(CruxOMRSRepositoryConnector cruxConnector,
                                 Map<Keyword, Object> cruxMap) {
        super(cruxConnector);
        this.cruxMap = cruxMap;
    }

    /**
     * Map from Egeria to Crux.
     * @return PersistentVector
     * @see #InstanceHeaderMapping(CruxOMRSRepositoryConnector, InstanceHeader)
     */
    public PersistentVector toCrux() {
        if (cruxMap == null && instanceHeader != null) {
            toMap();
        }
        if (cruxMap != null) {
            return Constants.put(cruxMap);
        } else {
            return null;
        }
    }

    /**
     * Translate the provided Egeria representation into a Crux map.
     */
    protected void toMap() {
        cruxMap = super.toMap(instanceHeader);
        cruxMap.put(GUID, getGuidReference(instanceHeader));
        cruxMap.put(INSTANCE_URL, instanceHeader.getInstanceURL());
    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     */
    protected void fromMap() {
        super.fromMap(instanceHeader, cruxMap);
        for (Keyword property : KNOWN_PROPERTIES) {
            Object objValue = cruxMap.getOrDefault(property, null);
            String value = objValue == null ? null : objValue.toString();
            if (GUID.equals(property)) {
                instanceHeader.setGUID(value == null ? null : Constants.trimGuidFromReference(value));
            } else if (INSTANCE_URL.equals(property)) {
                instanceHeader.setInstanceURL(value);
            } else {
                log.warn("Unmapped InstanceHeader property ({}): {}", property, objValue);
            }
        }
    }

    /**
     * Translate the provided InstanceHeader information into a Crux reference to the GUID of the instance.
     * @param ih to translate
     * @return Keyword for the Crux reference
     */
    public static Keyword getGuidReference(InstanceHeader ih) {
        TypeDefCategory type = ih.getType().getTypeDefCategory();
        if (type.equals(TypeDefCategory.ENTITY_DEF)) {
            return getGuid(EntitySummaryMapping.INSTANCE_REF_PREFIX, ih.getGUID());
        } else if (type.equals(TypeDefCategory.RELATIONSHIP_DEF)) {
            return getGuid(RelationshipMapping.INSTANCE_REF_PREFIX, ih.getGUID());
        } else {
            log.warn("Attempted to retrieve a GUID reference to a non-referenceable type -- returning null: {}", ih);
            return null;
        }
    }

    /**
     * Translate the provided details into a Crux reference.
     * @param instanceType of the instance (from TypeDefCategory name)
     * @param guid of the instance
     * @return Keyword for the Crux reference
     */
    protected static Keyword getGuid(String instanceType, String guid) {
        return Keyword.intern(instanceType, guid);
    }

}
