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
public class PurgeClassificationReferenceCopy extends AbstractTransactionFunction {

    private static final Logger log = LoggerFactory.getLogger(PurgeClassificationReferenceCopy.class);

    public static final Keyword FUNCTION_NAME = Keyword.intern("egeria", "purgeClassificationReferenceCopy");
    private static final String CLASS_NAME = PurgeClassificationReferenceCopy.class.getName();
    private static final String METHOD_NAME = FUNCTION_NAME.toString();

    private static final String FN = "" +
            "(fn [ctx eid e rcc mid] " +
            "    (let [db (xtdb.api/db ctx)" +
            "          tx-id (:tx-id db)" +
            "          existing (xtdb.api/entity db eid)" +
            "          updated (.doc (" + PurgeClassificationReferenceCopy.class.getCanonicalName() + ". tx-id existing e rcc mid))" +
            // Retrieve the metadata collection ID of the updated entity (if there is one)
            "          nmid (when (some? updated)" +
            "                     (get updated :" + InstanceAuditHeaderMapping.METADATA_COLLECTION_ID + "))" +
            getTxnTimeCalculation("updated") + "]" +
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
     * @param existing the existing entity in XT, if any
     * @param entity the entity against which to apply the classification
     * @param classification the classification to persist as a reference copy
     * @param homeMetadataCollectionId the metadataCollectionId of the repository where the transaction is running
     * @throws Exception on any error
     */
    public PurgeClassificationReferenceCopy(Long txId,
                                            PersistentHashMap existing,
                                            PersistentHashMap entity,
                                            Classification classification,
                                            String homeMetadataCollectionId)
            throws Exception {

        try {

            IPersistentMap docToUpdate = existing;
            if (existing == null && (!homeMetadataCollectionId.equals(getMetadataCollectionId(entity)))) {
                // If the entity from which to purge the classification is itself a reference copy,
                // update that reference copy.
                docToUpdate = entity;
            }

            if (docToUpdate != null) {
                try {
                    docToUpdate = ClassificationMapping.removeFromMap(docToUpdate, classification.getName());
                } catch (ClassificationErrorException e) {
                    // Do nothing: this simply means the repository did not have the classification reference copy stored
                    // anyway, so nothing to remove (no-op)
                    log.debug("Entity with GUID {} had no classification {}, nothing to purge.", getGUID(entity), classification.getName());
                    docToUpdate = null;
                }
            }
            xtdbDoc = docToUpdate;

        } catch (Exception e) {
            throw ErrorMessageCache.add(txId, e);
        }

    }

    /**
     * Permanently remove the provided classification from the XTDB repository by pushing down the transaction.
     * @param xtdb connectivity
     * @param toPurgeFrom the entity from which to remove the classification
     * @param classification to permanently remove
     * @throws EntityConflictException the new entity conflicts with an existing entity
     * @throws RepositoryErrorException on any other error
     */
    public static void transact(XtdbOMRSRepositoryConnector xtdb,
                                EntityDetail toPurgeFrom,
                                Classification classification)
            throws EntityConflictException, RepositoryErrorException {
        String docId = EntityDetailMapping.getReference(toPurgeFrom.getGUID());
        EntityDetailMapping edm = new EntityDetailMapping(xtdb, toPurgeFrom);
        XtdbDocument toPurgeFromXT = edm.toXTDB();
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, toPurgeFromXT.toMap(), classification, xtdb.getMetadataCollectionId());
        TransactionInstant results = xtdb.runTx(tx.build());
        try {
            xtdb.validateCommit(results, METHOD_NAME);
        } catch (EntityConflictException | RepositoryErrorException e) {
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
