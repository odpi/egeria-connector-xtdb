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
public abstract class DeclassifyEntity extends AbstractTransactionFunction {

    protected final IPersistentMap xtdbDoc;

    /**
     * Constructor used to execute the transaction function.
     * @param className name of the implementing class
     * @param methodName name of the implemented transaction method
     * @param txId the transaction ID of this function invocation
     * @param existing XTDB document to update
     * @param proxy XTDB document to update, if existing is empty
     * @param entityGUID of the entity to update
     * @param classificationName name of the classification
     * @throws Exception on any error
     */
    public DeclassifyEntity(String className,
                            String methodName,
                            Long txId,
                            PersistentHashMap existing,
                            PersistentHashMap proxy,
                            String entityGUID,
                            String classificationName)
            throws Exception {

        try {
            PersistentHashMap toUpdate;
            if (existing != null) {
                // If we found an existing entity with this GUID, use it (not the provided proxy)
                toUpdate = existing;
            } else if (proxy != null) {
                // Otherwise, fallback to the proxy we've been asked to create
                toUpdate = proxy;
            } else {
                // And in case that was not provided (older declassifyEntity method),
                // exit out with the not found exception
                throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_NOT_KNOWN.getMessageDefinition(
                        entityGUID), className, methodName);
            }
            TxnValidations.requiredProperty(entityGUID, "classificationName", classificationName, className, methodName);
            TxnValidations.entityFromStore(entityGUID, toUpdate, className, methodName);
            TxnValidations.instanceIsNotDeleted(toUpdate, entityGUID, className, methodName);
            xtdbDoc = ClassificationMapping.removeFromMap(toUpdate, classificationName);
        } catch (Exception e) {
            throw ErrorMessageCache.add(txId, e);
        }

    }

}
