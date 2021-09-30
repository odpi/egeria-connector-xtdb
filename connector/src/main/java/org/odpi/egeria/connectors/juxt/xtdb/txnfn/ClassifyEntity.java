/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.cache.ErrorMessageCache;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.cache.TypeDefCache;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.*;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.TransactionInstant;
import xtdb.api.tx.Transaction;

import java.util.*;

/**
 * Transaction function for adding / updating a classification to an entity.
 */
public class ClassifyEntity extends AbstractTransactionFunction {

    private static final Logger log = LoggerFactory.getLogger(ClassifyEntity.class);

    public static final Keyword FUNCTION_NAME = Keyword.intern("egeria", "classifyEntity");
    private static final String CLASS_NAME = ClassifyEntity.class.getName();
    private static final String METHOD_NAME = FUNCTION_NAME.toString();
    private static final String FN = "" +
            "(fn [ctx eid user cname ext-guid ext-name corigin corigin-guid properties mid] " +
            "    (let [db (xtdb.api/db ctx)" +
            "          tx-id (:tx-id db)" +
            "          existing (xtdb.api/entity db eid)" +
            "          updated (.doc (" + ClassifyEntity.class.getCanonicalName() + ". tx-id existing user eid mid cname ext-guid ext-name corigin corigin-guid properties))" +
            getTxnTimeCalculation("updated") + "]" +
            "         [[:xtdb.api/put updated txt]]))";

    private final IPersistentMap xtdbDoc;

