/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.mapping;

import xtdb.api.XtdbDocument;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceHeader;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;

import java.util.HashSet;
import java.util.Set;

/**
 * Maps the properties of InstanceHeaders between persistence and objects.
 * (Note that this is the first level of mapping that can be instantiated, as it is the first level
 * in which a XTDB ID (GUID) is mapped.)
 */
public class InstanceHeaderMapping extends InstanceAuditHeaderMapping {

    private static final String INSTANCE_HEADER = "InstanceHeader";

    private static final String INSTANCE_URL = "instanceURL";
    private static final String RE_IDENTIFIED_FROM_GUID = "reIdentifiedFromGUID";

    private static final Set<String> KNOWN_PROPERTIES = createKnownProperties();
    private static Set<String> createKnownProperties() {
        Set<String> set = new HashSet<>();
        set.add(INSTANCE_URL);
        set.add(RE_IDENTIFIED_FROM_GUID);
        return set;
    }

    protected InstanceHeader instanceHeader;
    protected XtdbDocument xtdbDoc;

    /**
     * Construct a mapping from an InstanceAuditHeader (to map to a XTDB representation).
     * @param xtdbConnector connectivity to XTDB
     * @param instanceHeader from which to map
     */
    protected InstanceHeaderMapping(XtdbOMRSRepositoryConnector xtdbConnector,
                                    InstanceHeader instanceHeader) {
        super(xtdbConnector);
        this.instanceHeader = instanceHeader;
    }

    /**
     * Construct a mapping from a XTDB map (to map to an Egeria representation).
     * @param xtdbConnector connectivity to XTDB
     * @param xtdbDoc from which to map
     */
    protected InstanceHeaderMapping(XtdbOMRSRepositoryConnector xtdbConnector,
                                    XtdbDocument xtdbDoc) {
        super(xtdbConnector);
        this.xtdbDoc = xtdbDoc;
    }

    /**
     * Map from Egeria to XTDB.
     * @return XtdbDocument
     * @see #InstanceHeaderMapping(XtdbOMRSRepositoryConnector, InstanceHeader)
     */
    public XtdbDocument toXTDB() {
        if (xtdbDoc == null && instanceHeader != null) {
            xtdbDoc = toDoc().build();
        }
        return xtdbDoc;
    }

    /**
     * Translate the provided Egeria representation into a XTDB document.
     * @return XtdbDocument.Builder from which to build the document
     */
    protected XtdbDocument.Builder toDoc() {
        XtdbDocument.Builder builder = XtdbDocument.builder(getGuidReference(xtdbConnector, instanceHeader));
        super.buildDoc(builder, instanceHeader);
        builder.put(INSTANCE_URL, instanceHeader.getInstanceURL());
        builder.put(RE_IDENTIFIED_FROM_GUID, instanceHeader.getReIdentifiedFromGUID());
        return builder;
    }

    /**
     * Translate the provided XTDB representation into an Egeria representation.
     */
    protected void fromDoc() {
        super.fromDoc(instanceHeader, xtdbDoc);
        final String methodName = "fromDoc";
        String guid = (String) xtdbDoc.getId();
        instanceHeader.setGUID(guid == null ? null : trimGuidFromReference(guid));
        for (String property : KNOWN_PROPERTIES) {
            Object objValue = xtdbDoc.get(property);
            String value = objValue == null ? null : objValue.toString();
            if (INSTANCE_URL.equals(property)) {
                instanceHeader.setInstanceURL(value);
            } else if (RE_IDENTIFIED_FROM_GUID.equals(property)) {
                instanceHeader.setReIdentifiedFromGUID(value);
            } else {
                xtdbConnector.logProblem(this.getClass().getName(),
                        methodName,
                        XtdbOMRSAuditCode.UNMAPPED_PROPERTY,
                        null,
                        property,
                        INSTANCE_HEADER);
            }
        }
    }

    /**
     * Translate the provided InstanceHeader information into a XTDB reference to the GUID of the instance.
     * @param xtdbConnector connectivity to the repository
     * @param ih to translate
     * @return String for the XTDB reference
     */
    public static String getGuidReference(XtdbOMRSRepositoryConnector xtdbConnector, InstanceHeader ih) {
        final String methodName = "getGuidReference";
        TypeDefCategory type = ih.getType().getTypeDefCategory();
        if (type.equals(TypeDefCategory.ENTITY_DEF)) {
            return getReference(EntitySummaryMapping.INSTANCE_REF_PREFIX, ih.getGUID());
        } else if (type.equals(TypeDefCategory.RELATIONSHIP_DEF)) {
            return getReference(RelationshipMapping.INSTANCE_REF_PREFIX, ih.getGUID());
        } else {
            xtdbConnector.logProblem(InstanceHeaderMapping.class.getName(),
                    methodName,
                    XtdbOMRSAuditCode.NON_INSTANCE_RETRIEVAL,
                    null,
                    type.name());
            return null;
        }
    }

    /**
     * Retrieve only the GUID portion of a XTDB reference.
     * @param reference from which to trim the GUID
     * @return String of only the GUID portion of the reference
     */
    public static String trimGuidFromReference(String reference) {
        return reference.substring(reference.indexOf("_") + 1);
    }

    /**
     * Translate the provided details into a XTDB reference.
     * @param instanceType of the instance (from TypeDefCategory name)
     * @param guid of the instance
     * @return String for the XTDB reference
     */
    protected static String getReference(String instanceType, String guid) {
        return instanceType + "_" + guid;
    }

}
