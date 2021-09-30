/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.cache.ErrorMessageCache;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.*;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.TransactionInstant;
import xtdb.api.tx.Transaction;

/**
 * Transaction function for removing a classification to an entity.
 */
public class DeclassifyEntity extends AbstractTransactionFunction {

    private static final Logger log = LoggerFactory.getLogger(DeclassifyEntity.class);

    public static final Keyword FUNCTION_NAME = Keyword.intern("egeria", "declassifyEntity");
    private static final String CLASS_NAME = DeclassifyEntity.class.getName();
    private static final String METHOD_NAME = FUNCTION_NAME.toString();
    private static final String FN = "" +
            "(fn [ctx eid cname] " +
            "    (let [db (xtdb.api/db ctx)" +
            "          tx-id (:tx-id db)" +
            "          existing (xtdb.api/entity db eid)" +
            "          updated (.doc (" + DeclassifyEntity.class.getCanonicalName() + ". tx-id existing eid cname))" +
            getTxnTimeCalculation("updated") + "]" +
            "         [[:xtdb.api/put updated txt]]))";

    private final IPersistentMap xtdbDoc;

    /**
     * Constructor used to execute the transaction function.
     * @param txId the transaction ID of this function invocation
     * @param existing XTDB document to update
     * @param entityGUID of the entity to update
     * @param classificationName name of the classification
     * @throws Exception on any error
     */
    public DeclassifyEntity(Long txId,
                            PersistentHashMap existing,
                            String entityGUID,
                            String classificationName)
            throws Exception {

        try {
            if (existing == null) {
                throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_NOT_KNOWN.getMessageDefinition(
                        entityGUID), CLASS_NAME, METHOD_NAME);
            } else {

                TxnValidations.nonProxyEntity(existing, entityGUID, CLASS_NAME, METHOD_NAME);
                TxnValidations.requiredProperty(entityGUID, "classificationName", classificationName, CLASS_NAME, METHOD_NAME);
                TxnValidations.entityFromStore(entityGUID, existing, CLASS_NAME, METHOD_NAME);
                TxnValidations.instanceIsNotDeleted(existing, entityGUID, CLASS_NAME, METHOD_NAME);

                xtdbDoc = ClassificationMapping.removeFromMap(existing, classificationName);

            }
        } catch (Exception e) {
            throw ErrorMessageCache.add(txId, e);
        }

    }

    /**
     * Update the status of the provided entity instance in the XTDB repository by pushing the transaction
     * down into the repository itself.
     * @param xtdb connectivity
     * @param entityGUID of the entity on which to update the status
     * @param classificationName name of the classification
     * @return EntityDetail of the entity with the new status applied
     * @throws EntityNotKnownException if the entity cannot be found
     * @throws InvalidParameterException if the entity exists but cannot be updated (deleted, reference copy, etc)
     * @throws ClassificationErrorException if there is any issue related to the classification
     * @throws RepositoryErrorException on any other error
     */
    public static EntityDetail transact(XtdbOMRSRepositoryConnector xtdb,
                                        String entityGUID,
                                        String classificationName)
            throws EntityNotKnownException, InvalidParameterException, ClassificationErrorException, RepositoryErrorException {
        String docId = EntityDetailMapping.getReference(entityGUID);
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, classificationName);
        TransactionInstant results = xtdb.runTx(tx.build());
        try {
            return xtdb.getResultingEntity(docId, results, METHOD_NAME);
        } catch (EntityNotKnownException | InvalidParameterException | ClassificationErrorException | RepositoryErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                    DeclassifyEntity.class.getName(),
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
