/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.mapping;

import xtdb.api.XtdbDocument;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

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

    /**
     * Add the provided enum value to the XTDB document.
     * @param xtdbConnector connectivity to the repository
     * @param instanceType of the instance for which this value applies
     * @param builder to which to add the property value
     * @param propertyName of the property
     * @param namespace by which to qualify the property
     * @param value of the property
     */
    public static void addEnumPropertyValueToDoc(XtdbOMRSRepositoryConnector xtdbConnector,
                                                 InstanceType instanceType,
                                                 XtdbDocument.Builder builder,
                                                 String propertyName,
                                                 String namespace,
                                                 EnumPropertyValue value) {
        builder.put(getPropertyValueKeyword(xtdbConnector, instanceType, propertyName, namespace), getEnumPropertyValueForComparison(value));
    }

    /**
     * Convert the provided enumeration property value into a XTDB comparable form.
     * @param epv Egeria value to translate to XTDB-comparable value
     * @return Integer value that XTDB can compare
     */
    public static Integer getEnumPropertyValueForComparison(EnumPropertyValue epv) {
        return epv == null ? null : epv.getOrdinal();
    }

    /**
     * Convert the provided ordinal into its InstanceProvenanceType.
     * @param xtdbConnector connectivity to the repository
     * @param ordinal to convert
     * @return InstanceProvenanceType
     */
    public static InstanceProvenanceType getInstanceProvenanceTypeFromOrdinal(XtdbOMRSRepositoryConnector xtdbConnector, Integer ordinal) {
        final String methodName = "getInstanceProvenanceTypeFromOrdinal";
        if (ordinal != null) {
            for (InstanceProvenanceType b : InstanceProvenanceType.values()) {
                if (b.getOrdinal() == ordinal) {
                    return b;
                }
            }
            xtdbConnector.logProblem(EnumPropertyValueMapping.class.getName(),
                    methodName,
                    XtdbOMRSAuditCode.NON_EXISTENT_ENUM,
                    null,
                    "InstanceProvenanceType",
                    ordinal.toString());
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
     * @param xtdbConnector connectivity to the repository
     * @param ordinal to convert
     * @return InstanceStatus
     */
    public static InstanceStatus getInstanceStatusFromOrdinal(XtdbOMRSRepositoryConnector xtdbConnector, Integer ordinal) {
        final String methodName = "getInstanceStatusFromOrdinal";
        if (ordinal != null) {
            for (InstanceStatus b : InstanceStatus.values()) {
                if (b.getOrdinal() == ordinal) {
                    return b;
                }
            }
            xtdbConnector.logProblem(EnumPropertyValueMapping.class.getName(),
                    methodName,
                    XtdbOMRSAuditCode.NON_EXISTENT_ENUM,
                    null,
                    "InstanceStatus",
                    ordinal.toString());
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
