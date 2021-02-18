/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;

import java.util.Date;
import java.util.Map;

/**
 * A set of constants to use across mappings.
 */
public class Constants {

    // Config details
    public static final Keyword CRUX_VERSION = Keyword.intern("crux.version", "version");
    public static final String CRUX_LUCENE = "crux.lucene/lucene-store";

    // Shared properties
    public static final Keyword CRUX_PK = Keyword.intern("crux.db", "id");
    public static final Keyword CRUX_TX_TIME = Keyword.intern("crux.tx", "tx-time");
    public static final Keyword CRUX_VALID_TIME = Keyword.intern("crux.db", "valid-time");

    // Shared operations
    public static final Keyword CRUX_PUT = Keyword.intern("crux.tx", "put");
    public static final Keyword CRUX_EVICT = Keyword.intern("crux.tx", "evict");

    // Graph query limits
    public static final int MAX_TRAVERSAL_DEPTH = 40;

    // Cascading limits
    public static final int CASCADE_DELETES_PAGE_SIZE = 10000;

    /**
     * Retrieve an insertion vector for Crux (applicable for any write operation, since it is append-only).
     * @param properties map to include in the insertion
     * @return PersistentVector containing the Crux instruction, detailed properties, and precise validity time
     */
    public static PersistentVector put(Map<Keyword, Object> properties) {
        IPersistentMap map = PersistentHashMap.create(properties);
        // Explicitly set the transaction time to match what is in the instance itself,
        // preferring update time if it is available and otherwise falling back to create time
        // (and if that is not available, falling back to allowing Crux to manage it itself)
        Date txnTime;
        Object timeFromMap = properties.getOrDefault(InstanceAuditHeaderMapping.UPDATE_TIME, null);
        if (timeFromMap instanceof Date) {
            txnTime = ((Date) timeFromMap);
        } else {
            timeFromMap = properties.getOrDefault(InstanceAuditHeaderMapping.CREATE_TIME, null);
            if (timeFromMap instanceof Date) {
                txnTime = ((Date) timeFromMap);
            } else {
                txnTime = new Date();
            }
        }
        return PersistentVector.create(Constants.CRUX_PUT, map, txnTime);
    }

    /**
     * Retrieve an eviction vector for Crux (applicable for permanently removing anything by its "primary key").
     * @param docRef giving the "primary key" of the record to be permanently removed
     * @return PersistentVector containing the Crux instruction
     */
    public static PersistentVector evict(Keyword docRef) {
        return PersistentVector.create(Constants.CRUX_EVICT, docRef);
    }

    /**
     * Retrieve only the GUID portion of a Crux reference.
     * @param reference from which to trim the GUID
     * @return String of only the GUID portion of the reference
     */
    public static String trimGuidFromReference(String reference) {
        return reference.substring(reference.indexOf("/") + 1);
    }

}
