/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Maps singular InstancePropertyValues between persistence and objects.
 *
 * These cannot simply be serialized to JSON as that would impact the ability to search their values correctly, so we
 * must break apart the values and the types for each property. In general, we will store the complete value into the
 * '.json' portion, but we will also store just the value alone (without any type details) into the '.value' portion.
 *
 * This will allow us to quickly pull back the complete value from a JSON-serialized form (from '.json') while also
 * providing a reliable search point at the '.value'. This class and its subclasses must be responsible for ensuring
 * that these two properties are kept aligned with each other at all times.
 *
 * Furthermore, the naming for the '.value' point used for searching must retain within its overall property name the
 * qualification of which TypeDef defined that property. This is to ensure that different TypeDefs that use the same
 * property name, but which have different types (eg. position being a string vs an integer) can be distinguished. This
 * is necessary at a minimum because otherwise we will hit ClassCastExceptions in Clojure due to trying to compare the
 * same property with fundamentally different values (string vs int) if the property name is not qualified with the
 * type in which it is defined. (The '.json' value does not need to be qualified since we do not compare it but only
 * use it for fast-access serde purposes.)
 *
 * See the subclasses of this class, which handle mappings for the various subtypes of InstancePropertyValue for
 * details of each '.value' representation.
 */
public class InstancePropertyValueMapping extends AbstractMapping {

    private static final Logger log = LoggerFactory.getLogger(InstancePropertyValueMapping.class);

    protected String namespace;
    protected InstanceType instanceType;
    protected String propertyName;
    protected InstancePropertyValue value = null;
    protected Map<Keyword, Object> cruxMap = null;

    /**
     * Default constructor.
     * @param cruxConnector connectivity to Crux
     */
    private InstancePropertyValueMapping(CruxOMRSRepositoryConnector cruxConnector) {
        super(cruxConnector);
    }

    /**
     * Construct a mapping from InstancePropertyValue (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param instanceType of the instance for which the value needs to be mapped
     * @param propertyName of the property to map
     * @param value from which to map
     * @param namespace by which to qualify properties
     */
    protected InstancePropertyValueMapping(CruxOMRSRepositoryConnector cruxConnector,
                                           InstanceType instanceType,
                                           String propertyName,
                                           InstancePropertyValue value,
                                           String namespace) {
        this(cruxConnector);
        this.instanceType = instanceType;
        this.propertyName = propertyName;
        this.value = value;
        this.namespace = namespace;
    }

    /**
     * Construct a mapping from InstancePropertyValue (to map to a Crux representation).
     * @param cruxConnector connectivity to Crux
     * @param instanceType of the instance for which the value is being mapped
     * @param propertyName of the property to map
     * @param value from which to map
     * @param namespace by which to qualify properties
     * @return InstancePropertyValueMapping appropriate to the type of the value
     */
    public static InstancePropertyValueMapping getInstancePropertyValueMappingForValue(CruxOMRSRepositoryConnector cruxConnector,
                                                                                       InstanceType instanceType,
                                                                                       String propertyName,
                                                                                       InstancePropertyValue value,
                                                                                       String namespace) {
        InstancePropertyValueMapping ipvm;
        if (value != null) {
            InstancePropertyCategory category = value.getInstancePropertyCategory();
            switch (category) {
                case PRIMITIVE:
                    ipvm = new PrimitivePropertyValueMapping(cruxConnector, instanceType, propertyName, (PrimitivePropertyValue) value, namespace);
                    break;
                case ENUM:
                    ipvm = new EnumPropertyValueMapping(cruxConnector, instanceType, propertyName, (EnumPropertyValue) value, namespace);
                    break;
                case MAP:
                case ARRAY:
                case STRUCT:
                case UNKNOWN:
                default:
                    ipvm = new InstancePropertyValueMapping(cruxConnector, instanceType, propertyName, value, namespace);
                    break;
            }
        } else {
            // Even if the value is null, create a mapping for it (so we explicitly set the property to null for searching)
            ipvm = new InstancePropertyValueMapping(cruxConnector, instanceType, propertyName, null, namespace);
        }
        return ipvm;
    }

