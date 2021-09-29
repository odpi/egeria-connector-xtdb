/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.migration.model;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.*;
import xtdb.api.*;

import java.util.*;

/**
 * Performs a persistence layer migration from version 2 of the connector to version 3.
 *
 * The specific changes that are included in version 3:
 * <ul>
 *     <li>EntityProxy documents now use the same property namespace as any other entity ("entityProperties") rather
 *          than a distinct namespace called "uniqueProperties." This simplifies mapping and should make "upgrading"
 *          a proxy to a full entity simpler as well.</li>
 * </ul>
 */
public class Upgrade2To3 extends UpgradePersistence {

    /**
     * Default constructor.
     * @param xtdbAPI the XTDB back-end to upgrade
     * @param batchSize number of documents to migrate per batch.
     */
    public Upgrade2To3(IXtdb xtdbAPI, int batchSize) {
        super(xtdbAPI, 2, 3, batchSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void migrateMetadata() throws Exception {

        HistoryOptions options = HistoryOptions.create(HistoryOptions.SortOrder.DESC);

        IXtdbDatasource db = xtdbAPI.db();

        // First search for every single entity proxy instance...
        try (ICursor<List<?>> searchCursor = db.openQuery(getAllEntityProxiesQuery())) {

            while (searchCursor.hasNext()) {

                List<?> next = searchCursor.next();
                String docId = (String) next.get(0);

                // then retrieve every single version of each of those entity proxies...
                try (ICursor<Map<Keyword, ?>> historyCursor = db.openEntityHistory(docId, options)) {

                    while (historyCursor.hasNext()) {

                        Map<Keyword, ?> version = historyCursor.next();
                        Object oValid = version.get(Constants.XTDB_VALID_TIME);
                        Object oTxn   = version.get(Constants.XTDB_TX_TIME);
                        if (oValid instanceof Date && oTxn instanceof Date) {
                            Date valid = (Date) oValid;
                            Date txn   = (Date) oTxn;
                            // ... and finally migrate every one of these versions of every instance
                            XtdbDocument doc = xtdbAPI.db(valid, txn).entity(docId);
                            XtdbDocument upgraded = migrateEntityProxy(doc);
                            // Add the migrated docs in batches
                            putToBatch(upgraded, valid, null);
                        }

                    }

                }

            }

        }

    }

    static final String OLD_NS = "uniqueProperties";
    static final String NEW_NS = EntityDetailMapping.ENTITY_PROPERTIES_NS;

    /**
     * Migrate from the entity proxies' property namespaces.
     * @param doc XTDB document to migrate
     * @return a clone of the document that has been migrated
     */
    @SuppressWarnings("unchecked")
    private XtdbDocument migrateEntityProxy(XtdbDocument doc) {

        IPersistentMap map = doc.toMap();
        Iterator<MapEntry> properties = (Iterator<MapEntry>) map.iterator();
        while (properties.hasNext()) {
            MapEntry entry = properties.next();
            Object oldKey = entry.getKey();
            String oldKeyName = oldKey.toString().substring(1); // remove the ':' from the keyword
            if (oldKeyName.startsWith(OLD_NS)) {
                String newKeyName = oldKeyName.replace(OLD_NS, NEW_NS);
                doc = doc.minus(oldKeyName).plus(newKeyName, entry.getValue());
            }
        }

        return doc;

    }

    /**
     * Construct a query to retrieve the document IDs for all instances of entity proxies.
     * @return the query
     */
    private IPersistentMap getAllEntityProxiesQuery() {
        Symbol docVar = Symbol.intern("ep");
        IPersistentMap query = PersistentArrayMap.EMPTY;
        IPersistentVector conditions = PersistentVector.create(
                docVar, Keyword.intern(EntityProxyMapping.ENTITY_PROXY_ONLY_MARKER), true
        );
        query = query.assoc(Keyword.intern("find"), PersistentVector.create(docVar));
        query = query.assoc(Keyword.intern("where"), PersistentVector.create(conditions));
        return query;
    }

}
