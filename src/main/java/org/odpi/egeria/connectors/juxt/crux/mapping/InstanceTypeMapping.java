/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.type.TypeReference;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Create a search-able mapping to the type for an instance.
 * (Also avoid storing these duplicative details on every single instance.)
 */
public class InstanceTypeMapping extends AbstractMapping {

    private static final Logger log = LoggerFactory.getLogger(InstanceTypeMapping.class);

    // TODO: would probably make sense to revise this to use the same style mapping we've done for
    //  InstancePropertyValueMapping (ie. embedding the entire serialized form as a single attribute, but having in
    //  addition an attribute that supports direct querying), rather than having a distinct referential set of type
    //  information. By making such a change it means that we keep the type details embedded with the instance itself,
    //  so we should avoid potential problems where versions change at different timelines than we may receive intsances
    //  (ie. from other locations in the cohort). This would also allow us to avoid needing to submit multiple
    //  transactions as part of operations like 'createEntity' in the connector, to create both the types and the
    //  instances as a single transaction with multiple records.
    //  (Think this is now complete: but probably worth checking before we remove this since we're not under version control?)

    // TODO: this class is no longer used (for now)...  If we decide to resurrect it, note that any enums should
    //  be translated into symbolicName serde to support search use cases (see InstanceAuditHeaderMapping for examples)

    private static final String TYPE_NS = "type";
    private static final String N_TYPEDEF_GUID = "typeDefGUID";
    private static final String N_TYPEDEF_VERSION = "typeDefVersion";
    private static final String N_TYPEDEF_CATEGORY = "typeDefCategory";
    private static final String N_TYPEDEF_NAME = "typeDefName";
    private static final String N_TYPEDEF_DESCRIPTION = "typeDefDescription";
    private static final String N_TYPEDEF_DESCRIPTION_GUID = "typeDefDescriptionGUID";
    private static final String N_TYPEDEF_SUPERTYPES = "typeDefSuperTypes";
    private static final String N_VALID_STATUS_LIST = "validStatusList";
    private static final String N_VALID_INSTANCE_PROPERTIES = "validInstanceProperties";

    private static final Keyword TYPEDEF_CATEGORY = Keyword.intern(TYPE_NS, N_TYPEDEF_CATEGORY);
    private static final Keyword TYPEDEF_GUID = Constants.CRUX_PK;
    private static final Keyword R_TYPEDEF_GUID = Keyword.intern(TYPE_NS, N_TYPEDEF_GUID);
    private static final Keyword TYPEDEF_NAME = Keyword.intern(TYPE_NS, N_TYPEDEF_NAME);
    private static final Keyword TYPEDEF_VERSION = Keyword.intern(TYPE_NS, N_TYPEDEF_VERSION);
    private static final Keyword TYPEDEF_DESCRIPTION = Keyword.intern(TYPE_NS, N_TYPEDEF_DESCRIPTION);
    private static final Keyword TYPEDEF_DESCRIPTION_GUID = Keyword.intern(TYPE_NS, N_TYPEDEF_DESCRIPTION_GUID);
    private static final Keyword TYPEDEF_SUPERTYPES = Keyword.intern(TYPE_NS, N_TYPEDEF_SUPERTYPES);
    private static final Keyword VALID_STATUS_LIST = Keyword.intern(TYPE_NS, N_VALID_STATUS_LIST);
    private static final Keyword VALID_INSTANCE_PROPERTIES = Keyword.intern(TYPE_NS, N_VALID_INSTANCE_PROPERTIES);

    private static final Set<Keyword> KNOWN_PROPERTIES = createKnownProperties();
    private static Set<Keyword> createKnownProperties() {
        Set<Keyword> set = new HashSet<>();
        set.add(TYPEDEF_CATEGORY);
        set.add(TYPEDEF_GUID);
        set.add(TYPEDEF_NAME);
        set.add(TYPEDEF_VERSION);
        set.add(TYPEDEF_DESCRIPTION);
        set.add(TYPEDEF_DESCRIPTION_GUID);
        set.add(TYPEDEF_SUPERTYPES);
        set.add(VALID_STATUS_LIST);
        set.add(VALID_INSTANCE_PROPERTIES);
        return set;
    }

    private InstanceType instanceType;
    private Map<Keyword, Object> cruxMap;

    /**
     * Default constructor.
     * @param cruxConnector connectivity to Crux
     */
    protected InstanceTypeMapping(CruxOMRSRepositoryConnector cruxConnector) {
        super(cruxConnector);
    }

