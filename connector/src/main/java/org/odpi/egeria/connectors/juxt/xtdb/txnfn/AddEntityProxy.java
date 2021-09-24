/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntityDetailMapping;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntityProxyMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityProxy;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityNotKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.InvalidParameterException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import xtdb.api.TransactionInstant;
import xtdb.api.XtdbDocument;
import xtdb.api.tx.Transaction;

/**
 * Transaction function for adding an EntityProxy.
 */
public class AddEntityProxy extends AbstractTransactionFunction {

    public static final Keyword FUNCTION_NAME = Keyword.intern("egeria", "addEntityProxy");
    private static final String METHOD_NAME = FUNCTION_NAME.toString();
    // Only create the proxy if some other entity with this GUID (proxy or otherwise) does not yet
    // exist
    private static final String FN = "" +
            "(fn [ctx eid proxy] " +
            "    (let [db (xtdb.api/db ctx)" +
            "          existing (xtdb.api/entity db eid)]" +
            "         (when-not (some? existing)" +
            "          [[:xtdb.api/put proxy]])))";

    /**
     * Default constructor.
     */
    private AddEntityProxy() {
        // Nothing to do here, logic is entirely handled through the Clojure
    }

    /**
     * Create the provided entity instance in the XTDB repository by pushing down the transaction.
     * @param xtdb connectivity
     * @param entity to create
     * @throws RepositoryErrorException on any error
     */
    public static void transact(XtdbOMRSRepositoryConnector xtdb,
                                EntityProxy entity) throws RepositoryErrorException {
        String docId = EntityDetailMapping.getReference(entity.getGUID());
        EntityProxyMapping edm = new EntityProxyMapping(xtdb, entity);
        XtdbDocument proxyDoc = edm.toXTDB();
        Transaction.Builder tx = Transaction.builder();
        tx.invokeFunction(FUNCTION_NAME, docId, proxyDoc.toMap());
        TransactionInstant results = xtdb.runTx(tx.build());
        try {
            xtdb.validateCommit(results, METHOD_NAME);
        } catch (RepositoryErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                    DeleteEntity.class.getName(),
                    METHOD_NAME,
                    e);
        }
    }

    /**
     * Create the transaction function within XTDB.
     * @param tx transaction through which to create the function
     */
    public static void create(Transaction.Builder tx) {
        createTransactionFunction(tx, FUNCTION_NAME, FN);
    }

}
