/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceAuditHeader;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Maps the properties of InstanceAuditHeaders between persistence and objects.
 */
public abstract class InstanceAuditHeaderMapping extends AbstractMapping {

    private static final Logger log = LoggerFactory.getLogger(InstanceAuditHeaderMapping.class);

    private static final String N_HEADER_VERSION = "headerVersion";
    private static final String N_TYPE = "type";
    private static final String N_INSTANCE_PROVENANCE_TYPE = "instanceProvenanceType";
    private static final String N_METADATA_COLLECTION_ID = "metadataCollectionId";
    private static final String N_METADATA_COLLECTION_NAME = "metadataCollectionName";
    private static final String N_REPLICATED_BY = "replicatedBy";
    private static final String N_INSTANCE_LICENSE = "instanceLicense";
    private static final String N_CREATED_BY = "createdBy";
    private static final String N_UPDATED_BY = "updatedBy";
    private static final String N_MAINTAINED_BY = "maintainedBy";
    private static final String N_CREATE_TIME = "createTime";
    private static final String N_UPDATE_TIME = "updateTime";
    private static final String N_VERSION = "version";
    private static final String N_CURRENT_STATUS = "currentStatus";
    private static final String N_STATUS_ON_DELETE = "statusOnDelete";
    private static final String N_MAPPING_PROPERTIES = "mappingProperties";

    // Note that these are non-namespaced, and therefore not useful on classifications (only entity, relationship)
    public static final Keyword CREATE_TIME = Keyword.intern(N_CREATE_TIME);
    public static final Keyword UPDATE_TIME = Keyword.intern(N_UPDATE_TIME);
    public static final Keyword CURRENT_STATUS = Keyword.intern(N_CURRENT_STATUS);
    public static final Keyword TYPE_DEF_GUID = Keyword.intern(N_TYPE + ".guid");
    public static final Keyword SUPERTYPE_DEF_GUIDS = Keyword.intern(N_TYPE + ".supers");

    public static final Set<String> KNOWN_PROPERTIES = createKnownProperties();
    private static Set<String> createKnownProperties() {
        Set<String> set = new HashSet<>();
        set.add(N_HEADER_VERSION);
        set.add(N_TYPE);
        set.add(N_INSTANCE_PROVENANCE_TYPE);
        set.add(N_METADATA_COLLECTION_ID);
        set.add(N_METADATA_COLLECTION_NAME);
        set.add(N_REPLICATED_BY);
        set.add(N_INSTANCE_LICENSE);
        set.add(N_CREATED_BY);
        set.add(N_UPDATED_BY);
        set.add(N_MAINTAINED_BY);
        set.add(N_CREATE_TIME);
        set.add(N_UPDATE_TIME);
        set.add(N_VERSION);
        set.add(N_CURRENT_STATUS);
        set.add(N_STATUS_ON_DELETE);
        set.add(N_MAPPING_PROPERTIES);
        return set;
    }

    /**
     * Default constructor.
     * @param cruxConnector connectivity to Crux
     */
    protected InstanceAuditHeaderMapping(CruxOMRSRepositoryConnector cruxConnector) {
        super(cruxConnector);
    }

    /**
     * Translate the provided Egeria representation into a Crux map.
     * @param iah Egeria representation from which to map
     * @return {@code Map<Keyword, Object>} Crux representation
     */
    protected Map<Keyword, Object> toMap(InstanceAuditHeader iah) {
        return toMap(iah, null);
    }

