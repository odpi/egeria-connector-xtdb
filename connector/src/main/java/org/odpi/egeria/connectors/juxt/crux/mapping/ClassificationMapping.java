/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.PersistentVector;
import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Maps the properties of Classifications between persistence and objects.
 *
 * The idea is to map Classifications into a Crux data model that flattens their structure into the EntitySummary
 * structure itself (so they are always retrieved with the EntitySummary document), using the following convention:
 * <code>
 *     {
 *         ...
 *         :classifications.Confidentiality/type :type/(GUID)
 *         :classifications.Confidentiality/instanceLicense ""
 *         :classifications.Confidentiality/createTime #inst "2021-01-26T16:35:37.504-00:00"
 *         :classifications.Confidentiality.classificationProperties/level ...
 *         ...
 *         :classifications.AnotherClassification/type :type/(GUID)
 *         :classifications.AnotherClassification/createTime #inst "2021-01-26T16:30:37.504-00:00"
 *         :classifications.AnotherClassification.classificationProperties/property ...
 *         ...
 *     }
 * </code>
 * In this way, each classification can be kept separate from other classifications, and a single classification's
 * value remains mutually-exclusive with any other values for that classification (due to the unique reference name of
 * the properties of that classification).
 */
public class ClassificationMapping extends InstanceAuditHeaderMapping {

    private static final Logger log = LoggerFactory.getLogger(ClassificationMapping.class);

    private static final String N_CLASSIFICATION_ORIGIN = "classificationOrigin";
    private static final String N_CLASSIFICATION_ORIGIN_GUID = "classificationOriginGUID";

    public static final String CLASSIFICATION_PROPERTIES_NS = "classificationProperties";
    public static final String N_LAST_CLASSIFICATION_CHANGE = "lastClassificationChange";

    public static final Set<String> KNOWN_PROPERTIES = createKnownProperties();
    private static Set<String> createKnownProperties() {
        Set<String> set = new HashSet<>();
        set.add(N_CLASSIFICATION_ORIGIN);
        set.add(N_CLASSIFICATION_ORIGIN_GUID);
        return set;
    }

    private List<Classification> classifications;
    private CruxDocument cruxDoc;
    private final String namespace;

    /**
     * Construct a mapping from a Classification (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param classifications from which to map
     * @param namespace under which to qualify the classifications
     */
    public ClassificationMapping(CruxOMRSRepositoryConnector cruxConnector,
                                 List<Classification> classifications,
                                 String namespace) {
        super(cruxConnector);
        this.classifications = classifications;
        this.namespace = namespace;
    }

    /**
     * Construct a mapping from a Crux map (to map to an Egeria representation).
     * @param cruxConnector connectivity to Crux
     * @param cruxDoc from which to map
     * @param namespace under which the classifications are qualified
     */
    public ClassificationMapping(CruxOMRSRepositoryConnector cruxConnector,
                                 CruxDocument cruxDoc,
                                 String namespace) {
        super(cruxConnector);
        this.cruxDoc = cruxDoc;
        this.namespace = namespace;
    }

    /**
     * Add the details of the mapping to the provided CruxDocument builder.
     * @param builder into which to add the classification details
     */
    public void addToCruxDoc(CruxDocument.Builder builder) {

        if (classifications != null) {
            Date latestChange = null;
            List<String> classificationNames = new ArrayList<>();
            for (Classification classification : classifications) {
                String classificationName = classification.getName();
                classificationNames.add(classificationName);
                String qualifiedNamespace = getNamespaceForClassification(classificationName);
                Date latestClassification = super.buildDoc(builder, classification, qualifiedNamespace);
                if (latestChange == null || latestChange.before(latestClassification)) {
                    latestChange = latestClassification;
                }
                builder.put(getKeyword(qualifiedNamespace, N_CLASSIFICATION_ORIGIN_GUID), classification.getClassificationOriginGUID());
                builder.put(getKeyword(qualifiedNamespace, N_CLASSIFICATION_ORIGIN), getSymbolicNameForClassificationOrigin(classification.getClassificationOrigin()));
                InstancePropertiesMapping.addToDoc(cruxConnector, builder, classification.getType(), classification.getProperties(), qualifiedNamespace + "." + CLASSIFICATION_PROPERTIES_NS);
            }
            // Add the list of classification names, for easing search
            builder.put(getKeyword(namespace), PersistentVector.create(classificationNames));
            // Add the latest change to any classification for internal tracking of validity
            builder.put(getKeyword(N_LAST_CLASSIFICATION_CHANGE), latestChange);
        }

    }

