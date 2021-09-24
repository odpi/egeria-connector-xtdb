/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntityDetailMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityDetail;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityNotKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.InvalidParameterException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.TransactionInstant;
import xtdb.api.tx.Transaction;

/**
 * Transaction function for updating an entity's status.
 */
public class UpdateEntityStatus extends AbstractTransactionFunction {

    private static final Logger log = LoggerFactory.getLogger(UpdateEntityStatus.class);

    public static final Keyword FUNCTION_NAME = Keyword.intern("egeria", "updateEntityStatus");
    private static final String CLASS_NAME = UpdateEntityStatus.class.getName();
    private static final String METHOD_NAME = FUNCTION_NAME.toString();
    private static final String FN = "" +
            "(fn [ctx eid user status mid] " +
            "    (let [db (xtdb.api/db ctx)" +
            "          existing (xtdb.api/entity db eid)" +
            "          updated (.doc (" + UpdateEntityStatus.class.getCanonicalName() + ". existing user eid mid status))]" +
            "         [[:xtdb.api/put updated]]))";

    private final IPersistentMap xtdbDoc;

    /**
     * Constructor used to execute the transaction function.
     * @param existing XTDB document to update
     * @param userId doing the update
     * @param entityGUID of the entity to update
     * @param metadataCollectionId of the metadata collection in which the transaction is running
     * @param instanceStatus to apply to the entity
     * @throws EntityNotKnownException if the entity cannot be found
     * @throws InvalidParameterException if the entity exists but cannot be updated (deleted, reference copy, etc)
     */
    public UpdateEntityStatus(PersistentHashMap existing,
                              String userId,
                              String entityGUID,
                              String metadataCollectionId,
                              Integer instanceStatus)
            throws InvalidParameterException, EntityNotKnownException {

        if (existing == null) {
            throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_NOT_KNOWN.getMessageDefinition(
                    entityGUID), this.getClass().getName(), METHOD_NAME);
        } else {
            TxnUtils.validateNonProxyEntity(existing, entityGUID, CLASS_NAME, METHOD_NAME);
            TxnUtils.validateInstanceIsNotDeleted(existing, entityGUID, CLASS_NAME, METHOD_NAME);
            TxnUtils.validateInstanceCanBeUpdated(existing, entityGUID, metadataCollectionId, CLASS_NAME, METHOD_NAME);
            TxnUtils.validateRequiredProperty(entityGUID, TxnUtils.CURRENT_STATUS.getName(), instanceStatus, CLASS_NAME, METHOD_NAME);
            xtdbDoc = TxnUtils.updateInstanceStatus(userId, existing, instanceStatus);
        }

    }

    /**
     * Update the status of the provided entity instance in the XTDB repository by pushing the transaction
     * down into the repository itself.
     * @param xtdb connectivity
     * @param userId doing the update
     * @param entityGUID of the entity on which to update the status
     * @param newStatus to apply to the entity
     * @return EntityDetail of the entity with the new status applied
     * @throws RepositoryErrorException on any error
     */
    public static EntityDetail transact(XtdbOMRSRepositoryConnector xtdb,
                                        String userId,
                                        String entityGUID,
                                        InstanceStatus newStatus) throws RepositoryErrorException {
        String docId = EntityDetailMapping.getReference(entityGUID);
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, userId, newStatus.getOrdinal(), xtdb.getMetadataCollectionId());
        TransactionInstant results = xtdb.runTx(tx.build());
        return xtdb.getResultingEntity(docId, results, METHOD_NAME);
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
