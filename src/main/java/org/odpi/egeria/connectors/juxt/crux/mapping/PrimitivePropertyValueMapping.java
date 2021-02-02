/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.PrimitivePropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.PrimitiveDefCategory;

import java.util.Date;

/**
 * Maps singular PrimitivePropertyValues between persistence and objects.
 *
 * These cannot simply be serialized to JSON as that would impact the ability to search their values correctly, so we
 * must break apart the values and the types for each property:
 * <code>
 *     {
 *         ...
 *         :entityProperties/qualifiedName.type "{\"class\":\"PrimitivePropertyValue\",\"instancePropertyCategory\":\"PRIMITIVE\",\"primitiveDefCategory\":\"OM_PRIMITIVE_TYPE_STRING\",\"primitiveValue\":\"A Simple Term\"}"
 *         :entityProperties/qualifiedName.value "A Simple Term"
 *         ...
 *         :classifications.Confidentiality.classificationProperties/level.type "{\"class\":\"PrimitivePropertyValue\",\"instancePropertyCategory\":\"PRIMITIVE\",\"primitiveDefCategory\":\"OM_PRIMITIVE_TYPE_INT\",\"primitiveValue\":5}"
 *         :classifications.Confidentiality.classificationProperties/level.value 5
 *         ...
 *     }
 * </code>
 */
public class PrimitivePropertyValueMapping extends InstancePropertyValueMapping {

    /**
     * Construct a mapping from PrimitivePropertyValue (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param propertyName of the property to map
     * @param value from which to map
     * @param namespace by which to qualify properties
     */
    public PrimitivePropertyValueMapping(CruxOMRSRepositoryConnector cruxConnector,
                                         String propertyName,
                                         PrimitivePropertyValue value,
                                         String namespace) {
        super(cruxConnector, propertyName, value, namespace);
    }

    /**
     * Add the provided primitive value to the map.
     */
    @Override
    protected void addValueToMap() {
        if (value instanceof PrimitivePropertyValue) {
            PrimitivePropertyValue ppv = (PrimitivePropertyValue) value;
            cruxMap.put(getPropertyValueKeyword(), getPrimitiveValueForComparison(ppv));
        }
    }

    /**
     * Convert the provided primitive property value into a Crux comparable form.
     * @param ppv Egeria value to translate to Crux-comparable value
     * @return Object value that Crux can compare
     */
    public static Object getPrimitiveValueForComparison(PrimitivePropertyValue ppv) {
        PrimitiveDefCategory category = ppv.getPrimitiveDefCategory();
        Object value = null;
        switch (category) {
            case OM_PRIMITIVE_TYPE_DATE:
                // Dates are the only thing we need to translate, from their native Long form into Crux's
                // native Date form
                Object longForm = ppv.getPrimitiveValue();
                if (longForm instanceof Long) {
                    value = new Date((Long) longForm);
                }
                break;
            case OM_PRIMITIVE_TYPE_STRING:
                // Note: further translation of strings into regexes is only necessary for queries, so that will be
                // done in the CruxQuery class directly.
            case OM_PRIMITIVE_TYPE_BOOLEAN:
            case OM_PRIMITIVE_TYPE_BIGINTEGER:
            case OM_PRIMITIVE_TYPE_BIGDECIMAL:
            case OM_PRIMITIVE_TYPE_DOUBLE:
            case OM_PRIMITIVE_TYPE_SHORT:
            case OM_PRIMITIVE_TYPE_FLOAT:
            case OM_PRIMITIVE_TYPE_LONG:
            case OM_PRIMITIVE_TYPE_INT:
            case OM_PRIMITIVE_TYPE_CHAR:
            case OM_PRIMITIVE_TYPE_BYTE:
            case OM_PRIMITIVE_TYPE_UNKNOWN:
            default:
                // For everything else, we can translate directly into a straight Java object
                value = ppv.getPrimitiveValue();
                break;
        }
        return value;
    }

}
