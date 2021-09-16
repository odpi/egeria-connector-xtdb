/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.mapping;

import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.PersistentVector;
import xtdb.api.XtdbDocument;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

import java.util.*;

/**
 * Maps the properties of Classifications between persistence and objects.
 *
 * The idea is to map Classifications into a XTDB data model that flattens their structure into the EntitySummary
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

    private static final String CLASSIFICATION = "classification";

    private static final String N_CLASSIFICATION_TYPE = "type";
    private static final String N_CLASSIFICATION_ORIGIN = "classificationOrigin";
    private static final String N_CLASSIFICATION_ORIGIN_GUID = "classificationOriginGUID";

    public static final String CLASSIFICATION_PROPERTIES_NS = "classificationProperties";
    public static final String N_LAST_CLASSIFICATION_CHANGE = "lastClassificationChange";

    private static final Set<String> KNOWN_PROPERTIES = createKnownProperties();
    private static Set<String> createKnownProperties() {
        Set<String> set = new HashSet<>();
        set.add(N_CLASSIFICATION_ORIGIN);
        set.add(N_CLASSIFICATION_ORIGIN_GUID);
        return set;
    }

    private List<Classification> classifications;
    private XtdbDocument xtdbDoc;
    private final String namespace;

    /**
     * Construct a mapping from a Classification (to map to a XTDB representation).
     * @param xtdbConnector connectivity to XTDB
     * @param classifications from which to map
     * @param namespace under which to qualify the classifications
     */
    public ClassificationMapping(XtdbOMRSRepositoryConnector xtdbConnector,
                                 List<Classification> classifications,
                                 String namespace) {
        super(xtdbConnector);
        this.classifications = classifications;
        this.namespace = namespace;
    }

    /**
     * Construct a mapping from a XTDB map (to map to an Egeria representation).
     * @param xtdbConnector connectivity to XTDB
     * @param xtdbDoc from which to map
     * @param namespace under which the classifications are qualified
     */
    public ClassificationMapping(XtdbOMRSRepositoryConnector xtdbConnector,
                                 XtdbDocument xtdbDoc,
                                 String namespace) {
        super(xtdbConnector);
        this.xtdbDoc = xtdbDoc;
        this.namespace = namespace;
    }

    /**
     * Check whether the specified property is a known base-level Classification property.
     * @param property to check
     * @return boolean
     */
    public static boolean isKnownBaseProperty(String property) {
        return KNOWN_PROPERTIES.contains(property);
    }

    /**
     * Add the details of the mapping to the provided XtdbDocument builder.
     * @param builder into which to add the classification details
     */
    public void addToXtdbDoc(XtdbDocument.Builder builder) {

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
                InstancePropertiesMapping.addToDoc(xtdbConnector, builder, classification.getType(), classification.getProperties(), getNamespaceForProperties(qualifiedNamespace));
            }
            // Add the list of classification names, for easing search
            builder.put(getKeyword(namespace), PersistentVector.create(classificationNames));
            // Add the latest change to any classification for internal tracking of validity
            builder.put(getKeyword(N_LAST_CLASSIFICATION_CHANGE), latestChange);
        }

    }

    /**
     * Map from XTDB to Egeria.
     * @return {@code List<Classification>}
     * @see #ClassificationMapping(XtdbOMRSRepositoryConnector, XtdbDocument, String)
     */
    public List<Classification> toEgeria() {

        if (classifications != null) {
            return classifications;
        } else if (xtdbDoc == null) {
            return null;
        } else {
            return fromDoc();
        }

    }

    /**
     * Translate the provided XTDB representation into an Egeria representation.
     * @return {@code List<Classification>}
     */
    protected List<Classification> fromDoc() {

        List<Classification> list = new ArrayList<>();
        // Start by retrieving the list of classification names
        IPersistentVector classificationNames = (IPersistentVector) xtdbDoc.get(getKeyword(namespace));

        if (classificationNames != null) {
            // Then, for each classification associated with the document...
            for (int i = 0; i < classificationNames.length(); i++) {

                String classificationName = (String) classificationNames.nth(i);
                String namespaceForClassification = getNamespaceForClassification(classificationName);

                Classification classification = new Classification();
                classification.setName(classificationName);
                super.fromDoc(classification, xtdbDoc, namespaceForClassification);

                // Retrieve its embedded type details (doing this rather than going to TypeDef from repositoryHelper,
                // since these could change over history of the document)
                IPersistentMap embeddedType = (IPersistentMap) xtdbDoc.get(getKeyword(namespaceForClassification, N_CLASSIFICATION_TYPE));
                InstanceType classificationType = getDeserializedValue(xtdbConnector, CLASSIFICATION, N_CLASSIFICATION_TYPE, embeddedType, mapper.getTypeFactory().constructType(InstanceType.class));

                // And use these to retrieve the property mappings for this classification (only)
                InstanceProperties ip = InstancePropertiesMapping.getFromDoc(xtdbConnector, classificationType, xtdbDoc, namespaceForClassification + "." + CLASSIFICATION_PROPERTIES_NS);

                if (ip != null) {
                    classification.setProperties(ip);
                }

                String originGuid = (String) xtdbDoc.get(getKeyword(namespaceForClassification, N_CLASSIFICATION_ORIGIN_GUID));
                classification.setClassificationOriginGUID(originGuid);
                String originSymbolicName = (String) xtdbDoc.get(getKeyword(namespaceForClassification, N_CLASSIFICATION_ORIGIN));
                ClassificationOrigin classificationOrigin = getClassificationOriginFromSymbolicName(xtdbConnector, originSymbolicName);
                classification.setClassificationOrigin(classificationOrigin);

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
     * @param xtdbConnector connectivity to the repository
     * @param symbolicName to convert
     * @return ClassificationOrigin
     */
    public static ClassificationOrigin getClassificationOriginFromSymbolicName(XtdbOMRSRepositoryConnector xtdbConnector, String symbolicName) {
        final String methodName = "getClassificationOriginFromSymbolicName";
        for (ClassificationOrigin b : ClassificationOrigin.values()) {
            if (b.getName().equals(symbolicName)) {
                return b;
            }
        }
        xtdbConnector.logProblem(ClassificationMapping.class.getName(),
                methodName,
                XtdbOMRSAuditCode.NON_EXISTENT_ENUM,
                null,
                "ClassificationOrigin",
                symbolicName);
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

    /**
     * Retrieve the namespace for properties of the classification
     * @param qualifiedRoot the classification-qualified root for the namespace
     * @return String
     */
    public static String getNamespaceForProperties(String qualifiedRoot) {
        return qualifiedRoot + "." + CLASSIFICATION_PROPERTIES_NS;
    }

}