    /**
     * Translate the provided Egeria representation into a Crux map.
     * @param iah Egeria representation from which to map
     * @param namespace by which to qualify the properties
     * @return {@code Map<Keyword, Object>} Crux representation
     */
    protected Map<Keyword, Object> toMap(InstanceAuditHeader iah, String namespace) {

        Map<Keyword, Object> map = new HashMap<>();
        map.put(Keyword.intern(namespace, N_HEADER_VERSION), iah.getHeaderVersion());
        map.put(Keyword.intern(namespace, N_METADATA_COLLECTION_ID), iah.getMetadataCollectionId());
        map.put(Keyword.intern(namespace, N_METADATA_COLLECTION_NAME), iah.getMetadataCollectionName());
        map.put(Keyword.intern(namespace, N_REPLICATED_BY), iah.getReplicatedBy());
        map.put(Keyword.intern(namespace, N_INSTANCE_LICENSE), iah.getInstanceLicense());
        map.put(Keyword.intern(namespace, N_CREATED_BY), iah.getCreatedBy());
        map.put(Keyword.intern(namespace, N_UPDATED_BY), iah.getUpdatedBy());
        map.put(Keyword.intern(namespace, N_MAINTAINED_BY), iah.getMaintainedBy());
        map.put(Keyword.intern(namespace, N_CREATE_TIME), iah.getCreateTime());
        map.put(Keyword.intern(namespace, N_UPDATE_TIME), iah.getUpdateTime());
        map.put(Keyword.intern(namespace, N_VERSION), iah.getVersion());

        // Note that for the type, we will break things out a bit to optimise search:
        // - the GUID on its own (under 'type.guid')
        // - the list of supertypes of the instances TypeDef (under 'type.supers')
        // Then we'll also serialize the full InstanceType information into the N_TYPE property itself.
        InstanceType type = iah.getType();
        map.put(Keyword.intern(namespace, N_TYPE + ".guid"), type.getTypeDefGUID());
        List<TypeDefLink> superTypes = type.getTypeDefSuperTypes();
        if (superTypes != null) {
            List<String> stList = new ArrayList<>();
            for (TypeDefLink superType : superTypes) {
                stList.add(superType.getGUID());
            }
            map.put(Keyword.intern(namespace, N_TYPE + ".supers"), PersistentVector.create(stList));
        }
        map.put(Keyword.intern(namespace, N_TYPE), getEmbeddedSerializedForm(type));
        map.put(Keyword.intern(namespace, N_INSTANCE_PROVENANCE_TYPE), EnumPropertyValueMapping.getOrdinalForInstanceProvenanceType(iah.getInstanceProvenanceType()));
        map.put(Keyword.intern(namespace, N_CURRENT_STATUS), EnumPropertyValueMapping.getOrdinalForInstanceStatus(iah.getStatus()));
        map.put(Keyword.intern(namespace, N_STATUS_ON_DELETE), EnumPropertyValueMapping.getOrdinalForInstanceStatus(iah.getStatusOnDelete()));
        map.put(Keyword.intern(namespace, N_MAPPING_PROPERTIES), getEmbeddedSerializedForm(iah.getMappingProperties()));

        return map;

    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     * @param iah into which to map
     * @param map from which to map
     */
    protected void fromMap(InstanceAuditHeader iah, Map<Keyword, Object> map) {
        fromMap(iah, map, null);
    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     * @param iah into which to map
     * @param map from which to map
     * @param namespace by which the properties are qualified
     */
    @SuppressWarnings("unchecked")
    protected void fromMap(InstanceAuditHeader iah, Map<Keyword, Object> map, String namespace) {

        for (String propertyName : KNOWN_PROPERTIES) {
            Keyword property = Keyword.intern(namespace, propertyName);
            Object objValue = map.getOrDefault(property, null);
            String value = objValue == null ? null : objValue.toString();
            switch (propertyName) {
                case N_HEADER_VERSION:
                    iah.setHeaderVersion(objValue == null ? 0 : (Long) objValue);
                    break;
                case N_TYPE:
                    iah.setType(getDeserializedValue((IPersistentMap)objValue, mapper.getTypeFactory().constructType(InstanceType.class)));
                    break;
                case N_INSTANCE_PROVENANCE_TYPE:
                    iah.setInstanceProvenanceType(EnumPropertyValueMapping.getInstanceProvenanceTypeFromOrdinal((Integer) objValue));
                    break;
                case N_METADATA_COLLECTION_ID:
                    iah.setMetadataCollectionId(value);
                    break;
                case N_METADATA_COLLECTION_NAME:
                    iah.setMetadataCollectionName(value);
                    break;
                case N_REPLICATED_BY:
                    iah.setReplicatedBy(value);
                    break;
                case N_INSTANCE_LICENSE:
                    iah.setInstanceLicense(value);
                    break;
                case N_CREATED_BY:
                    iah.setCreatedBy(value);
                    break;
                case N_UPDATED_BY:
                    iah.setUpdatedBy(value);
                    break;
                case N_MAINTAINED_BY:
                    iah.setMaintainedBy(objValue == null ? null : (List<String>) objValue);
                    break;
                case N_CREATE_TIME:
                    iah.setCreateTime(objValue == null ? null : (Date) objValue);
                    break;
                case N_UPDATE_TIME:
                    iah.setUpdateTime(objValue == null ? null : (Date) objValue);
                    break;
                case N_VERSION:
                    iah.setVersion(objValue == null ? -1 : (Long) objValue);
                    break;
                case N_CURRENT_STATUS:
                    iah.setStatus(EnumPropertyValueMapping.getInstanceStatusFromOrdinal((Integer) objValue));
                    break;
                case N_STATUS_ON_DELETE:
                    iah.setStatusOnDelete(EnumPropertyValueMapping.getInstanceStatusFromOrdinal((Integer) objValue));
                    break;
                case N_MAPPING_PROPERTIES:
                    iah.setMappingProperties(getDeserializedValue((IPersistentMap)objValue, mapper.getTypeFactory().constructMapType(Map.class, String.class, Serializable.class)));
                    break;
                default:
                    log.warn("Unmapped InstanceAuditHeader property ({}): {}", property, objValue);
                    break;
            }

        }

    }

}
