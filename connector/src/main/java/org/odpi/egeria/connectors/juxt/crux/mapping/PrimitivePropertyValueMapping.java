/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceType;
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
 *         :entityProperties/qualifiedName.json {:json "{\"class\":\"PrimitivePropertyValue\",\"instancePropertyCategory\":\"PRIMITIVE\",\"primitiveDefCategory\":\"OM_PRIMITIVE_TYPE_STRING\",\"primitiveValue\":\"A Simple Term\"}"}
 *         :entityProperties/Referenceable.qualifiedName.value "A Simple Term"
 *         ...
 *         :classifications.Confidentiality.classificationProperties/Confidentiality.level.json {:json "{\"class\":\"PrimitivePropertyValue\",\"instancePropertyCategory\":\"PRIMITIVE\",\"primitiveDefCategory\":\"OM_PRIMITIVE_TYPE_INT\",\"primitiveValue\":5}"}
 *         :classifications.Confidentiality.classificationProperties/Confidentiality.level.value 5
 *         ...
 *     }
 * </code>
 */
public class PrimitivePropertyValueMapping extends InstancePropertyValueMapping {

    /**
     * Add the provided primitive value to the Crux document.
     * @param cruxConnector connectivity to the repository
     * @param instanceType of the instance for which this value applies
     * @param builder to which to add the property value
     * @param propertyName of the property
     * @param namespace by which to qualify the property
     * @param value of the property
     */
    public static void addPrimitivePropertyValueToDoc(CruxOMRSRepositoryConnector cruxConnector,
                                                      InstanceType instanceType,
                                                      CruxDocument.Builder builder,
                                                      String propertyName,
                                                      String namespace,
                                                      PrimitivePropertyValue value) {
        builder.put(getPropertyValueKeyword(cruxConnector, instanceType, propertyName, namespace), getPrimitiveValueForComparison(value));
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