    /**
     * Construct a mapping from an EntityDetail (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param instanceType from which to map
     */
    public InstanceTypeMapping(CruxOMRSRepositoryConnector cruxConnector,
                               InstanceType instanceType) {
        this(cruxConnector);
        this.instanceType = instanceType;
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxMap from which to map
     */
    public InstanceTypeMapping(CruxOMRSRepositoryConnector cruxConnector,
                               Map<Keyword, Object> cruxMap) {
        this(cruxConnector);
        this.cruxMap = cruxMap;
    }

    /**
     * Map from Egeria to Crux.
     * @return PersistentVector
     * @see #InstanceTypeMapping(CruxOMRSRepositoryConnector, InstanceType)
     */
    public PersistentVector toCrux() {

        if (cruxMap == null && instanceType != null) {
            cruxMap = toMap(instanceType);
        }
        if (cruxMap != null) {
            return Constants.put(cruxMap);
        } else {
            return null;
        }

    }

    /**
     * Map from Crux to Egeria.
     * @return InstanceType
     * @see #InstanceTypeMapping(CruxOMRSRepositoryConnector, Map)
     */
    public InstanceType toEgeria() {

        if (instanceType != null) {
            return instanceType;
        } else if (cruxMap == null) {
            return null;
        } else {
            instanceType = new InstanceType();
            fromMap(instanceType, cruxMap);
            return instanceType;
        }

    }

    /**
     * Translate the provided Egeria representation into a Crux map.
     * @param it Egeria representation from which to map
     * @return {@code Map<Keyword, Object>} Crux representation
     */
    protected Map<Keyword, Object> toMap(InstanceType it) {

        Map<Keyword, Object> map = new HashMap<>();
        // Use the typedef guid as the primary key
        map.put(Constants.CRUX_PK, getReference(it));

        map.put(TYPEDEF_NAME, it.getTypeDefName());
        map.put(TYPEDEF_VERSION, it.getTypeDefVersion());
        map.put(TYPEDEF_DESCRIPTION, it.getTypeDefDescription());
        map.put(TYPEDEF_DESCRIPTION_GUID, it.getTypeDefDescriptionGUID());
        map.put(VALID_INSTANCE_PROPERTIES, it.getValidInstanceProperties());

        try {
            map.put(TYPEDEF_SUPERTYPES, mapper.writeValueAsString(it.getTypeDefSuperTypes()));
            // TODO: might be useful for supertype-searching to create an array of references to the supertypes?
            /*List<TypeDefLink> parents = it.getTypeDefSuperTypes();
            if (parents != null) {
                map.put("???", getReferencesToInstanceTypes(parents));
            }*/
            map.put(TYPEDEF_CATEGORY, mapper.writeValueAsString(it.getTypeDefCategory()));
            map.put(VALID_STATUS_LIST, mapper.writeValueAsString(it.getValidStatusList()));
        } catch (IOException e) {
            log.error("Unable to translate value to JSON.", e);
        }

        return map;

    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     * @param it into which to map
     * @param map from which to map
     */
    @SuppressWarnings("unchecked")
    protected void fromMap(InstanceType it, Map<Keyword, Object> map) {

        for (Keyword property : KNOWN_PROPERTIES) {
            Object objValue = map.getOrDefault(property, null);
            String value = objValue == null ? null : objValue.toString();
            if (Constants.CRUX_PK.equals(property)) {
                it.setTypeDefGUID(value == null ? null : Constants.trimGuidFromReference(value));
            } else {
                String propertyName = property.getName();
                try {
                    switch (propertyName) {
                        case N_TYPEDEF_VERSION:
                            it.setTypeDefVersion(objValue == null ? 0 : (Long) objValue);
                            break;
                        case N_TYPEDEF_CATEGORY:
                            if (value != null) {
                                it.setTypeDefCategory(mapper.readValue(value, new TypeReference<TypeDefCategory>() {}));
                            }
                            break;
                        case N_TYPEDEF_NAME:
                            it.setTypeDefName(value);
                            break;
                        case N_TYPEDEF_DESCRIPTION:
                            it.setTypeDefDescription(value);
                            break;
                        case N_TYPEDEF_DESCRIPTION_GUID:
                            it.setTypeDefDescriptionGUID(value);
                            break;
                        case N_TYPEDEF_SUPERTYPES:
                            if (value != null) {
                                it.setTypeDefSuperTypes(mapper.readValue(value, new TypeReference<List<TypeDefLink>>() {}));
                            }
                            break;
                        case N_VALID_STATUS_LIST:
                            if (value != null) {
                                it.setValidStatusList(mapper.readValue(value, new TypeReference<List<InstanceStatus>>() {}));
                            }
                            break;
                        case N_VALID_INSTANCE_PROPERTIES:
                            it.setValidInstanceProperties(objValue == null ? null : (List<String>) objValue);
                            break;
                        default:
                            log.warn("Unmapped InstanceAuditHeader property ({}): {}", property, objValue);
                            break;
                    }
                } catch (IOException e) {
                    log.error("Unable to translate value to JSON.", e);
                }
            }

        }

    }

    /**
     * Translate the provided InstanceType information into a Crux reference to the instance type.
     * @param it to translate
     * @return Keyword for the Crux reference
     */
    public static Keyword getReference(InstanceType it) {
        return getReference(it.getTypeDefGUID());
    }

    /**
     * Retrieve an Egeria InstanceType representation from the provided reference.
     * @param cruxConnector connectivity to Crux
     * @param reference to translate into an InstanceType
     * @return InstanceType
     */
    public static InstanceType retrieveFromReference(CruxOMRSRepositoryConnector cruxConnector,
                                                     Keyword reference) {
        Map<Keyword, Object> cruxDoc = cruxConnector.getCruxObjectByReference(reference, null);
        InstanceTypeMapping itm = new InstanceTypeMapping(cruxConnector, cruxDoc);
        return itm.toEgeria();
    }

    /**
     * Retrieve the referential properties needed to point to an instance type definition.
     * @param it the instance type to refer to
     * @return {@code Map<Keyword, Object>}
     */
    public static Map<Keyword, Object> getReferenceToInstanceType(InstanceType it) {
        Map<Keyword, Object> map = new HashMap<>();
        map.put(R_TYPEDEF_GUID, getReference(it));
        map.put(TYPEDEF_VERSION, it.getTypeDefVersion());
        return Collections.unmodifiableMap(map);
    }

    /**
     * Retrieve the referential properties needed to point to a list of instance type definitions (supertypes).
     * @param tdls the list of type definition links to refer to
     * @return {@code Map<Keyword, Object>}
     */
    private static List<Keyword> getReferencesToInstanceTypes(List<TypeDefLink> tdls) {
        List<Keyword> parents = new ArrayList<>();
        for (TypeDefLink tdl : tdls) {
            parents.add(getReference(tdl.getGUID()));
        }
        return parents;
    }

    private static Keyword getReference(String guid) {
        return Keyword.intern(TYPE_NS, guid);
    }

}