    /**
     * Construct a mapping of the InstancePropertyValues (from a Crux representation), as a singular pass through the
     * map (rather than inefficiently trying to do so property-by-property).
     * @param cruxMap from which to map
     * @param namespace by which the properties are qualified
     * @return {@code Map<String, InstancePropertyValue>} keyed by Egeria property name
     */
    public static Map<String, InstancePropertyValue> getInstancePropertyValuesFromMap(Map<Keyword, Object> cruxMap,
                                                                                      String namespace) {
        Map<String, InstancePropertyValue> ipvs = new HashMap<>();
        for (Map.Entry<Keyword, Object> entry : cruxMap.entrySet()) {
            Keyword property = entry.getKey();
            String detectedNamespace = property.getNamespace();
            if (detectedNamespace != null && detectedNamespace.equals(namespace)) {
                String propertyName = property.getName();
                if (propertyName.endsWith(".json")) {
                    // We'll pull values from the '.json'-qualified portion, given this is a complete JSON serialization
                    Object objValue = entry.getValue();
                    IPersistentMap value = (objValue instanceof IPersistentMap) ? (IPersistentMap) objValue : null;
                    InstancePropertyValueMapping.addInstancePropertyValueToMap(ipvs, propertyName, value);
                }
            }
        }
        return ipvs;
    }

    /**
     * Map from Egeria to Crux.
     * @return {@code Map<Keyword, Object>}
     * @see #InstancePropertyValueMapping(CruxOMRSRepositoryConnector, InstanceType, String, InstancePropertyValue, String)
     */
    public Map<Keyword, Object> toCrux() {
        // Note that we are explicitly not checking whether the value is null, as we want to map all values (even nulls)
        // for allowing them to be searched
        if (cruxMap == null) {
            toMap();
        }
        if (cruxMap != null) {
            return cruxMap;
        } else {
            return null;
        }
    }

    /**
     * Translate the provided Egeria representation into the Crux map.
     */
    protected void toMap() {
        cruxMap = new HashMap<>();
        if (value != null) {
            addFullJSONToMap();
        }
        addValueToMap();
    }

    /**
     * Add the specified property value as a fully-serialized JSON representation to the map (for expedient retrieval
     * later back into an Egeria object).
     */
    protected void addFullJSONToMap() {
        cruxMap.put(getSerializedPropertyKeyword(), getEmbeddedSerializedForm(value));
    }

    /**
     * Add the specified value to the map. (Note that this method will generally be overridden by the subclasses to
     * define how the subclass-specific values are translated appropriately.) If it is NOT overridden, the value will
     * be written in a non-searchable JSON-serialized form.
     */
    protected void addValueToMap() {
        cruxMap.put(getPropertyValueKeyword(), getEmbeddedSerializedForm(value));
    }

    /**
     * Retrieve the qualified Crux name for the type of the property.
     * @return Keyword
     */
    protected Keyword getSerializedPropertyKeyword() {
        return Keyword.intern(namespace, propertyName + ".json");
    }

    /**
     * Retrieve the qualified Crux name for the value of the property.
     * @return Keyword
     */
    protected Keyword getPropertyValueKeyword() {
        // Note that different TypeDefs may include the same attribute name, but with different types (eg. 'position'
        // meaning order (int) as well as role (string) -- we must therefore fully-qualify the propertyName with the
        // name of the typedef in which it is defined -- and that must be done here so that it flows down to any
        // subclasses as well.
        Set<String> typesToConsider = new HashSet<>();
        typesToConsider.add(instanceType.getTypeDefName());
        Set<Keyword> names = getNamesForProperty(cruxConnector.getRepositoryName(),
                cruxConnector.getRepositoryHelper(),
                propertyName,
                namespace,
                typesToConsider,
                null);
        Keyword qualified = null;
        if (names.size() > 1) {
            log.error("Found more than one property in this instanceType ({}) with the name '{}': {}", instanceType.getTypeDefName(), propertyName, names);
        }
        for (Keyword name : names) {
            qualified = name;
        }
        return qualified;
    }

