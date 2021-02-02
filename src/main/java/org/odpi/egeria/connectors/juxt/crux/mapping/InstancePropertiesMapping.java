/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the properties of InstanceProperties between persistence and objects.
 *
 * The values of the properties (InstancePropertyValue) will be both JSON-serialized and searchable.
 * @see InstancePropertyValueMapping
 */
public class InstancePropertiesMapping extends AbstractMapping {

    private static final Logger log = LoggerFactory.getLogger(InstancePropertiesMapping.class);

    private String namespace;
    private InstanceType instanceType;
    private InstanceProperties instanceProperties;
    private Map<Keyword, Object> cruxMap;

    /**
     * Default constructor.
     * @param cruxConnector connectivity to Crux
     */
    protected InstancePropertiesMapping(CruxOMRSRepositoryConnector cruxConnector) {
        super(cruxConnector);
    }

    /**
     * Construct a mapping from InstanceProperties (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param instanceType of instance in which the properties exist
     * @param instanceProperties from which to map
     * @param namespace by which to qualify properties
     */
    public InstancePropertiesMapping(CruxOMRSRepositoryConnector cruxConnector,
                                     InstanceType instanceType,
                                     InstanceProperties instanceProperties,
                                     String namespace) {
        this(cruxConnector);
        this.instanceType = instanceType;
        this.instanceProperties = instanceProperties;
        this.cruxMap = null;
        this.namespace = namespace;
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxMap from which to map
     * @param namespace by which properties are qualified
     */
    public InstancePropertiesMapping(CruxOMRSRepositoryConnector cruxConnector,
                                     Map<Keyword, Object> cruxMap,
                                     String namespace) {
        this(cruxConnector);
        this.instanceProperties = null;
        this.cruxMap = cruxMap;
        this.namespace = namespace;
    }

    /**
     * Map from Egeria to Crux.
     * @return {@code Map<Keyword, Object>}
     * @see #InstancePropertiesMapping(CruxOMRSRepositoryConnector, InstanceType, InstanceProperties, String)
     */
    public Map<Keyword, Object> toCrux() {

        if (cruxMap == null && instanceProperties != null) {
            toMap();
        }
        if (cruxMap != null) {
            return cruxMap;
        } else {
            return null;
        }

    }

    /**
     * Map from Crux to Egeria.
     * @return EntityDetail
     * @see #InstancePropertiesMapping(CruxOMRSRepositoryConnector, Map, String)
     */
    public InstanceProperties toEgeria() {

        if (instanceProperties != null) {
            return instanceProperties;
        } else if (cruxMap == null) {
            return null;
        } else {
            fromMap();
            return instanceProperties;
        }

    }

    /**
     * Translate the provided Egeria representation into a Crux map.
     */
    protected void toMap() {
        cruxMap = new HashMap<>();
        if (instanceProperties == null) {
            cruxMap.put(Keyword.intern(namespace), null);
        } else {
            Map<String, InstancePropertyValue> propertyMap = instanceProperties.getInstanceProperties();
            if (propertyMap != null) {
                for (Map.Entry<String, InstancePropertyValue> entry : propertyMap.entrySet()) {
                    String propertyName = entry.getKey();
                    InstancePropertyValue value = entry.getValue();
                    if (value != null) {
                        InstancePropertyValueMapping ipvm = InstancePropertyValueMapping.getInstancePropertyValueMappingForValue(cruxConnector, instanceType, propertyName, value, namespace);
                        if (ipvm != null) {
                            Map<Keyword, Object> singlePropertyMap = ipvm.toCrux();
                            if (singlePropertyMap != null) {
                                cruxMap.putAll(singlePropertyMap);
                            }
                        }
                    }
                }
            } else {
                // Create an empty map if there is not one, for next set of checks
                propertyMap = new HashMap<>();
            }
            // explicitly set any other properties on the instance to null, so that we can still include them
            // if sorting, or running an explicit 'IS_NULL' or 'NOT_NULL' search against appropriately
            List<String> allProperties = instanceType.getValidInstanceProperties();
            if (allProperties != null) {
                for (String propertyName : allProperties) {
                    if (!propertyMap.containsKey(propertyName)) {
                        // Only explicitly set to null if the earlier processing has not already set a value on the property
                        InstancePropertyValueMapping ipvm = InstancePropertyValueMapping.getInstancePropertyValueMappingForValue(cruxConnector, instanceType, propertyName, null, namespace);
                        if (ipvm != null) {
                            Map<Keyword, Object> singlePropertyMap = ipvm.toCrux();
                            if (singlePropertyMap != null) {
                                cruxMap.putAll(singlePropertyMap);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     */
    protected void fromMap() {
        Map<String, InstancePropertyValue> ipvs = InstancePropertyValueMapping.getInstancePropertyValuesFromMap(cruxMap, namespace);
        if (!ipvs.isEmpty()) {
            instanceProperties = new InstanceProperties();
            instanceProperties.setInstanceProperties(ipvs);
        }
    }

}
