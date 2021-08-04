/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.IPersistentMap;
import clojure.lang.PersistentVector;
import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceAuditHeader;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefLink;

import java.io.Serializable;
import java.util.*;

/**
 * Maps the properties of InstanceAuditHeaders between persistence and objects.
 */
public abstract class InstanceAuditHeaderMapping extends AbstractMapping {

    private static final String INSTANCE_AUDIT_HEADER = "InstanceAuditHeader";

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

    // Note that these have no namespace, and therefore not useful on classifications (only entity, relationship)
    public static final String METADATA_COLLECTION_ID = getKeyword(N_METADATA_COLLECTION_ID);
    public static final String CREATE_TIME = getKeyword(N_CREATE_TIME);
    public static final String UPDATE_TIME = getKeyword(N_UPDATE_TIME);
    public static final String CURRENT_STATUS = getKeyword(N_CURRENT_STATUS);
    public static final String TYPE_DEF_GUIDS = getKeyword(N_TYPE + ".guids");
    public static final String TYPE_DEF_CATEGORY = getKeyword(N_TYPE + ".category");
    public static final String VERSION = getKeyword(N_VERSION);

    private static final Set<String> KNOWN_PROPERTIES = createKnownProperties();
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
     * Check whether the specified property is a known base-level Instance property.
     * @param property to check
     * @return boolean
     */
    public static boolean isKnownBaseProperty(String property) {
        return KNOWN_PROPERTIES.contains(property);
    }

    /**
     * Translate the provided Egeria representation into a Crux document.
     * @param builder for the Crux document
     * @param iah Egeria representation from which to map
     */
    protected void buildDoc(CruxDocument.Builder builder, InstanceAuditHeader iah) {
        buildDoc(builder, iah, null);
    }

    /**
     * Translate the provided Egeria representation into a Crux document.
     * @param builder for the Crux document
     * @param iah Egeria representation from which to map
     * @param namespace by which to qualify the properties
     * @return the latest change date in the header (updateTime or if empty createTime)
     */
    protected Date buildDoc(CruxDocument.Builder builder, InstanceAuditHeader iah, String namespace) {

        Date updateTime = iah.getUpdateTime();
        Date createTime = iah.getCreateTime();

        builder.put(getKeyword(namespace, N_HEADER_VERSION), iah.getHeaderVersion());
        builder.put(getKeyword(namespace, N_METADATA_COLLECTION_ID), iah.getMetadataCollectionId());
        builder.put(getKeyword(namespace, N_METADATA_COLLECTION_NAME), iah.getMetadataCollectionName());
        builder.put(getKeyword(namespace, N_REPLICATED_BY), iah.getReplicatedBy());
        builder.put(getKeyword(namespace, N_INSTANCE_LICENSE), iah.getInstanceLicense());
        builder.put(getKeyword(namespace, N_CREATED_BY), iah.getCreatedBy());
        builder.put(getKeyword(namespace, N_UPDATED_BY), iah.getUpdatedBy());
        builder.put(getKeyword(namespace, N_MAINTAINED_BY), iah.getMaintainedBy());
        builder.put(getKeyword(namespace, N_CREATE_TIME), createTime);
        builder.put(getKeyword(namespace, N_UPDATE_TIME), updateTime);
        builder.put(getKeyword(namespace, N_VERSION), iah.getVersion());

        // Note that for the type, we will break things out a bit to optimise search:
        // - a list of all type GUIDs for this type: its actual type and all of its supertypes (under 'type.guids')
        // Then we'll also serialize the full InstanceType information into the N_TYPE property itself.
        List<String> typeList = new ArrayList<>();
        InstanceType type = iah.getType();
        typeList.add(type.getTypeDefGUID());
        List<TypeDefLink> superTypes = type.getTypeDefSuperTypes();
        if (superTypes != null) {
            for (TypeDefLink superType : superTypes) {
                typeList.add(superType.getGUID());
            }
        }
        builder.put(getKeyword(namespace, N_TYPE + ".guids"), PersistentVector.create(typeList));
        builder.put(getKeyword(namespace, N_TYPE + ".category"), type.getTypeDefCategory().getOrdinal());
        builder.put(getKeyword(namespace, N_TYPE), getEmbeddedSerializedForm(cruxConnector, INSTANCE_AUDIT_HEADER, N_TYPE, type));
        builder.put(getKeyword(namespace, N_INSTANCE_PROVENANCE_TYPE), EnumPropertyValueMapping.getOrdinalForInstanceProvenanceType(iah.getInstanceProvenanceType()));
        builder.put(getKeyword(namespace, N_CURRENT_STATUS), EnumPropertyValueMapping.getOrdinalForInstanceStatus(iah.getStatus()));
        builder.put(getKeyword(namespace, N_STATUS_ON_DELETE), EnumPropertyValueMapping.getOrdinalForInstanceStatus(iah.getStatusOnDelete()));
        builder.put(getKeyword(namespace, N_MAPPING_PROPERTIES), getEmbeddedSerializedForm(cruxConnector, INSTANCE_AUDIT_HEADER, N_MAPPING_PROPERTIES, iah.getMappingProperties()));

        return updateTime == null ? createTime : updateTime;

    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     * @param iah into which to map
     * @param doc from which to map
     */
    protected void fromDoc(InstanceAuditHeader iah, CruxDocument doc) {
        fromDoc(iah, doc, null);
    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     * @param iah into which to map
     * @param doc from which to map
     * @param namespace by which the properties are qualified
     */
    @SuppressWarnings("unchecked")
    protected void fromDoc(InstanceAuditHeader iah, CruxDocument doc, String namespace) {

        final String methodName = "fromDoc";
        for (String propertyName : KNOWN_PROPERTIES) {
            String property = getKeyword(namespace, propertyName);
            Object objValue = doc.get(property);
            String value = objValue == null ? null : objValue.toString();
            switch (propertyName) {
                case N_HEADER_VERSION:
                    iah.setHeaderVersion(objValue == null ? 0 : (Long) objValue);
                    break;
                case N_TYPE:
                    iah.setType(getDeserializedValue(cruxConnector, INSTANCE_AUDIT_HEADER, N_TYPE, (IPersistentMap)objValue, mapper.getTypeFactory().constructType(InstanceType.class)));
                    break;
                case N_INSTANCE_PROVENANCE_TYPE:
                    iah.setInstanceProvenanceType(EnumPropertyValueMapping.getInstanceProvenanceTypeFromOrdinal(cruxConnector, (Integer) objValue));
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
                    iah.setStatus(EnumPropertyValueMapping.getInstanceStatusFromOrdinal(cruxConnector, (Integer) objValue));
                    break;
                case N_STATUS_ON_DELETE:
                    iah.setStatusOnDelete(EnumPropertyValueMapping.getInstanceStatusFromOrdinal(cruxConnector, (Integer) objValue));
                    break;
                case N_MAPPING_PROPERTIES:
                    iah.setMappingProperties(getDeserializedValue(cruxConnector, INSTANCE_AUDIT_HEADER, N_MAPPING_PROPERTIES, (IPersistentMap)objValue, mapper.getTypeFactory().constructMapType(Map.class, String.class, Serializable.class)));
                    break;
                default:
                    cruxConnector.logProblem(this.getClass().getName(),
                            methodName,
                            CruxOMRSAuditCode.UNMAPPED_PROPERTY,
                            null,
                            property,
                            INSTANCE_AUDIT_HEADER);
                    break;
            }

        }

    }

}
