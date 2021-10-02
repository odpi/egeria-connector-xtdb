/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.cache.ErrorMessageCache;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.ClassificationMapping;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntityDetailMapping;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.InstanceAuditHeaderMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.Classification;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityDetail;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.TransactionInstant;
import xtdb.api.XtdbDocument;
import xtdb.api.tx.Transaction;

/**
 * Transaction function for adding a reference copy classification.
 */
public class SaveClassificationReferenceCopy extends AbstractTransactionFunction {

    private static final Logger log = LoggerFactory.getLogger(SaveClassificationReferenceCopy.class);

    public static final Keyword FUNCTION_NAME = Keyword.intern("egeria", "saveClassificationReferenceCopy");
    private static final String CLASS_NAME = SaveClassificationReferenceCopy.class.getName();
    private static final String METHOD_NAME = FUNCTION_NAME.toString();

    private static final String FN = "" +
            "(fn [ctx eid e rcc mid] " +
            "    (let [db (xtdb.api/db ctx)" +
            "          tx-id (:tx-id db)" +
            "          existing (xtdb.api/entity db eid)" +
            "          updated (.doc (" + SaveClassificationReferenceCopy.class.getCanonicalName() + ". tx-id eid existing e rcc mid))" +
            getTxnTimeCalculation("updated") +
            // Retrieve the metadata collection ID of the updated entity (if there is one)
            "          nmid (when (some? updated)" +
            "                     (get updated :" + InstanceAuditHeaderMapping.METADATA_COLLECTION_ID + "))]" +
            // Only proceed if there is some update to apply
            "         (when (some? nmid)" +
            "          (if (= mid nmid)" +
            // If the entity is homed in this repository, apply it as a normal put
            "            [[:xtdb.api/put updated txt]]" +
            // If we are updating a reference copy entity, instead delegate to the reference copy transaction function
            "            [[:xtdb.api/fn " + SaveEntityReferenceCopy.FUNCTION_NAME + " eid updated mid]]))))";

    private final IPersistentMap xtdbDoc;

    /**
     * Constructor used to execute the transaction function.
     * @param txId the transaction ID of this function invocation
     * @param entityGUID unique identifier of the existing entity
     * @param existing the existing entity in XT, if any
     * @param entity the entity against which to apply the classification
     * @param classification the classification to persist as a reference copy
     * @param homeMetadataCollectionId the metadataCollectionId of the repository where the transaction is running
     * @throws Exception on any error
     */
    public SaveClassificationReferenceCopy(Long txId,
                                           String entityGUID,
                                           PersistentHashMap existing,
                                           PersistentHashMap entity,
                                           Classification classification,
                                           String homeMetadataCollectionId)
            throws Exception {

        try {

            IPersistentMap docToUpdate = existing;
            if (existing == null && (!homeMetadataCollectionId.equals(getMetadataCollectionId(entity)))) {
                // If the entity against which to store the classification is itself a reference copy,
                // store everything together as a reference copy.
                docToUpdate = entity;
            }

            if (docToUpdate != null) {
                try {
                    TxnValidations.entityFromStore(entityGUID, docToUpdate, CLASS_NAME, METHOD_NAME);
                    TxnValidations.instanceIsNotDeleted(docToUpdate, entityGUID, CLASS_NAME, METHOD_NAME);
                    TxnValidations.instanceType(docToUpdate, CLASS_NAME, METHOD_NAME);
                    TxnValidations.classification(classification.getName(), getTypeDefForInstance(docToUpdate).getName(), CLASS_NAME, METHOD_NAME);
                    TxnValidations.classificationProperties(classification.getName(), classification.getProperties(), CLASS_NAME, METHOD_NAME);
                    docToUpdate = ClassificationMapping.addToMap(docToUpdate, classification);
                } catch (EntityNotKnownException e) {
                    // Ignore since the entity has been removed since the classification was added
                    log.debug("Entity with GUID {} was not known, so classification not stored.", entityGUID);
                    docToUpdate = null;
                } catch (ClassificationErrorException e) {
                    throw new TypeErrorException(e);
                }
            }
            xtdbDoc = docToUpdate;

        } catch (Exception e) {
            throw ErrorMessageCache.add(txId, e);
        }

    }

    /**
     * Create the provided classification in the XTDB repository by pushing down the transaction.
     * @param xtdb connectivity
     * @param toStoreAgainst the entity against which to persist the classification
     * @param classification to persist as a reference copy
     * @throws EntityConflictException the new entity conflicts with an existing entity
     * @throws TypeErrorException the requested type is not known or not supported
     * @throws PropertyErrorException one or more of the requested properties are not defined or have different characteristics in the TypeDef for this classification type
     * @throws InvalidParameterException one of the parameters is invalid or null
     * @throws RepositoryErrorException on any other error
     */
    public static void transact(XtdbOMRSRepositoryConnector xtdb,
                                EntityDetail toStoreAgainst,
                                Classification classification)
            throws EntityConflictException, TypeErrorException, PropertyErrorException, InvalidParameterException, RepositoryErrorException {
        String docId = EntityDetailMapping.getReference(toStoreAgainst.getGUID());
        EntityDetailMapping edm = new EntityDetailMapping(xtdb, toStoreAgainst);
        XtdbDocument toStoreAgainstXT = edm.toXTDB();
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, toStoreAgainstXT.toMap(), classification, xtdb.getMetadataCollectionId());
        TransactionInstant results = xtdb.runTx(tx.build());
        try {
            xtdb.validateCommit(results, METHOD_NAME);
        } catch (EntityConflictException | TypeErrorException | PropertyErrorException | InvalidParameterException | RepositoryErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                    CLASS_NAME,
                    METHOD_NAME,
                    e);
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
