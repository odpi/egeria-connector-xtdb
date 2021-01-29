/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
     * @param instanceProperties from which to map
     * @param namespace by which to qualify properties
     */
    public InstancePropertiesMapping(CruxOMRSRepositoryConnector cruxConnector,
                                     InstanceProperties instanceProperties,
                                     String namespace) {
        this(cruxConnector);
        this.instanceProperties = instanceProperties;
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
        this.cruxMap = cruxMap;
        this.namespace = namespace;
    }

    /**
     * Map from Egeria to Crux.
     * @return {@code Map<Keyword, Object>}
     * @see #InstancePropertiesMapping(CruxOMRSRepositoryConnector, InstanceProperties, String)
     */
    public Map<Keyword, Object> toCrux() {

        if (cruxMap == null && instanceProperties != null) {
            cruxMap = toMap(instanceProperties, namespace);
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
            instanceProperties = fromMap(cruxMap, namespace);
            return instanceProperties;
        }

    }

    /**
     * Translate the provided Egeria representation into a Crux map.
     * @param properties Egeria representation from which to map
     * @param namespace by which to qualify the set of properties
     * @return {@code Map<Keyword, Object>} Crux representation
     */
    protected Map<Keyword, Object> toMap(InstanceProperties properties,
                                         String namespace) {
        Map<Keyword, Object> map = new HashMap<>();
        if (properties == null) {
            map.put(Keyword.intern(namespace), null);
        } else {
            Map<String, InstancePropertyValue> propertyMap = properties.getInstanceProperties();
            if (propertyMap != null) {
                for (Map.Entry<String, InstancePropertyValue> entry : propertyMap.entrySet()) {
                    String propertyName = entry.getKey();
                    InstancePropertyValue value = entry.getValue();
                    if (value != null) {
                        InstancePropertyValueMapping ipvm = InstancePropertyValueMapping.getInstancePropertyValueMappingForValue(cruxConnector, propertyName, value, namespace);
                        if (ipvm != null) {
                            Map<Keyword, Object> singlePropertyMap = ipvm.toCrux();
                            if (singlePropertyMap != null) {
                                map.putAll(singlePropertyMap);
                            }
                        }
                    }
                }
            }
        }
        return map;
    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     * @param map from which to map
     * @param namespace by which the properties are qualified
     * @return InstanceProperties that were mapped
     */
    protected InstanceProperties fromMap(Map<Keyword, Object> map, String namespace) {
        Map<String, InstancePropertyValue> ipvs = InstancePropertyValueMapping.getInstancePropertyValuesFromMap(map, namespace);
        if (!ipvs.isEmpty()) {
            InstanceProperties ip = new InstanceProperties();
            ip.setInstanceProperties(ipvs);
            return ip;
        } else {
            return null;
        }
    }

}
