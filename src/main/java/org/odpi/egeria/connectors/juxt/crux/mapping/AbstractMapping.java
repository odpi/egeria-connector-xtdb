/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentHashMap;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * General utilities for the mapping of any instance data.
 */
public abstract class AbstractMapping {

    private static final Logger log = LoggerFactory.getLogger(AbstractMapping.class);

    protected final CruxOMRSRepositoryConnector cruxConnector;

    protected static final ObjectMapper mapper = new ObjectMapper();
    private static final Keyword EMBEDDED_JSON = Keyword.intern("json");

    /**
     * Default constructor.
     * @param cruxConnector connectivity to Crux
     */
    protected AbstractMapping(CruxOMRSRepositoryConnector cruxConnector) {
        this.cruxConnector = cruxConnector;
    }

    /**
     * Construct a string representation of a keyword.
     * @param name of the keyword
     * @return String representation of the form ':name'
     */
    public static String getKeyword(String name) {
        return getKeyword(null, name);
    }

    /**
     * Construct a string representation of a keyword.
     * @param namespace of the keyword (optional)
     * @param name of the keyword
     * @return String representation of the form ':namespace/name'
     */
    public static String getKeyword(String namespace, String name) {
        if (name == null || name.equals("")) {
            return null;
        }
        if (namespace == null || namespace.equals("")) {
            return name;
        } else {
            return namespace + "/" + name;
        }
    }

    /**
     * Retrieve an embedded JSON-serialized form of a given object. This will prevent the object from being searchable,
     * but provides an efficient means to store structured information.
     * @param value to JSON-serialize
     * @return IPersistentMap giving the embedded serialized form
     */
    protected static IPersistentMap getEmbeddedSerializedForm(Object value) {
        IPersistentMap subMap = null;
        if (value != null) {
            try {
                // Serialize the value into JSON (via Jackson)
                String json = mapper.writeValueAsString(value);
                // Create a new map {:json "serialized-json-string"}
                Map<Keyword, String> map = new HashMap<>();
                map.put(EMBEDDED_JSON, json);
                subMap = PersistentHashMap.create(map);
            } catch (IOException e) {
                log.error("Unable to serialize object into JSON: {}", value);
            }
        }
        return subMap;
    }

    /**
     * Retrieve the deserialized value given an embedded form. This will prevent the object from being searchable,
     * but provides an efficient means to retrieve structured information.
     * @param embedded value to JSON-deserialize
     * @param type the type of value to deserialize
     * @param <T> type of value to deserialize
     * @return the deserialized value
     */
    protected static <T> T getDeserializedValue(IPersistentMap embedded, JavaType type) {
        // There must be the ":json" keyword in the map for it to be an embedded serialized form
        T deserialized = null;
        if (embedded != null && embedded.containsKey(EMBEDDED_JSON)) {
            String value = (String) embedded.valAt(EMBEDDED_JSON);
            try {
                deserialized = mapper.readValue(value, type);
            } catch (IOException e) {
                log.error("Unable to deserialize object from JSON: {}", value);
            }
        }
        return deserialized;
    }

}
