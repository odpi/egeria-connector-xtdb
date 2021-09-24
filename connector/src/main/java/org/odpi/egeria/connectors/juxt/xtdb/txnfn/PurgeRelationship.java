/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.RelationshipMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RelationshipNotDeletedException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RelationshipNotKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.TransactionInstant;
import xtdb.api.tx.Transaction;

/**
 * Transaction function for hard-deleting a relationship.
 */
public class PurgeRelationship extends AbstractTransactionFunction {

    private static final Logger log = LoggerFactory.getLogger(PurgeRelationship.class);

    public static final Keyword FUNCTION_NAME = Keyword.intern("egeria", "purgeRelationship");
    private static final String METHOD_NAME = FUNCTION_NAME.toString();
    private static final String FN = "" +
            "(fn [ctx rid force] " +
            "    (let [db (xtdb.api/db ctx)" +
            "          existing (xtdb.api/entity db rid)" +
            "          deleted (.doc (" + PurgeRelationship.class.getCanonicalName() + ". existing rid force))]" +
            "         [[:xtdb.api/evict rid]]))";

    private final IPersistentMap xtdbDoc;

    /**
     * Constructor used to execute the transaction function.
     * @param existing XTDB document to update
     * @param deletedRelationshipGUID of the relationship to purge
     * @param force if true, do not validate whether the relationship is already soft-deleted before purging
     * @throws RelationshipNotKnownException if the relationship cannot be found
     * @throws RelationshipNotDeletedException if the relationship exists but is not in a soft-deleted state
     */
    public PurgeRelationship(PersistentHashMap existing,
                             String deletedRelationshipGUID,
                             boolean force)
            throws RelationshipNotKnownException, RelationshipNotDeletedException {
        if (existing == null) {
            throw new RelationshipNotKnownException(XtdbOMRSErrorCode.RELATIONSHIP_NOT_KNOWN.getMessageDefinition(
                    deletedRelationshipGUID), this.getClass().getName(), METHOD_NAME);
        } else {
            if (!force) {
                Integer currentStatus = (Integer) existing.valAt(TxnUtils.CURRENT_STATUS);
                if (currentStatus == null || currentStatus != InstanceStatus.DELETED.getOrdinal()) {
                    throw new RelationshipNotDeletedException(XtdbOMRSErrorCode.INSTANCE_NOT_DELETED.getMessageDefinition(
                            deletedRelationshipGUID), this.getClass().getName(), METHOD_NAME);
                }
            }
        }
        xtdbDoc = existing;
    }

    /**
     * Permanently delete the relationship (and all of its history) from the XTDB repository by pushing down the transaction.
     * Note that this operation is NOT reversible!
     * @param xtdb connectivity
     * @param relationshipGUID of the relationship to permanently delete
     * @param force if true, do not validate whether the relationship is already soft-deleted but directly purge it
     * @throws RepositoryErrorException on any error
     */
    public static void transact(XtdbOMRSRepositoryConnector xtdb,
                                String relationshipGUID,
                                boolean force) throws RepositoryErrorException {
        String docId = RelationshipMapping.getReference(relationshipGUID);
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, force);
        TransactionInstant results = xtdb.runTx(tx.build());
        xtdb.validateCommit(results, METHOD_NAME);
    }

    /**
     * Interface that returns the document about to be evicted (purged).
     * @return IPersistentMap giving the purged document in its entirety
     */
    public IPersistentMap doc() {
        log.debug("Relationship being purged: {}", xtdbDoc);
        return xtdbDoc;
    }

    /**
     * Create the transaction function within XTDB.
     * @param tx transaction through whic to create the function
     */
    public static void create(Transaction.Builder tx) {
        createTransactionFunction(tx, FUNCTION_NAME, FN);
    }

}
