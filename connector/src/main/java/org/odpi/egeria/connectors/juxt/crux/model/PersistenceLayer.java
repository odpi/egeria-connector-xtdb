/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.model;

import crux.api.CruxDocument;
import crux.api.ICruxAPI;
import crux.api.TransactionInstant;
import crux.api.tx.Transaction;

/**
 * Manages the persistence layer: for now, primarily indicating the version of the connector for
 * which data has been persisted, to validate migration starting points.
 */
public class PersistenceLayer {

    public static final long LATEST_VERSION = 2L;

    private static final String PERSISTENCE_DOC = "egeria-connector-crux/persistence-layer";
    private static final String VERSION = "egeria-connector-crux/persistence-layer.version";

    /**
     * Retrieve the version of the persistence layer for the provided Crux back-end.
     * @param cruxAPI the Crux back-end for which to retrieve the persistence layer version
     * @return the version of the persistence layer
     */
    public static long getVersion(ICruxAPI cruxAPI) {
        CruxDocument doc = getPersistenceDetails(cruxAPI);
        if (doc != null) {
            Object version = doc.get(VERSION);
            if (version instanceof Long) {
                return (Long) version;
            }
        }
        return -1;
    }

    /**
     * Set the version of the persistence layer for the provided Crux back-end.
     * CAUTION: this is used for migration purposes, so should not be set by anything other than
     * migration utilities.
     * @param cruxAPI the Crux back-end for which to set the persistence layer version
     * @param version to set
     */
    public static void setVersion(ICruxAPI cruxAPI, long version) {
        CruxDocument.Builder builder = CruxDocument.builder(PERSISTENCE_DOC);
        builder.put(VERSION, version);
        Transaction.Builder tx = Transaction.builder();
        tx.put(builder.build());
        TransactionInstant instant = cruxAPI.submitTx(tx.build());
        cruxAPI.awaitTx(instant, null);
    }

    /**
     * Check whether the persistence layer for the provided Crux back-end is the latest or not.
     * @param cruxAPI the Crux back-end for which to check the persistence layer version
     * @return true if it is at the latest version, otherwise false
     */
    public static boolean isLatestVersion(ICruxAPI cruxAPI) {
        return getVersion(cruxAPI) == LATEST_VERSION;
    }

    /**
     * Retrieve details about the persistence layer from the provided Crux back-end.
     * @param cruxAPI the Crux back-end for which to retrieve the persistence layer details
     * @return CruxDocument containing the details (or null if there are none)
     */
    protected static CruxDocument getPersistenceDetails(ICruxAPI cruxAPI) {
        return cruxAPI.db().entity(PERSISTENCE_DOC);
    }

}
