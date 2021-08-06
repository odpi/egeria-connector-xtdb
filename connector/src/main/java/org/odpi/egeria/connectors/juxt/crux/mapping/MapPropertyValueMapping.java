/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

import java.util.*;

/**
 * Maps singular MapPropertyValues between persistence and objects.
 *
 * These cannot simply be serialized to JSON as that would impact the ability to search their values correctly, so we
 * must break apart the values and the types for each property:
 * <code>
 *     {
 *         ...
 *         :entityProperties/someProperty.json {:json "{\"class\":\"MapPropertyValue\",\"instancePropertyCategory\":\"MAP\",\"mapValues\":{\"class\":\"InstanceProperties\",\"instanceProperties\":{\"key\":\"{\"class\":\"PrimitivePropertyValue\",\"instancePropertyCategory\":\"PRIMITIVE\",\"primitiveDefCategory\":\"OM_PRIMITIVE_TYPE_STRING\",\"primitiveValue\":\"A Simple Term\"}\"}}}"}
 *         :entityProperties/someProperty.value {"key": "A Simple Term"}
 *         ...
 *     }
 * </code>
 */
public class MapPropertyValueMapping extends InstancePropertyValueMapping {

    /**
     * Add the provided map value to the Crux document.
     * @param cruxConnector connectivity to the repository
     * @param instanceType of the instance for which this value applies
     * @param builder to which to add the property value
     * @param propertyName of the property
     * @param namespace by which to qualify the property
     * @param value of the property
     */
    public static void addMapPropertyValueToDoc(CruxOMRSRepositoryConnector cruxConnector,
                                                InstanceType instanceType,
                                                CruxDocument.Builder builder,
                                                String propertyName,
                                                String namespace,
                                                MapPropertyValue value) {
        builder.put(getPropertyValueKeyword(cruxConnector, instanceType, propertyName, namespace), getMapPropertyValueForComparison(cruxConnector, value));
    }

    /**
     * Convert the provided map property value into a Crux comparable form.
     * @param cruxConnector connectivity to the repository
     * @param mpv Egeria value to translate to Crux-comparable value
     * @return {@code Map<String, Object>} value that Crux can compare
     */
    public static Map<String, Object> getMapPropertyValueForComparison(CruxOMRSRepositoryConnector cruxConnector, MapPropertyValue mpv) {
        InstanceProperties values = mpv.getMapValues();
        if (values != null && values.getInstanceProperties() != null) {
            // Create a new TreeMap of the values to ensure they are sorted by key (for consistency)
            Map<String, Object> results = new TreeMap<>();
            for (Map.Entry<String, InstancePropertyValue> entry : values.getInstanceProperties().entrySet()) {
                String key = entry.getKey();
                InstancePropertyValue value = entry.getValue();
                Object toCompare = getValueForComparison(cruxConnector, value);
                if (toCompare != null) {
                    results.put(key, toCompare);
                }
            }
            if (!results.isEmpty()) {
                return results;
            }
        }
        return null;
    }

}