    /**
     * Constructor used to execute the transaction function.
     * @param txId the transaction ID of this function invocation
     * @param existing XTDB document to update
     * @param userId doing the update
     * @param entityGUID of the entity to update
     * @param metadataCollectionId of the metadata collection in which the transaction is running
     * @param classificationName name of the classification
     * @param externalSourceGUID unique identifier for the external source
     * @param externalSourceName unique name for the external source
     * @param classificationOrigin source of the classification (assigned or propagated)
     * @param classificationOriginGUID unique identifier of the entity that propagated the classification (if propagated)
     * @param properties the properties to set on the classification
     * @throws Exception on any error
     */
    public ClassifyEntity(Long txId,
                          PersistentHashMap existing,
                          String userId,
                          String entityGUID,
                          String metadataCollectionId,
                          String classificationName,
                          String externalSourceGUID,
                          String externalSourceName,
                          ClassificationOrigin classificationOrigin,
                          String classificationOriginGUID,
                          InstanceProperties properties)
            throws Exception {

        try {
            if (existing == null) {
                throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_NOT_KNOWN.getMessageDefinition(
                        entityGUID), CLASS_NAME, METHOD_NAME);
            } else {

                TxnValidations.nonProxyEntity(existing, entityGUID, CLASS_NAME, METHOD_NAME);
                TxnValidations.entityFromStore(entityGUID, existing, CLASS_NAME, METHOD_NAME);
                TxnValidations.instanceIsNotDeleted(existing, entityGUID, CLASS_NAME, METHOD_NAME);
                TxnValidations.instanceType(existing, CLASS_NAME, METHOD_NAME);
                InstanceType entityType = InstanceAuditHeaderMapping.getTypeFromInstance(existing, null);
                TxnValidations.classification(classificationName, entityType.getTypeDefName(), CLASS_NAME, METHOD_NAME);

                String entityTypeDefGUID = getTypeDefGUID(existing);
                TypeDef entityTypeDef = TypeDefCache.getTypeDef(entityTypeDefGUID);
                String entityTypeDefName = entityTypeDef.getName();

                Classification newClassification;
                try {
                    TxnValidations.classificationProperties(classificationName, properties, CLASS_NAME, METHOD_NAME);
                    if (externalSourceGUID == null) {
                        newClassification = getNewClassification(
                                metadataCollectionId,
                                null,
                                InstanceProvenanceType.LOCAL_COHORT,
                                userId,
                                classificationName,
                                entityTypeDefName,
                                classificationOrigin == null ? ClassificationOrigin.ASSIGNED : classificationOrigin,
                                classificationOriginGUID,
                                properties);
                    } else {
                        newClassification = getNewClassification(
                                externalSourceGUID,
                                externalSourceName,
                                InstanceProvenanceType.EXTERNAL_SOURCE,
                                userId,
                                classificationName,
                                entityTypeDefName,
                                classificationOrigin == null ? ClassificationOrigin.ASSIGNED : classificationOrigin,
                                classificationOriginGUID,
                                properties);
                        newClassification.setMetadataCollectionName(externalSourceName);
                        newClassification.setReplicatedBy(metadataCollectionId);
                    }
                } catch (TypeErrorException e) {
                    throw new ClassificationErrorException(XtdbOMRSErrorCode.INVALID_CLASSIFICATION_FOR_ENTITY.getMessageDefinition(
                            classificationName, entityTypeDefName), CLASS_NAME, METHOD_NAME, e);
                }

                xtdbDoc = ClassificationMapping.addToMap(existing, newClassification);

            }
        } catch (Exception e) {
            throw ErrorMessageCache.add(txId, e);
        }

    }

    /**
     * Update the status of the provided entity instance in the XTDB repository by pushing the transaction
     * down into the repository itself.
     * @param xtdb connectivity
     * @param userId doing the update
     * @param entityGUID of the entity on which to update the status
     * @param classificationName name of the classification
     * @param externalSourceGUID unique identifier for the external source
     * @param externalSourceName unique name for the external source
     * @param classificationOrigin source of the classification (assigned or propagated)
     * @param classificationOriginGUID unique identifier of the entity that propagated the classification (if propagated)
     * @param properties the properties to set on the classification
     * @return EntityDetail of the entity with the new status applied
     * @throws EntityNotKnownException if the entity cannot be found
     * @throws InvalidParameterException if the entity exists but cannot be updated (deleted, reference copy, etc)
     * @throws ClassificationErrorException if there is any issue related to the classification
     * @throws PropertyErrorException one or more of the requested properties are not defined, or have different characteristics in the TypeDef for this classification type
     * @throws RepositoryErrorException on any other error
     */
    public static EntityDetail transact(XtdbOMRSRepositoryConnector xtdb,
                                        String userId,
                                        String entityGUID,
                                        String classificationName,
                                        String externalSourceGUID,
                                        String externalSourceName,
                                        ClassificationOrigin classificationOrigin,
                                        String classificationOriginGUID,
                                        InstanceProperties properties)
            throws EntityNotKnownException, InvalidParameterException, ClassificationErrorException, PropertyErrorException, RepositoryErrorException {
        String docId = EntityDetailMapping.getReference(entityGUID);
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, userId, classificationName, externalSourceGUID, externalSourceName, classificationOrigin, classificationOriginGUID, properties, xtdb.getMetadataCollectionId());
        TransactionInstant results = xtdb.runTx(tx.build());
        try {
            return xtdb.getResultingEntity(docId, results, METHOD_NAME);
        } catch (EntityNotKnownException | InvalidParameterException | ClassificationErrorException | PropertyErrorException | RepositoryErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                    ClassifyEntity.class.getName(),
                    METHOD_NAME,
                    e);
        }
    }

    /**
     * Return a complete, new classification.
     * @param metadataCollectionId unique identifier for the home metadata collection
     * @param metadataCollectionName unique name for the home metadata collection
     * @param provenanceType origin of the classification
     * @param userName name of the creator
     * @param classificationTypeName name of the type definition for the classification
     * @param entityTypeName name of the type definition for the entity the classification will be applied to
     * @param classificationOrigin indicating whether this was explicitly assigned or propagated
     * @param classificationOriginGUID if propagated, this is the GUID of the origin
     * @param properties properties for the classification
     * @return Classification
     * @throws TypeErrorException if the type names are not recognized or are not valid relative to each other
     */
    private static Classification getNewClassification(String metadataCollectionId,
                                                       String metadataCollectionName,
                                                       InstanceProvenanceType provenanceType,
                                                       String userName,
                                                       String classificationTypeName,
                                                       String entityTypeName,
                                                       ClassificationOrigin classificationOrigin,
                                                       String classificationOriginGUID,
                                                       InstanceProperties properties) throws TypeErrorException {

        final String methodName = "getNewClassification";

        if (TypeDefCache.isValidClassificationForEntity(classificationTypeName, entityTypeName)) {

            Classification classification = new Classification();

            classification.setHeaderVersion(InstanceAuditHeader.CURRENT_AUDIT_HEADER_VERSION);
            classification.setInstanceProvenanceType(provenanceType);
            classification.setMetadataCollectionId(metadataCollectionId);
            classification.setMetadataCollectionName(metadataCollectionName);
            classification.setName(classificationTypeName);
            classification.setCreateTime(new Date());
            classification.setCreatedBy(userName);
            classification.setVersion(1L);
            classification.setType(TypeDefCache.getInstanceType(TypeDefCategory.CLASSIFICATION_DEF, classificationTypeName));
            classification.setStatus(TypeDefCache.getInitialStatus(classificationTypeName));

            classification.setClassificationOrigin(classificationOrigin);
            classification.setClassificationOriginGUID(classificationOriginGUID);
            classification.setProperties(properties);

            return classification;

        } else {
            throw new TypeErrorException(XtdbOMRSErrorCode.INVALID_CLASSIFICATION_FOR_ENTITY.getMessageDefinition(
                    classificationTypeName, entityTypeName),
                    ClassifyEntity.class.getName(),
                    methodName);
        }

    }

    /**
     * Interface that returns the updated document to write-back from the transaction.
     * @return IPersistentMap giving the updated document in its entirety
     */
    public IPersistentMap doc() {
        log.debug("Entity being persisted: {}", xtdbDoc);
        return xtdbDoc;
    }

    /**
     * Create the transaction function within XTDB.
     * @param tx transaction through which to create the function
     */
    public static void create(Transaction.Builder tx) {
        createTransactionFunction(tx, FUNCTION_NAME, FN);
    }

}
