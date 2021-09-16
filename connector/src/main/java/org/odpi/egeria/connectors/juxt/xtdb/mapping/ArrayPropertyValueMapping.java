/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.mapping;

import xtdb.api.XtdbDocument;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
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
     * Add the provided array value to the XTDB document.
     * @param xtdbConnector connectivity to the repository
     * @param instanceType of the instance for which this value applies
     * @param builder to which to add the property value
     * @param propertyName of the property
     * @param namespace by which to qualify the property
     * @param value of the property
     */
    public static void addArrayPropertyValueToDoc(XtdbOMRSRepositoryConnector xtdbConnector,
                                                  InstanceType instanceType,
                                                  XtdbDocument.Builder builder,
                                                  String propertyName,
                                                  String namespace,
                                                  ArrayPropertyValue value) {
        builder.put(getPropertyValueKeyword(xtdbConnector, instanceType, propertyName, namespace), getArrayPropertyValueForComparison(xtdbConnector, value));
    }

    /**
     * Convert the provided array property value into a XTDB comparable form.
     * @param xtdbConnector connectivity to the repository
     * @param apv Egeria value to translate to XTDB-comparable value
     * @return {@code List<Object>} value that XTDB can compare
     */
    public static List<Object> getArrayPropertyValueForComparison(XtdbOMRSRepositoryConnector xtdbConnector, ArrayPropertyValue apv) {
        InstanceProperties values = apv.getArrayValues();
        if (values != null) {
            List<Object> results = new ArrayList<>();
            int total = apv.getArrayCount();
            for (int i = 0; i < total; i++) {
                InstancePropertyValue value = values.getPropertyValue("" + i);
                Object toCompare = getValueForComparison(xtdbConnector, value);
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
