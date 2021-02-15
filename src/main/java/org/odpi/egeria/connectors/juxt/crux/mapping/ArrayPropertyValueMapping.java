/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

import java.util.*;

/**
 * Maps singular ArrayPropertyValues between persistence and objects.
 *
 * These cannot simply be serialized to JSON as that would impact the ability to search their values correctly, so we
 * must break apart the values and the types for each property:
 * <code>
 *     {
 *         ...
 *         :entityProperties/someProperty.json {:json "{\"class\":\"ArrayPropertyValue\",\"instancePropertyCategory\":\"ARRAY\",\"arrayValues\":{\"class\":\"InstanceProperties\",\"instanceProperties\":{\"0\":\"{\"class\":\"PrimitivePropertyValue\",\"instancePropertyCategory\":\"PRIMITIVE\",\"primitiveDefCategory\":\"OM_PRIMITIVE_TYPE_STRING\",\"primitiveValue\":\"A Simple Term\"}\"}}}"}
 *         :entityProperties/someProperty.value ["A Simple Term"]
 *         ...
 *     }
 * </code>
 */
public class ArrayPropertyValueMapping extends InstancePropertyValueMapping {

    /**
     * Construct a mapping from ArrayPropertyValue (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param instanceType of the instance for which the value needs to be mapped
     * @param propertyName of the property to map
     * @param value from which to map
     * @param namespace by which to qualify properties
     */
    public ArrayPropertyValueMapping(CruxOMRSRepositoryConnector cruxConnector,
                                     InstanceType instanceType,
                                     String propertyName,
                                     ArrayPropertyValue value,
                                     String namespace) {
        super(cruxConnector, instanceType, propertyName, value, namespace);
    }

    /**
     * Add the provided primitive value to the map.
     */
    @Override
    protected void addValueToMap() {
        if (value instanceof ArrayPropertyValue) {
            ArrayPropertyValue apv = (ArrayPropertyValue) value;
            cruxMap.put(getPropertyValueKeyword(), getArrayPropertyValueForComparison(apv));
        } else {
            cruxMap.put(getPropertyValueKeyword(), null);
        }
    }

    /**
     * Convert the provided primitive property value into a Crux comparable form.
     * @param apv Egeria value to translate to Crux-comparable value
     * @return {@code List<Object>} value that Crux can compare
     */
    public static List<Object> getArrayPropertyValueForComparison(ArrayPropertyValue apv) {
        InstanceProperties values = apv.getArrayValues();
        if (values != null) {
            // Create a new TreeMap of the values to ensure they are sorted by numeric key
            // TODO: will this break with more than 10 elements? (ie. "10" as a string will be sorted before "2"?)
            TreeMap<String, InstancePropertyValue> valueMap = new TreeMap<>(values.getInstanceProperties());
            List<Object> results = new ArrayList<>();
            for (InstancePropertyValue value : valueMap.values()) {
                Object toCompare = getValueForComparison(value);
                if (toCompare != null) {
                    results.add(toCompare);
                }
            }
            if (!results.isEmpty()) {
                return results;
            }
        }
        return null;
    }

}
