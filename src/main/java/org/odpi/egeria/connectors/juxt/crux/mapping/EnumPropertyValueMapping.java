/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps singular EnumPropertyValues between persistence and objects.
 *
 * These cannot simply be serialized to JSON as that would impact the ability to search their values correctly, so we
 * will serde and search based strictly on their ordinal values:
 * <code>
 *     {
 *         ...
 *         :instanceProvenanceType 1
 *         :currentStatus 15
 *         ...
 *     }
 * </code>
 */
public class EnumPropertyValueMapping extends InstancePropertyValueMapping {

    private static final Logger log = LoggerFactory.getLogger(EnumPropertyValueMapping.class);

    /**
     * Construct a mapping from PrimitivePropertyValue (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param instanceType of the instance for which the value needs to be mapped
     * @param propertyName of the property to map
     * @param value from which to map
     * @param namespace by which to qualify properties
     */
    public EnumPropertyValueMapping(CruxOMRSRepositoryConnector cruxConnector,
                                    InstanceType instanceType,
                                    String propertyName,
                                    EnumPropertyValue value,
                                    String namespace) {
        super(cruxConnector, instanceType, propertyName, value, namespace);
    }

    /**
     * Add the provided primitive value to the map.
     */
    @Override
    protected void addValueToMap() {
        if (value instanceof EnumPropertyValue) {
            EnumPropertyValue epv = (EnumPropertyValue) value;
            cruxMap.put(getPropertyValueKeyword(), getEnumPropertyValueForComparison(epv));
        } else {
            cruxMap.put(getPropertyValueKeyword(), null);
        }
    }

    /**
     * Convert the provided enumeration property value into a Crux comparable form.
     * @param epv Egeria value to translate to Crux-comparable value
     * @return Integer value that Crux can compare
     */
    public static Integer getEnumPropertyValueForComparison(EnumPropertyValue epv) {
        return epv == null ? null : epv.getOrdinal();
    }

    /**
     * Convert the provided ordinal into its InstanceProvenanceType.
     * @param ordinal to convert
     * @return InstanceProvenanceType
     */
    public static InstanceProvenanceType getInstanceProvenanceTypeFromOrdinal(Integer ordinal) {
        if (ordinal != null) {
            for (InstanceProvenanceType b : InstanceProvenanceType.values()) {
                if (b.getOrdinal() == ordinal) {
                    return b;
                }
            }
            log.warn("Non-existent InstanceProvenanceType ordinal -- returning null: {}", ordinal);
        }
        return null;
    }

    /**
     * Convert the provided InstanceProvenanceType into its symbolic name.
     * @param ipt to convert
     * @return Integer
     */
    public static Integer getOrdinalForInstanceProvenanceType(InstanceProvenanceType ipt) {
        return ipt == null ? null : ipt.getOrdinal();
    }

    /**
     * Convert the provided ordinal into its InstanceStatus.
     * @param ordinal to convert
     * @return InstanceStatus
     */
    public static InstanceStatus getInstanceStatusFromOrdinal(Integer ordinal) {
        if (ordinal != null) {
            for (InstanceStatus b : InstanceStatus.values()) {
                if (b.getOrdinal() == ordinal) {
                    return b;
                }
            }
            log.warn("Non-existent InstanceStatus ordinal -- returning null: {}", ordinal);
        }
        return null;
    }

    /**
     * Convert the provided InstanceStatus into its ordinal.
     * @param is to convert
     * @return Integer
     */
    public static Integer getOrdinalForInstanceStatus(InstanceStatus is) {
        return is == null ? null : is.getOrdinal();
    }

}
