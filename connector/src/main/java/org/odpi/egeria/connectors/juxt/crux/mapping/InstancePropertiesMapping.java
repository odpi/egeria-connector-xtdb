/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Maps the properties of InstanceProperties between persistence and objects.
 *
 * The values of the properties (InstancePropertyValue) will be both JSON-serialized and searchable.
 * @see InstancePropertyValueMapping
 */
public class InstancePropertiesMapping {

    private InstancePropertiesMapping() {}

    /**
     * Retrieve the instance property values from the provided Crux document.
     * NOTE: whether empty or null, the instance properties will always be returned back from the Crux representation
     * as an empty set of properties (not null).
     * @param type of the Egeria instance from which we are retrieving the values
     * @param cruxDoc from which to retrieve the values
     * @param namespace by which the properties to retrieve are qualified
     * @return InstanceProperties
     */
    public static InstanceProperties getFromDoc(InstanceType type,
                                                CruxDocument cruxDoc,
                                                String namespace) {

        List<String> validProperties = type.getValidInstanceProperties();

        // Iterate through each of the properties this instance could contain and add them to the map of values
        if (validProperties != null && !validProperties.isEmpty()) {
            Map<String, InstancePropertyValue> values = new TreeMap<>();
            for (String propertyName : validProperties) {
                InstancePropertyValue value = InstancePropertyValueMapping.getInstancePropertyValueFromDoc(cruxDoc, namespace, propertyName);
                if (value != null) {
                    values.put(propertyName, value);
                }
            }
            InstanceProperties ip = new InstanceProperties();
            ip.setInstanceProperties(values);
            return ip;
        }

        return null;

    }

    /**
     * Add the provided instance property values to the Crux document.
     * @param cruxConnector connectivity to the repository
     * @param builder to which to add the properties
     * @param type of the Egeria instance to which the values are being added
     * @param properties to add
     * @param namespace by which the properties should be qualified
     */
    public static void addToDoc(CruxOMRSRepositoryConnector cruxConnector,
                                CruxDocument.Builder builder,
                                InstanceType type,
                                InstanceProperties properties,
                                String namespace) {

        Map<String, InstancePropertyValue> propertyMap;
        if (properties != null) {
            propertyMap = properties.getInstanceProperties();
            if (propertyMap != null) {
                for (Map.Entry<String, InstancePropertyValue> entry : propertyMap.entrySet()) {
                    InstancePropertyValueMapping.addInstancePropertyValueToDoc(cruxConnector, type, builder, entry.getKey(), namespace, entry.getValue());
                }
            } else {
                propertyMap = new HashMap<>(); // Create an empty map if there is not one, for next set of checks
            }
        } else {
            propertyMap = new HashMap<>();     // Create an empty map if there is not one, for next set of checks
        }

        // explicitly set any other properties on the instance to null, so that we can still include them
        // if sorting, or running an explicit 'IS_NULL' or 'NOT_NULL' search against appropriately
        List<String> allProperties = type.getValidInstanceProperties();
        if (allProperties != null) {
            for (String propertyName : allProperties) {
                if (!propertyMap.containsKey(propertyName)) {
                    // Only explicitly set to null if the earlier processing has not already set a value on the property
                    InstancePropertyValueMapping.addInstancePropertyValueToDoc(cruxConnector, type, builder, propertyName, namespace, null);
                }
            }
        }

    }

}
