/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntityProxyMapping;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EnumPropertyValueMapping;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.InstanceAuditHeaderMapping;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProvenanceType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.ffdc.OMRSErrorCode;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityNotKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.InvalidParameterException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Common utilities to use within transaction functions.
 */
public class TxnUtils {

    public static final Keyword VERSION = Keyword.intern(InstanceAuditHeaderMapping.VERSION);
    public static final Keyword MAINTAINED_BY = Keyword.intern(InstanceAuditHeaderMapping.MAINTAINED_BY);
    public static final Keyword UPDATED_BY = Keyword.intern(InstanceAuditHeaderMapping.UPDATED_BY);
    public static final Keyword UPDATE_TIME = Keyword.intern(InstanceAuditHeaderMapping.UPDATE_TIME);
    public static final Keyword CURRENT_STATUS = Keyword.intern(InstanceAuditHeaderMapping.CURRENT_STATUS);
    public static final Keyword STATUS_ON_DELETE = Keyword.intern(InstanceAuditHeaderMapping.STATUS_ON_DELETE);
    public static final Keyword INSTANCE_PROVENANCE_TYPE = Keyword.intern(InstanceAuditHeaderMapping.INSTANCE_PROVENANCE_TYPE);
    public static final Keyword METADATA_COLLECTION_ID = Keyword.intern(InstanceAuditHeaderMapping.METADATA_COLLECTION_ID);
    public static final Keyword REPLICATED_BY = Keyword.intern(InstanceAuditHeaderMapping.REPLICATED_BY);

    public static final Keyword ENTITY_PROXY_ONLY_MARKER = Keyword.intern(EntityProxyMapping.ENTITY_PROXY_ONLY_MARKER);

    /**
     * Changes the control information to reflect an update in an instance.
     *
     * @param userId   user making the change
     * @param instance instance to update
     * @return IPersistentMap with the updates applied
     */
    @SuppressWarnings("unchecked")
    public static IPersistentMap incrementVersion(String userId,
                                                  IPersistentMap instance) {

        Long currentVersion = (Long) instance.valAt(VERSION);
        List<String> maintainers = (List<String>) instance.valAt(MAINTAINED_BY);

        instance = instance
                .assoc(UPDATED_BY, userId)
                .assoc(UPDATE_TIME, new Date())
                .assoc(VERSION, currentVersion + 1);

        if (maintainers == null) {
            maintainers = new ArrayList<>();
        }
        if (!maintainers.contains(userId)) {
            maintainers.add(userId);
            instance = instance.assoc(MAINTAINED_BY, PersistentVector.create(maintainers));
        }

        return instance;

    }

    /**
     * Validates that the provided metadata instance is not in a deleted state.
     *
     * @param existing metadata instance
     * @param instanceGUID of the instance
     * @param className doing the validation
     * @param methodName doing the validation
     * @throws InvalidParameterException if the provided metadata instance is already in a deleted state
     */
    public static void validateInstanceIsNotDeleted(IPersistentMap existing,
                                                    String instanceGUID,
                                                    String className,
                                                    String methodName) throws InvalidParameterException {
        Integer currentStatus = (Integer) existing.valAt(CURRENT_STATUS);
        if (currentStatus != null && currentStatus == InstanceStatus.DELETED.getOrdinal()) {
            throw new InvalidParameterException(XtdbOMRSErrorCode.INSTANCE_ALREADY_DELETED.getMessageDefinition(
                    instanceGUID), className, methodName, "instance");
        }
    }