    /**
     * Map from Crux to Egeria.
     * @return {@code List<Classification>}
     * @see #ClassificationMapping(CruxOMRSRepositoryConnector, CruxDocument, String)
     */
    public List<Classification> toEgeria() {

        if (classifications != null) {
            return classifications;
        } else if (cruxDoc == null) {
            return null;
        } else {
            return fromDoc();
        }

    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     * @return {@code List<Classification>}
     */
    protected List<Classification> fromDoc() {

        List<Classification> list = new ArrayList<>();
        // Start by retrieving the list of classification names
        IPersistentVector classificationNames = (IPersistentVector) cruxDoc.get(getKeyword(namespace));

        if (classificationNames != null) {
            // Then, for each classification associated with the document...
            for (int i = 0; i < classificationNames.length(); i++) {

                String classificationName = (String) classificationNames.nth(i);
                String namespaceForClassification = getNamespaceForClassification(classificationName);

                Classification classification = new Classification();
                classification.setName(classificationName);
                super.fromDoc(classification, cruxDoc, namespaceForClassification);

                // Retrieve its embedded type details (doing this rather than going to TypeDef from repositoryHelper,
                // since these could change over history of the document)
                IPersistentMap embeddedType = (IPersistentMap) cruxDoc.get(getKeyword(namespaceForClassification, "type"));
                InstanceType classificationType = getDeserializedValue(embeddedType, mapper.getTypeFactory().constructType(InstanceType.class));

                // And use these to retrieve the property mappings for this classification (only)
                InstanceProperties ip = InstancePropertiesMapping.getFromDoc(classificationType, cruxDoc, namespaceForClassification + "." + CLASSIFICATION_PROPERTIES_NS);

                if (ip != null) {
                    classification.setProperties(ip);
                }

                list.add(classification);
            }
        }

        return list.isEmpty() ? null : list;

    }

    /**
     * Given a classification name and qualifying namespace, convert into a qualified name that can be used for the
     * classification-specific namespace.
     * @param root namespace
     * @param classificationName of the classification
     * @return String qualified namespace
     */
    public static String getNamespaceForClassification(String root, String classificationName) {
        return root + "." + classificationName;
    }

    /**
     * Given a fully-qualified classification namespace and a root, parse out the name of the classification.
     * @param root namespace
     * @param qualifiedNamespace fully-qualified classification namespace
     * @return String classification name
     */
    public static String getClassificationNameFromNamespace(String root, String qualifiedNamespace) {
        String remainder = qualifiedNamespace.substring(root.length() + 1);
        if (remainder.contains(".")) {
            int firstDot = remainder.indexOf(".");
            return remainder.substring(0, firstDot);
        } else {
            return remainder;
        }
    }

    /**
     * Given a classification name (on its own), convert it into a qualified name that can be used for the namespace.
     * @param classificationName to translate
     * @return String qualified namespace
     */
    private String getNamespaceForClassification(String classificationName) {
        return getNamespaceForClassification(namespace, classificationName);
    }

    /**
     * Convert the provided symbolic name into its ClassificationOrigin.
     * @param symbolicName to convert
     * @return ClassificationOrigin
     */
    public static ClassificationOrigin getClassificationOriginFromSymbolicName(String symbolicName) {
        for (ClassificationOrigin b : ClassificationOrigin.values()) {
            if (b.getName().equals(symbolicName)) {
                return b;
            }
        }
        log.warn("Non-existent ClassificationOrigin symbolicName -- returning null: {}", symbolicName);
        return null;
    }

    /**
     * Convert the provided ClassificationOrigin into its symbolic name.
     * @param co to convert
     * @return String
     */
    public static String getSymbolicNameForClassificationOrigin(ClassificationOrigin co) {
        return co == null ? null : co.getName();
    }

}
