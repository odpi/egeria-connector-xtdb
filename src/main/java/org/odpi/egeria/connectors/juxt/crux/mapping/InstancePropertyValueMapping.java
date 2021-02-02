/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import com.fasterxml.jackson.core.type.TypeReference;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.PrimitivePropertyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps singular InstancePropertyValues between persistence and objects.
 *
 * These cannot simply be serialized to JSON as that would impact the ability to search their values correctly, so we
 * must break apart the values and the types for each property. In general, we will store the complete value into the
 * '.json' portion, but we will also store just the value alone (without any type details) into the '.value' portion.
 *
 * This will allow us to quickly pull back the complete value from a JSON-serialized form (from '.json') while also
 * providing a reliable search point at the '.value'. This class and its subclasses must be responsible for ensuring
 * that these two properties are kept aligned with each other at all times.
 *
 * See the subclasses of this class, which handle mappings for the various subtypes of InstancePropertyValue for
 * details of each '.value' representation.
 */
public class InstancePropertyValueMapping extends AbstractMapping {

    private static final Logger log = LoggerFactory.getLogger(InstancePropertyValueMapping.class);

    protected String namespace;
    protected String propertyName;
    protected InstancePropertyValue value = null;
    protected Map<Keyword, Object> cruxMap = null;

    /**
     * Default constructor.
     * @param cruxConnector connectivity to Crux
     */
    private InstancePropertyValueMapping(CruxOMRSRepositoryConnector cruxConnector) {
        super(cruxConnector);
    }

    /**
     * Construct a mapping from InstancePropertyValue (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param propertyName of the property to map
     * @param value from which to map
     * @param namespace by which to qualify properties
     */
    protected InstancePropertyValueMapping(CruxOMRSRepositoryConnector cruxConnector,
                                           String propertyName,
                                           InstancePropertyValue value,
                                           String namespace) {
        this(cruxConnector);
        this.propertyName = propertyName;
        this.value = value;
        this.namespace = namespace;
    }

    /**
     * Construct a mapping from InstancePropertyValue (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param propertyName of the property to map
     * @param value from which to map
     * @param namespace by which to qualify properties
     * @return InstancePropertyValueMapping appropriate to the type of the value
     */
    public static InstancePropertyValueMapping getInstancePropertyValueMappingForValue(CruxOMRSRepositoryConnector cruxConnector,
                                                                                       String propertyName,
                                                                                       InstancePropertyValue value,
                                                                                       String namespace) {
        InstancePropertyValueMapping ipvm = null;
        if (value != null) {
            InstancePropertyCategory category = value.getInstancePropertyCategory();
            switch (category) {
                case PRIMITIVE:
                    ipvm = new PrimitivePropertyValueMapping(cruxConnector, propertyName, (PrimitivePropertyValue) value, namespace);
                    break;
                case MAP:
                case ENUM:
                case ARRAY:
                case STRUCT:
                case UNKNOWN:
                default:
                    ipvm = new InstancePropertyValueMapping(cruxConnector, propertyName, value, namespace);
                    break;
            }
        }
        return ipvm;
    }

    /**
     * Construct a mapping of the InstancePropertyValues (from a Crux representation), as a singular pass through the
     * map (rather than inefficiently trying to do so property-by-property).
     * @param cruxMap from which to map
     * @param namespace by which the properties are qualified
     * @return {@code Map<String, InstancePropertyValue>} keyed by Egeria property name
     */
    public static Map<String, InstancePropertyValue> getInstancePropertyValuesFromMap(Map<Keyword, Object> cruxMap,
                                                                                      String namespace) {
        Map<String, InstancePropertyValue> ipvs = new HashMap<>();
        for (Map.Entry<Keyword, Object> entry : cruxMap.entrySet()) {
            Keyword property = entry.getKey();
            String detectedNamespace = property.getNamespace();
            if (detectedNamespace != null && detectedNamespace.equals(namespace)) {
                String propertyName = property.getName();
                if (propertyName.endsWith(".json")) {
                    // We'll pull values from the '.json'-qualified portion, given this is a complete JSON serialization
                    // TODO: this will also need to change if we change the serialization
                    Object objValue = entry.getValue();
                    String value = objValue == null ? null : objValue.toString();
                    InstancePropertyValueMapping.addInstancePropertyValueToMap(ipvs, propertyName, value);
                }
            }
        }
        return ipvs;
    }

    /**
     * Map from Egeria to Crux.
     * @return {@code Map<Keyword, Object>}
     * @see #InstancePropertyValueMapping(CruxOMRSRepositoryConnector, String, InstancePropertyValue, String)
     */
    public Map<Keyword, Object> toCrux() {
        if (cruxMap == null && value != null) {
            toMap();
        }
        if (cruxMap != null) {
            return cruxMap;
        } else {
            return null;
        }
    }

    /**
     * Translate the provided Egeria representation into the Crux map.
     */
    protected void toMap() {
        if (value != null) {
            cruxMap = new HashMap<>();
            addFullJSONToMap();
            addValueToMap();
        }
    }

    /**
     * Add the specified property value as a fully-serialized JSON representation to the map (for expedient retrieval
     * later back into an Egeria object).
     */
    protected void addFullJSONToMap() {
        try {
            cruxMap.put(getPropertyTypeKeyword(), mapper.writeValueAsString(value));
        } catch (IOException e) {
            log.error("Unable to translate property details into JSON: {}", value, e);
        }
    }

    /**
     * Add the specified value to the map. (Note that this method will generally be overridden by the subclasses to
     * define how the subclass-specific values are translated appropriately.)
     */
    protected void addValueToMap() {
        try {
            cruxMap.put(getPropertyValueKeyword(), mapper.writeValueAsString(value));
        } catch (IOException e) {
            log.error("Unable to translate property value into JSON: {}", value, e);
        }
    }

    /**
     * Retrieve the qualified Crux name for the type of the property.
     * @return Keyword
     */
    protected Keyword getPropertyTypeKeyword() {
        return Keyword.intern(namespace, propertyName + ".json");
    }

    /**
     * Retrieve the qualified Crux name for the value of the property.
     * @return Keyword
     */
    protected Keyword getPropertyValueKeyword() {
        return Keyword.intern(namespace, propertyName + ".value");
    }

    /**
     * Add the specified value for the specified property name to the provided map of values.
     * @param ipvs map of values
     * @param propertyName to add
     * @param jsonValue to add
     */
    public static void addInstancePropertyValueToMap(Map<String, InstancePropertyValue> ipvs, String propertyName, String jsonValue) {
        // Only process the propertyName if it ends with the '.json' qualification
        // (This logic is intentionally in this method as it is called from elsewhere, like ClassificationMapping)
        if (propertyName.endsWith(".json")) {
            // Need to remove the '.json' qualifier before mapping to the Egeria property name
            String actualPropertyName = propertyName.substring(0, propertyName.length() - 5);
            InstancePropertyValue ipv = getInstancePropertyValue(jsonValue);
            ipvs.put(actualPropertyName, ipv);
        }
    }

    /**
     * Translate the provided JSON representation of a value into an Egeria object.
     * @param jsonValue to translate
     * @return InstancePropertyValue
     */
    private static InstancePropertyValue getInstancePropertyValue(String jsonValue) {
        if (jsonValue != null) {
            try {
                return mapper.readValue(jsonValue, new TypeReference<InstancePropertyValue>() {});
            } catch (IOException e) {
                log.error("Unable to translate value from JSON: {}", jsonValue, e);
            }
        }
        return null;
    }

}