    /**
     * Add the specified value for the specified property name to the provided map of values.
     * @param ipvs map of values
     * @param propertyName to add
     * @param jsonValue to add
     */
    public static void addInstancePropertyValueToMap(Map<String, InstancePropertyValue> ipvs, String propertyName, IPersistentMap jsonValue) {
        // Only process the propertyName if it ends with the '.json' qualification
        // (This logic is intentionally in this method as it is called from elsewhere, like ClassificationMapping)
        if (propertyName.endsWith(".json")) {
            // Need to remove the '.json' qualifier before mapping to the Egeria property name
            String actualPropertyName = propertyName.substring(0, propertyName.length() - 5);
            InstancePropertyValue ipv = getInstancePropertyValue(jsonValue);
            ipvs.put(actualPropertyName, ipv);
        }
    }

    /**
     * Translate the provided JSON representation of a value into an Egeria object.
     * @param jsonValue to translate
     * @return InstancePropertyValue
     */
    private static InstancePropertyValue getInstancePropertyValue(IPersistentMap jsonValue) {
        return getDeserializedValue(jsonValue, mapper.getTypeFactory().constructType(InstancePropertyValue.class));
    }

    /**
     * Retrieve the fully-qualified names for the provided property, everywhere it could appear within a given type.
     * Note that generally the returned Set will only have a single element, however if requested from a sufficiently
     * abstract type (eg. Referenceable) under which different subtypes have the same property defined, the Set will
     * contain a property reference for each of those subtypes' properties.
     * @param repositoryName of the repository (for logging)
     * @param repositoryHelper utilities for introspecting type definitions and their properties
     * @param propertyName of the property for which to qualify type-specific references
     * @param namespace under which to qualify the properties
     * @param limitToTypes limit the type-specific qualifications to only properties that are applicable to these types
     * @param value that will be used for comparison, to limit the properties to include based on their type
     * @return {@code Set<Keyword>} of the property references
     */
    public static Set<Keyword> getNamesForProperty(String repositoryName,
                                                   OMRSRepositoryHelper repositoryHelper,
                                                   String propertyName,
                                                   String namespace,
                                                   Set<String> limitToTypes,
                                                   InstancePropertyValue value) {
        final String methodName = "getNamesForProperty";

        // Start by determining all valid combinations of propertyName in every type name provided in limitToTypes
        Set<String> validTypesForProperty = repositoryHelper.getAllTypeDefsForProperty(repositoryName, propertyName, methodName);
        Set<Keyword> qualifiedNames = new TreeSet<>();

        // since the property itself may actually be defined at the super-type level of one of the limited types, we
        // cannot simply do a set intersection between types but must traverse and take the appropriate (super)type name
        // for qualification
        for (String typeNameWithProperty : validTypesForProperty) {
            Keyword candidateRef = getFullyQualifiedPropertyNameForValue(namespace, typeNameWithProperty, propertyName);
            if (!qualifiedNames.contains(candidateRef)) { // short-circuit if we already have this one in the list
                for (String limitToType : limitToTypes) {
                    // Only if the type definition by which we are limiting is a subtype of this type definition should
                    // we consider the type definitions' properties
                    if (repositoryHelper.isTypeOf(repositoryName, limitToType, typeNameWithProperty)) {
                        // Only if the property's types align do we continue with ensuring that the type itself should be included
                        // (While this conditional itself will further loop over cached information, it should do so only
                        // in limited cases due to the short-circuiting above)
                        if (propertyDefMatchesValueType(repositoryHelper, repositoryName, typeNameWithProperty, propertyName, value)) {
                            qualifiedNames.add(candidateRef);
                        }
                    }
                }
            }
        }
        return qualifiedNames;
    }

