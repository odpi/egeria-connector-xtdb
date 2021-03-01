/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityDetail;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceHeader;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Maps the properties of InstanceHeaders between persistence and objects.
 * (Note that this is the first level of mapping that can be instantiated, as it is the first level
 * in which a Crux ID (GUID) is mapped.)
 */
public class InstanceHeaderMapping extends InstanceAuditHeaderMapping {

    private static final Logger log = LoggerFactory.getLogger(InstanceHeaderMapping.class);

    private static final String INSTANCE_URL = ("instanceURL");

    private static final Set<String> KNOWN_PROPERTIES = createKnownProperties();
    private static Set<String> createKnownProperties() {
        Set<String> set = new HashSet<>();
        set.add(INSTANCE_URL);
        return set;
    }

    protected InstanceHeader instanceHeader;
    protected CruxDocument cruxDoc;

    /**
     * Construct a mapping from an InstanceAuditHeader (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param instanceHeader from which to map
     */
    protected InstanceHeaderMapping(CruxOMRSRepositoryConnector cruxConnector,
                                    InstanceHeader instanceHeader) {
        super(cruxConnector);
        this.instanceHeader = instanceHeader;
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxDoc from which to map
     */
    protected InstanceHeaderMapping(CruxOMRSRepositoryConnector cruxConnector,
                                    CruxDocument cruxDoc) {
        super(cruxConnector);
        this.cruxDoc = cruxDoc;
    }

    /**
     * Map from Egeria to Crux.
     * @return CruxDocument
     * @see #InstanceHeaderMapping(CruxOMRSRepositoryConnector, InstanceHeader)
     */
    public CruxDocument toCrux() {
        if (cruxDoc == null && instanceHeader != null) {
            cruxDoc = toDoc().build();
        }
        return cruxDoc;
    }

    /**
     * Translate the provided Egeria representation into a Crux document.
     * @return CruxDocument.Builder from which to build the document
     */
    protected CruxDocument.Builder toDoc() {
        CruxDocument.Builder builder = CruxDocument.builder(getGuidReference(instanceHeader));
        super.buildDoc(builder, instanceHeader);
        builder.put(INSTANCE_URL, instanceHeader.getInstanceURL());
        return builder;
    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     */
    protected void fromDoc() {
        super.fromDoc(instanceHeader, cruxDoc);
        String guid = (String) cruxDoc.getId();
        instanceHeader.setGUID(guid == null ? null : trimGuidFromReference(guid));
        for (String property : KNOWN_PROPERTIES) {
            Object objValue = cruxDoc.get(property);
            String value = objValue == null ? null : objValue.toString();
            if (INSTANCE_URL.equals(property)) {
                instanceHeader.setInstanceURL(value);
            } else {
                log.warn("Unmapped InstanceHeader property ({}): {}", property, objValue);
            }
        }
    }

    /**
     * Translate the provided InstanceHeader information into a Crux reference to the GUID of the instance.
     * @param ih to translate
     * @return String for the Crux reference
     */
    public static String getGuidReference(InstanceHeader ih) {
        TypeDefCategory type = ih.getType().getTypeDefCategory();
        if (type.equals(TypeDefCategory.ENTITY_DEF)) {
            return getGuid(EntitySummaryMapping.INSTANCE_REF_PREFIX, ih.getGUID());
        } else if (type.equals(TypeDefCategory.RELATIONSHIP_DEF)) {
            return getGuid(RelationshipMapping.INSTANCE_REF_PREFIX, ih.getGUID());
        } else {
            log.warn("Attempted to retrieve a GUID reference to a non-reference-able type -- returning null: {}", ih);
            return null;
        }
    }

    /**
     * Retrieve only the GUID portion of a Crux reference.
     * @param reference from which to trim the GUID
     * @return String of only the GUID portion of the reference
     */
    public static String trimGuidFromReference(String reference) {
        return reference.substring(reference.indexOf("_") + 1);
    }

    /**
     * Translate the provided details into a Crux reference.
     * @param instanceType of the instance (from TypeDefCategory name)
     * @param guid of the instance
     * @return String for the Crux reference
     */
    protected static String getGuid(String instanceType, String guid) {
        return instanceType + "_" + guid;
    }

}
