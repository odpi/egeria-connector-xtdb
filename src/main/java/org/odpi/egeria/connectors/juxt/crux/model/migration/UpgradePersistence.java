/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.model.migration;

import crux.api.CruxDocument;
import crux.api.ICruxAPI;
import crux.api.tx.Transaction;
import org.odpi.egeria.connectors.juxt.crux.model.PersistenceLayer;

import java.util.Date;

/**
 * Base class that should be extended to define how the persistence layer should be upgraded
 * from one version to another.
 */
public abstract class UpgradePersistence {

    protected final ICruxAPI cruxAPI;

    private final long fromVersion;
    private final long toVersion;
    private final int batchSize;

    private Transaction.Builder tx;
    private int txSize;

    /**
     * Defines the versions that the upgrade will apply.
     * @param cruxAPI the Crux back-end to upgrade
     * @param from the version that must be in place to start the upgrade
     * @param to the version that will be in place once the upgrade is completed
     * @param batchSize the number of documents to migrate, per batch
     */
    protected UpgradePersistence(ICruxAPI cruxAPI, long from, long to, int batchSize) {
        this.cruxAPI = cruxAPI;
        fromVersion = from;
        toVersion = to;
        this.batchSize = batchSize;
        setNewTransactionQueue();
    }

    /**
     * Returns the version that must be in place to start the upgrade.
     * @return version
     */
    public long getFromVersion() {
        return fromVersion;
    }

    /**
     * Returns the version that will be in place once the upgrade is completed.
     * @return version
     */
    public long getToVersion() {
        return toVersion;
    }

    /**
     * Returns the number of documents that should be migrated, per batch.
     * @return batch size
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Execute the in-place migration.
     * @throws Exception on any error during the migration
     */
    public final void migrate() throws Exception {
        migrateMetadata();
        markUpdatedVersion();
    }

    /**
     * Execute the in-place migration of metadata.
     * @throws Exception on any error during the migration
     */
    public abstract void migrateMetadata() throws Exception;

    /**
     * Add the provided document into the batch as a 'put' operation. This will automatically submit the batch once it
     * reaches the batch size, followed by rolling a new transaction queue.
     * @param doc the Crux document to put
     * @param valid the valid from date
     * @param txn the transaction date
     */
    protected void putToBatch(CruxDocument doc, Date valid, Date txn) {
        if (doc != null) {
            tx.put(doc, valid, txn);
            txSize++;
            if (txSize == batchSize) {
                submitBatch();
                setNewTransactionQueue();
            }
        }
    }

    /**
     * Submit the current queue of transactions.
     */
    private void submitBatch() {
        cruxAPI.submitTx(tx.build());
    }

    /**
     * Create a new queue of transactions
     */
    private void setNewTransactionQueue() {
        tx = Transaction.builder();
        txSize = 0;
    }

    /**
     * Persist the updated version of the layer (call at the end of migrate()).
     * @see #migrate()
     */
    private void markUpdatedVersion() {
        PersistenceLayer.setVersion(cruxAPI, toVersion);
    }

}