    /**
     * Retrieve a fully-qualified property name that can be used for value comparison purposes (ie. searching).
     * @param namespace under which to qualify the property
     * @param typeName by which to qualify the property (ie. ensure it is type-specific)
     * @param propertyName of the property to reference
     * @return Keyword reference to the property (fully-qualified)
     */
    private static Keyword getFullyQualifiedPropertyNameForValue(String namespace, String typeName, String propertyName) {
        return Keyword.intern(namespace, typeName + "." + propertyName + ".value");
    }

    /**
     * Indicates whether the provided property value is of the same type as the named property in the specified type
     * definition.
     * @param repositoryHelper utilities for introspecting type definitions and their properties
     * @param repositoryName of the repository (for logging)
     * @param typeDefName of the type definition in which the property is defined
     * @param propertyName of the property for which to check the type definition
     * @param value that will be used for comparison, to limit the properties to include based on their type
     * @return boolean true if the value's type matches the property definition's type, otherwise false (if they do not match)
     */
    private static boolean propertyDefMatchesValueType(OMRSRepositoryHelper repositoryHelper,
                                                       String repositoryName,
                                                       String typeDefName,
                                                       String propertyName,
                                                       InstancePropertyValue value) {

        if (value == null) {
            // If the value is null, we cannot compare types, so must assume that they would match
            return true;
        }

        // Otherwise, determine the type of this property in the model, and only if they match consider including
        // this property
        TypeDef typeDef = repositoryHelper.getTypeDefByName(repositoryName, typeDefName);
        List<TypeDefAttribute> typeDefProperties = typeDef.getPropertiesDefinition();
        for (TypeDefAttribute typeDefProperty : typeDefProperties) {
            // Start by finding the property
            if (typeDefProperty.getAttributeName().equals(propertyName)) {
                AttributeTypeDef atd = typeDefProperty.getAttributeType();
                switch (atd.getCategory()) {
                    case PRIMITIVE:
                        PrimitiveDef pd = (PrimitiveDef) atd;
                        PrimitiveDefCategory pdc = pd.getPrimitiveDefCategory();
                        // In the case of a primitive, the value must also be a primitive and its primitive type
                        // must match
                        return (value.getInstancePropertyCategory().equals(InstancePropertyCategory.PRIMITIVE)
                                && ((PrimitivePropertyValue) value).getPrimitiveDefCategory().equals(pdc));
                    case ENUM_DEF:
                        return (value.getInstancePropertyCategory().equals(InstancePropertyCategory.ENUM));
                    case COLLECTION:
                        CollectionDef cd = (CollectionDef) atd;
                        switch (cd.getCollectionDefCategory()) {
                            // TODO: these may need deeper checks (eg. that the types within the array match, etc)
                            case OM_COLLECTION_ARRAY:
                                return (value.getInstancePropertyCategory().equals(InstancePropertyCategory.ARRAY));
                            case OM_COLLECTION_MAP:
                                return (value.getInstancePropertyCategory().equals(InstancePropertyCategory.MAP));
                            case OM_COLLECTION_STRUCT:
                                return (value.getInstancePropertyCategory().equals(InstancePropertyCategory.STRUCT));
                            case OM_COLLECTION_UNKNOWN:
                            default:
                                log.warn("Unhandled collection type definition category for comparison: {}", cd.getCollectionDefCategory());
                                break;
                        }
                    case UNKNOWN_DEF:
                    default:
                        log.warn("Unhandled attribute type definition category for comparison: {}", atd.getCategory());
                        break;
                }
            }
        }

        // If we have fallen through, the value does not have the same type as the property
        return false;

    }

}
