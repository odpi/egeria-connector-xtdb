/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;

/**
 * A set of constants to use across mappings.
 */
public class Constants {

    // Config details
    public static final Keyword CRUX_VERSION = Keyword.intern("crux.version", "version");
    public static final String EGERIA_LUCENE = "egeria.crux.lucene/lucene-store";

    // Shared properties
    public static final Keyword CRUX_PK = Keyword.intern("crux.db", "id");
    public static final Keyword CRUX_TX_TIME = Keyword.intern("crux.tx", "tx-time");
    public static final Keyword CRUX_VALID_TIME = Keyword.intern("crux.db", "valid-time");

    // Shared operations
    public static final Keyword CRUX_PUT = Keyword.intern("crux.tx", "put");
    public static final Keyword CRUX_EVICT = Keyword.intern("crux.tx", "evict");

    // Graph query limits
    public static final int MAX_TRAVERSAL_DEPTH = 40;

}
