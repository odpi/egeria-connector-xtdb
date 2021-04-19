/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.model.migration;

import clojure.lang.*;
import crux.api.*;
import org.odpi.egeria.connectors.juxt.crux.mapping.Constants;
import org.odpi.egeria.connectors.juxt.crux.mapping.InstanceAuditHeaderMapping;

import java.util.*;

/**
 * Performs a persistence layer migration from the initial release of the connector to version 2.
 *
 * The specific changes that are included in version 2:
 * <ul>
 *     <li>InstanceAuditHeaderMapping no longer separates the type GUID and supertype GUIDs, but places all such information
 *          into a single vector (for improved search performance)</li>
 *     <li>RelationshipMapping no longer has separate properties for each entity proxy, but stores them as a vector: this
 *          retains their ordering, but allows relationships to be more efficiently searched from either related entity</li>
 * </ul>
 */
public class UpgradeInitialTo2 extends UpgradePersistence {

    /**
     * Default constructor.
     * @param cruxAPI the Crux back-end to upgrade
     * @param batchSize number of documents to migrate per batch.
     */
    public UpgradeInitialTo2(ICruxAPI cruxAPI, int batchSize) {
        super(cruxAPI, -1, 2, batchSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void migrateMetadata() throws Exception {

        HistoryOptions options = HistoryOptions.create(HistoryOptions.SortOrder.DESC);

        ICruxDatasource db = cruxAPI.db();

        // First search for every single metadata instance...
        try (ICursor<List<?>> searchCursor = db.openQuery(getAllInstancesQuery())) {

            while (searchCursor.hasNext()) {

                List<?> next = searchCursor.next();
                String docId = (String) next.get(0);

                // then retrieve every single version of each metadata instance...
                try (ICursor<Map<Keyword, ?>> historyCursor = db.openEntityHistory(docId, options)) {

                    while (historyCursor.hasNext()) {

                        Map<Keyword, ?> version = historyCursor.next();
                        Object oValid = version.get(Constants.CRUX_VALID_TIME);
                        Object oTxn   = version.get(Constants.CRUX_TX_TIME);
                        if (oValid instanceof Date && oTxn instanceof Date) {
                            Date valid = (Date) oValid;
                            Date txn   = (Date) oTxn;
                            // ... and finally migrate every one of these versions of every instance
                            CruxDocument doc = cruxAPI.db(valid, txn).entity(docId);
                            CruxDocument upgraded = migrateInstanceAuditHeader(doc);
                            // Check if the instance is a relationship, and only if so do the following
                            if (isRelationship(upgraded)) {
                                upgraded = migrateRelationship(upgraded);
                            }
                            // Add the migrated docs in batches
                            putToBatch(upgraded, valid, txn);
                        }

                    }

                }

            }

        }

    }

    private static final String TYPE_DEF_GUID = "type.guid";
    private static final String SUPERTYPE_DEF_GUIDS = "type.supers";
    private static final String TYPE_DEF_GUIDS = "type.guids";

    /**
     * Migrate from the split type and supertypes to a single vector of all type GUIDs.
     * @param doc Crux document to migrate
     * @return a clone of the document that has been migrated
     */
    private CruxDocument migrateInstanceAuditHeader(CruxDocument doc) {

        // Retrieve the old split type information
        Object typeDefGuid = doc.get(TYPE_DEF_GUID);
        Object superTypeDefGuids = doc.get(SUPERTYPE_DEF_GUIDS);

        // Create a singular list of the types
        List<Object> types = new ArrayList<>();
        if (typeDefGuid != null) {
            types.add(typeDefGuid);
        }
        if (superTypeDefGuids instanceof IPersistentVector) {
            IPersistentVector v = (IPersistentVector) superTypeDefGuids;
            for (int i = 0; i < v.length(); i++) {
                Object supertype = v.nth(i);
                if (supertype != null) {
                    types.add(supertype);
                }
            }
        }

        Set<String> remove = new HashSet<>();
        remove.add(TYPE_DEF_GUID);
        remove.add(SUPERTYPE_DEF_GUIDS);

        // Remove the old type split and add the new singular vector
        return doc.minusAll(remove).plus(TYPE_DEF_GUIDS, PersistentVector.create(types));

    }

    private static final String ENTITY_ONE_PROXY = "entityOneProxy";
    private static final String ENTITY_TWO_PROXY = "entityTwoProxy";
    private static final String ENTITY_PROXIES = "entityProxies";

    /**
     * Migrate from the split entity proxies to a single vector of the proxies.
     * @param doc Crux document to migrate
     * @return a clone of the document that has been migrated
     */
    private CruxDocument migrateRelationship(CruxDocument doc) {

        // Retrieve the old split proxies
        Object proxyOne = doc.get(ENTITY_ONE_PROXY);
        Object proxyTwo = doc.get(ENTITY_TWO_PROXY);

        // Create a singular vector of the proxies
        List<Object> proxies = new ArrayList<>();
        if (proxyOne != null && proxyTwo != null) {
            proxies.add(proxyOne);
            proxies.add(proxyTwo);
        }

        Set<String> remove = new HashSet<>();
        remove.add(ENTITY_ONE_PROXY);
        remove.add(ENTITY_TWO_PROXY);

        // Remove the old proxy split and add the new singular vector
        return doc.minusAll(remove).plus(ENTITY_PROXIES, PersistentVector.create(proxies));

    }

    /**
     * Construct a query to retrieve the document IDs for all instances of metadata.
     * @return the query
     */
    private IPersistentMap getAllInstancesQuery() {
        Symbol docVar = Symbol.intern("d");
        IPersistentMap query = PersistentArrayMap.EMPTY;
        IPersistentVector conditions = PersistentVector.create(
                docVar, Keyword.intern(InstanceAuditHeaderMapping.METADATA_COLLECTION_ID), Symbol.intern("_")
        );
        query = query.assoc(Keyword.intern("find"), PersistentVector.create(docVar));
        query = query.assoc(Keyword.intern("where"), PersistentVector.create(conditions));
        return query;
    }

    /**
     * Check whether the Crux document is an Egeria relationship.
     * @param doc to check
     * @return true if it refers to a relationship, otherwise false
     */
    private boolean isRelationship(CruxDocument doc) {
        return doc != null && ((String)doc.getId()).startsWith("r_");
    }

}
