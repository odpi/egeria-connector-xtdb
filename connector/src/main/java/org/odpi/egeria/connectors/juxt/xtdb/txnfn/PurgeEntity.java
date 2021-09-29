/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.cache.ErrorMessageCache;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntityDetailMapping;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.InstanceAuditHeaderMapping;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.RelationshipMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityNotDeletedException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityNotKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.InvalidParameterException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.TransactionInstant;
import xtdb.api.tx.Transaction;

/**
 * Transaction function for hard-deleting an entity.
 */
public class PurgeEntity extends AbstractTransactionFunction {

    private static final Logger log = LoggerFactory.getLogger(PurgeEntity.class);

    public static final Keyword FUNCTION_NAME = Keyword.intern("egeria", "purgeEntity");
    private static final String CLASS_NAME = PurgeEntity.class.getName();
    private static final String METHOD_NAME = FUNCTION_NAME.toString();

    // Query to retrieve ALL relationships that point to this entity, irrespective of status or
    // home repository
    private static final String RELN_QUERY = "" +
            "(quote {:find [r]" +
            " :where [[r :" + RelationshipMapping.ENTITY_PROXIES + " e]" +
            "         [r :" + InstanceAuditHeaderMapping.TYPE_DEF_CATEGORY + " " + TypeDefCategory.RELATIONSHIP_DEF.getOrdinal() + "]]" +
            " :in [e]})";

    private static final String FN = "" +
            "(fn [ctx eid force] " +
            "    (let [db (xtdb.api/db ctx)" +
            "          tx-id (:tx-id db)" +
            "          relationships (xtdb.api/q db " + RELN_QUERY + " eid)" +
            "          existing (xtdb.api/entity db eid)" +
            "          deleted (.doc (" + PurgeEntity.class.getCanonicalName() + ". tx-id existing eid force))]" +
            // For each of the relationships that was found, delegate to the purge transaction function
            "         (conj (vec (for [[rid] relationships]" +
            "                     [:xtdb.api/fn " + PurgeRelationship.FUNCTION_NAME + " rid true]))" +
            // And of course also purge the entity itself as part of this transaction
            "               [:xtdb.api/evict eid])))";

    private final IPersistentMap xtdbDoc;

    /**
     * Constructor used to execute the transaction function.
     * @param txId the transaction ID of this function invocation
     * @param existing XTDB document to update
     * @param deletedEntityGUID of the entity to purge
     * @param force if true do not validate whether the entity is soft-deleted prior to purging it
     * @throws Exception on any error
     */
    public PurgeEntity(Long txId,
                       PersistentHashMap existing,
                       String deletedEntityGUID,
                       boolean force)
            throws Exception {
        try {
            if (existing == null) {
                throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_NOT_KNOWN.getMessageDefinition(
                        deletedEntityGUID), CLASS_NAME, METHOD_NAME);
            } else {
                // If all we have is an EntityProxy, do not remove it and respond that we have no such entity
                // (Purging the EntityProxy would mean invalidating any relationships that use that EntityProxy: if such
                // relationship removal is intended, it should come through from the same source that has purged the entity,
                // otherwise this may simply be a cleanup of the locally-cached reference copy, but not an actual purge of
                // the entity from the home repository -- in which case we may still want to retain relationships.)
                TxnValidations.nonProxyEntity(existing, deletedEntityGUID, this.getClass().getName(), METHOD_NAME);
                if (!force) {
                    TxnValidations.entityFromStore(deletedEntityGUID, existing, CLASS_NAME, METHOD_NAME);
                    try {
                        TxnValidations.instanceIsDeleted(existing, deletedEntityGUID, CLASS_NAME, METHOD_NAME);
                    } catch (InvalidParameterException e) {
                        throw new EntityNotDeletedException(XtdbOMRSErrorCode.INSTANCE_NOT_DELETED.getMessageDefinition(
                                deletedEntityGUID), CLASS_NAME, METHOD_NAME);
                    }
                }
            }
            xtdbDoc = existing;
        } catch (Exception e) {
            throw ErrorMessageCache.add(txId, e);
        }
    }

    /**
     * Permanently delete the entity (and all of its history) from the XTDB repository by pushing down the transaction.
     * Note that this operation is NOT reversible!
     * @param xtdb connectivity
     * @param entityGUID of the entity to permanently delete
     * @throws EntityNotKnownException if the entity cannot be found
     * @throws EntityNotDeletedException if the entity exists but is not in a soft-deleted state
     * @throws RepositoryErrorException on any other error
     */
    public static void transactWithValidation(XtdbOMRSRepositoryConnector xtdb,
                                              String entityGUID)
            throws EntityNotKnownException, EntityNotDeletedException, RepositoryErrorException {
        String docId = EntityDetailMapping.getReference(entityGUID);
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, false);
        TransactionInstant results = xtdb.runTx(tx.build());
        try {
            xtdb.validateCommit(results, METHOD_NAME);
        } catch (EntityNotKnownException | EntityNotDeletedException | RepositoryErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                    DeleteEntity.class.getName(),
                    METHOD_NAME,
                    e);
        }
    }

    /**
     * Permanently delete the entity (and all of its history) from the XTDB repository by pushing down the transaction.
     * Note that this operation is NOT reversible!
     * @param xtdb connectivity
     * @param entityGUID of the entity to permanently delete
     * @throws EntityNotKnownException if the entity cannot be found
     * @throws RepositoryErrorException on any other error
     */
    public static void transactWithoutValidation(XtdbOMRSRepositoryConnector xtdb,
                                                 String entityGUID)
            throws EntityNotKnownException, RepositoryErrorException {
        String docId = EntityDetailMapping.getReference(entityGUID);
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, true);
        TransactionInstant results = xtdb.runTx(tx.build());
        try {
            xtdb.validateCommit(results, METHOD_NAME);
        } catch (EntityNotKnownException | RepositoryErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                    DeleteEntity.class.getName(),
                    METHOD_NAME,
                    e);
        }
    }

    /**
     * Interface that returns the document about to be evicted (purged).
     * @return IPersistentMap giving the purged document in its entirety
     */
    public IPersistentMap doc() {
        log.debug("Entity being purged: {}", xtdbDoc);
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
