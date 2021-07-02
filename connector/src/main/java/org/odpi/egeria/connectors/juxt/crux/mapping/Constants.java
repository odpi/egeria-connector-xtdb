/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;

/**
 * A set of constants to use across mappings.
 */
public class Constants {

    private Constants() {}

    // Common strings
    private static final String CRUX_TX = "crux.tx";
    private static final String CRUX_DB = "crux.db";
    public static final String NO_ACTION = "No action is required.  This is part of the normal operation of the service.";
    public static final String ENTITY_GUID = "entityGUID";
    public static final String RELATIONSHIP_GUID = "relationshipGUID";
    public static final String HOME_METADATA_COLLECTION_ID = "homeMetadataCollectionId";
    public static final String QUERY_WITH = "Querying with: {}";
    public static final String FOUND_RESULTS = "Found results: {}";
    public static final String WRITE_RESULTS = " ... results: {}";

    // Config details
    public static final Keyword CRUX_VERSION = Keyword.intern("crux.version", "version");
    public static final String EGERIA_LUCENE = "egeria.crux.lucene/lucene-store";

    // Shared properties
    public static final Keyword CRUX_PK = Keyword.intern(CRUX_DB, "id");
    public static final Keyword CRUX_TX_TIME = Keyword.intern(CRUX_TX, "tx-time");
    public static final Keyword CRUX_VALID_TIME = Keyword.intern(CRUX_DB, "valid-time");

    // Shared operations
    public static final Keyword CRUX_PUT = Keyword.intern(CRUX_TX, "put");
    public static final Keyword CRUX_EVICT = Keyword.intern(CRUX_TX, "evict");

    // Graph query limits
    public static final int MAX_TRAVERSAL_DEPTH = 40;

}