    /**
     * Validates that the provided entity is not only an EntityProxy.
     *
     * @param existing entity to check
     * @param entityGUID of the entity
     * @param className doing the validation
     * @param methodName doing the validation
     * @throws EntityNotKnownException if the provided entity is only an EntityProxy
     */
    public static void validateNonProxyEntity(IPersistentMap existing,
                                              String entityGUID,
                                              String className,
                                              String methodName) throws EntityNotKnownException {
        Boolean proxyOnly = (Boolean) existing.valAt(ENTITY_PROXY_ONLY_MARKER);
        if (proxyOnly != null && proxyOnly) {
            throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, null), className, methodName);
        }
    }

    /**
     * Verify that a metadata instance can be updated by the metadataCollection. The caller can update a
     * metadata instance provided: the instance is locally homed (matching metadataCollectionId) OR the
     * instance has instanceProvenanceType set to external and replicatedBy is set to the local metadataCollectionId.
     *
     * Any other combination suggests that this is either a reference copy of an instance from the local cohort or
     * a reference copy of an external entity (and something else is responsible for its replication): in these cases
     * we are not permitted to update the metadata instance and will instead throw an InvalidParameterException.
     *
     * @param instance instance to validate
     * @param guid unique identifier for the metadata instance being updated
     * @param metadataCollectionId unique identifier for the metadata collection
     * @param className name of the calling class
     * @param methodName name of calling method
     * @throws InvalidParameterException the instance cannot be updated due to its status or metadataCollectionId
     */
    public static void validateInstanceCanBeUpdated(IPersistentMap instance,
                                                    String guid,
                                                    String metadataCollectionId,
                                                    String className,
                                                    String methodName) throws InvalidParameterException

    {

        boolean updateAllowed = true;

        Integer instanceProvenance = (Integer) instance.valAt(INSTANCE_PROVENANCE_TYPE);
        if (instanceProvenance != null) {
            if (instanceProvenance == InstanceProvenanceType.LOCAL_COHORT.getOrdinal()) {
                String entityHome = (String) instance.valAt(METADATA_COLLECTION_ID);
                if (entityHome != null && !entityHome.equals(metadataCollectionId)) {
                    updateAllowed = false;
                }
            } else if (instanceProvenance == InstanceProvenanceType.EXTERNAL_SOURCE.getOrdinal()) {
                String replicatedBy = (String) instance.valAt(REPLICATED_BY);
                if (replicatedBy != null && !replicatedBy.equals(metadataCollectionId)) {
                    updateAllowed = false;
                }
            } else {
                updateAllowed = false;
            }
        } else {
            updateAllowed = false;
        }

        if (!updateAllowed) {
            throw new InvalidParameterException(OMRSErrorCode.INSTANCE_HOME_NOT_LOCAL.getMessageDefinition((String)instance.valAt(METADATA_COLLECTION_ID),
                    methodName,
                    guid,
                    metadataCollectionId),
                    className,
                    methodName,
                    "instance");
        }
    }

    /**
     * Validates an instance status where null is not allowed.
     *
     * @param guid unique identifier for the metadata instance being updated
     * @param propertyName name of the required property
     * @param propertyValue value of the required property
     * @param className class called
     * @param methodName method called
     * @throws InvalidParameterException invalid parameter
     */
    public static void validateRequiredProperty(String guid,
                                                String propertyName,
                                                Object propertyValue,
                                                String className,
                                                String methodName) throws InvalidParameterException {
        if (propertyValue == null) {
            throw new InvalidParameterException(XtdbOMRSErrorCode.NULL_REQUIRED_PROPERTY.getMessageDefinition(propertyName, guid),
                    className,
                    methodName,
                    propertyName);
        }
    }

    /**
     * Makes the necessary changes to mark a metadata instance as soft-deleted.
     *
     * @param userId doing the deletion
     * @param existing metadata instance
     * @return IPersistentMap giving the deleted instance representation
     */
    public static IPersistentMap deleteInstance(String userId,
                                                IPersistentMap existing) {
        IPersistentMap doc = TxnUtils.incrementVersion(userId, existing);
        return doc
                .assoc(TxnUtils.STATUS_ON_DELETE, doc.valAt(TxnUtils.CURRENT_STATUS))
                .assoc(TxnUtils.CURRENT_STATUS, EnumPropertyValueMapping.getOrdinalForInstanceStatus(InstanceStatus.DELETED));
    }

    /**
     * Makes the necessary changes to update a metadata instance's status.
     *
     * @param userId doing the update
     * @param existing metadata instance
     * @param instanceStatus new status for the instance
     * @return IPersistentMap giving the updated instance representation
     */
    public static IPersistentMap updateInstanceStatus(String userId,
                                                      IPersistentMap existing,
                                                      int instanceStatus) {
        IPersistentMap doc = TxnUtils.incrementVersion(userId, existing);
        return doc.assoc(TxnUtils.CURRENT_STATUS, instanceStatus);
    }

    /**
     * Makes the necessary changes to update a metadata instance's status.
     *
     * @param userId doing the update
     * @param existing metadata instance
     * @param properties full set of properties for the instance
     * @return IPersistentMap giving the updated instance representation
     */
    public static IPersistentMap updateInstanceProperties(String userId,
                                                          IPersistentMap existing,
                                                          InstanceProperties properties) {
        IPersistentMap doc = TxnUtils.incrementVersion(userId, existing);
        // TODO: see logic in InstancePropertiesMapping -- likely needs type input...
        if (properties == null) {
            // TODO: iterate through known properties, set all to null
        } else {
            // TODO: iterate through known properties, set received properties as-received and others to null
        }
        return doc;
        //return doc.assoc(TxnUtils.CURRENT_STATUS, instanceStatus);
    }

}
