/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
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

    public static final Set<String> KNOWN_PROPERTIES = createKnownProperties();
    private static Set<String> createKnownProperties() {
        Set<String> set = new HashSet<>();
        set.add(N_CLASSIFICATION_ORIGIN);
        set.add(N_CLASSIFICATION_ORIGIN_GUID);
        return set;
    }

    private List<Classification> classifications;
    private Map<Keyword, Object> cruxMap;
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
     * @param cruxMap from which to map
     * @param namespace under which the classifications are qualified
     */
    public ClassificationMapping(CruxOMRSRepositoryConnector cruxConnector,
                                 Map<Keyword, Object> cruxMap,
                                 String namespace) {
        super(cruxConnector);
        this.cruxMap = cruxMap;
        this.namespace = namespace;
    }

    /**
     * Map from Egeria to Crux.
     * @return {@code Map<Keyword, Object>}
     * @see #ClassificationMapping(CruxOMRSRepositoryConnector, List, String)
     */
    public Map<Keyword, Object> toCrux() {

        if (cruxMap == null && classifications != null && !classifications.isEmpty()) {
            cruxMap = toMap(classifications);
        }
        if (cruxMap != null) {
            return cruxMap;
        } else {
            return null;
        }

    }

    /**
     * Map from Crux to Egeria.
     * @return {@code List<Classification>}
     * @see #ClassificationMapping(CruxOMRSRepositoryConnector, Map, String)
     */
    public List<Classification> toEgeria() {

        if (classifications != null) {
            return classifications;
        } else if (cruxMap == null) {
            return null;
        } else {
            return fromMap(cruxMap);
        }

    }

    /**
     * Translate the provided Egeria representation into a Crux map.
     * @param cls Egeria representation from which to map
     * @return {@code Map<Keyword, Object>} Crux representation
     */
    protected Map<Keyword, Object> toMap(List<Classification> cls) {

        Map<Keyword, Object> map = new HashMap<>();
        List<String> classificationNames = new ArrayList<>();

        for (Classification classification : cls) {
            String classificationName = classification.getName();
            classificationNames.add(classificationName);
            String qualifiedNamespace = getNamespaceForClassification(classificationName);
            Map<Keyword, Object> mapForClassification = super.toMap(classification, qualifiedNamespace);
            if (mapForClassification == null) {
                mapForClassification = new HashMap<>();
            }
            mapForClassification.put(Keyword.intern(qualifiedNamespace, N_CLASSIFICATION_ORIGIN_GUID), classification.getClassificationOriginGUID());
            mapForClassification.put(Keyword.intern(qualifiedNamespace, N_CLASSIFICATION_ORIGIN), getSymbolicNameForClassificationOrigin(classification.getClassificationOrigin()));
            InstancePropertiesMapping ipm = new InstancePropertiesMapping(cruxConnector, classification.getProperties(), qualifiedNamespace + "." + CLASSIFICATION_PROPERTIES_NS);
            Map<Keyword, Object> propertyMap = ipm.toCrux();
            if (propertyMap != null) {
                mapForClassification.putAll(propertyMap);
            }
            map.putAll(mapForClassification);
        }

        // Add the list of classification names, for easing search
        map.put(Keyword.intern(namespace), PersistentVector.create(classificationNames));

        return map;

    }

    /**
     * Translate the provided Crux representation into an Egeria representation.
     * @param map from which to map
     * @return {@code List<Classification>}
     */
    protected List<Classification> fromMap(Map<Keyword, Object> map) {

        Map<String, Classification> classNameToDetails = new TreeMap<>();
        Map<String, Map<String, InstancePropertyValue>> classNameToPropertiesMap = new TreeMap<>();

        // We will need to loop through all objects in the doc to find all the classifications
        for (Map.Entry<Keyword, Object> entry : map.entrySet()) {

            Keyword property = entry.getKey();
            Object objValue = entry.getValue();
            String detectedNamespace = property.getNamespace();

            if (detectedNamespace != null && detectedNamespace.startsWith(namespace)) {
                // Only here do we know we have a classification to be mapped...
                String value = objValue == null ? null : objValue.toString();
                String classificationName = getClassificationNameFromNamespace(detectedNamespace);
                String propertyName = property.getName();
                if (detectedNamespace.endsWith("." + CLASSIFICATION_PROPERTIES_NS)) {
                    // We've hit classification-specific instance properties, which we need to consolidate
                    if (!classNameToPropertiesMap.containsKey(classificationName)) {
                        classNameToPropertiesMap.put(classificationName, new HashMap<>());
                    }
                    InstancePropertyValueMapping.addInstancePropertyValueToMap(classNameToPropertiesMap.get(classificationName), propertyName, value);
                } else {
                    // Otherwise it should just be a "normal" property
                    if (!classNameToDetails.containsKey(classificationName)) {
                        Classification classification = new Classification();
                        // Initialise with the name and other header properties
                        classification.setName(classificationName);
                        super.fromMap(classification, map, detectedNamespace);
                        classNameToDetails.put(classificationName, classification);
                    }
                    if (N_CLASSIFICATION_ORIGIN.equals(propertyName)) {
                        classNameToDetails.get(classificationName).setClassificationOrigin(getClassificationOriginFromSymbolicName(value));
                    } else if (N_CLASSIFICATION_ORIGIN_GUID.equals(propertyName)) {
                        classNameToDetails.get(classificationName).setClassificationOriginGUID(value);
                    }
                }

            }

        }

        // Now we need to iterate through the classifications we parsed out, to:
        // 1. set their instance properties (only possible now after they've all been consolidated by iterating above)
        // 2. put the overall results into a list
        List<Classification> list = new ArrayList<>();
        for (Map.Entry<String, Classification> entry : classNameToDetails.entrySet()) {
            String classificationName = entry.getKey();
            Classification classification = entry.getValue();
            Map<String, InstancePropertyValue> propertiesMap = classNameToPropertiesMap.getOrDefault(classificationName, null);
            if (propertiesMap != null) {
                // Set the classification properties (if they are non-empty)
                InstanceProperties ip = new InstanceProperties();
                ip.setInstanceProperties(propertiesMap);
                classification.setProperties(ip);
            }
            list.add(classification);
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
     * Given a qualified namespace, parse out the name of the classification.
     * @param ns from which to parse the classification name
     * @return String classification name
     */
    private String getClassificationNameFromNamespace(String ns) {
        return getClassificationNameFromNamespace(namespace, ns);
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
