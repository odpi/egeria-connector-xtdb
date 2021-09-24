/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.txnfn;

import clojure.lang.Keyword;
import xtdb.api.XtdbDocument;
import xtdb.api.tx.Transaction;

/**
 * Base class that all transaction functions should implement.
 */
public abstract class AbstractTransactionFunction {

    /**
     * Create the transaction function within XTDB.
     * @param tx transaction through which to create the function
     * @param name of the transaction function
     * @param fn the logic of the transaction function
     */
    protected static void createTransactionFunction(Transaction.Builder tx,
                                                    Keyword name,
                                                    String fn) {
        XtdbDocument function = XtdbDocument.createFunction(name, fn);
        tx.put(function);
    }

    // TODO: define a generic mechanism through which to record any errors (correlated to the specific
    //  transaction that ran the function), so that we can throw appropriate exceptions from the connector
    //  when it checks the committed state of the transaction itself (?)

}
