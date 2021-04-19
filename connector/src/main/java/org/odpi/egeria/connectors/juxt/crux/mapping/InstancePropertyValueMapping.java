/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import crux.api.CruxDocument;
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
 * qualification of which TypeDef defined that property. This is to ensure that different TypeDefinitions that use the same
 * property name, but which have different types (eg. position being a string vs an integer) can be distinguished. This
 * is necessary at a minimum because otherwise we will hit ClassCastExceptions in Clojure due to trying to compare the
 * same property with fundamentally different values (string vs int) if the property name is not qualified with the
 * type in which it is defined. (The '.json' value does not need to be qualified since we do not compare it but only
 * use it for fast-access serde purposes.)
 *
 * See the subclasses of this class, which handle mappings for the various subtypes of InstancePropertyValue for
 * details of each '.value' representation.
 */
public abstract class InstancePropertyValueMapping extends AbstractMapping {

    private static final Logger log = LoggerFactory.getLogger(InstancePropertyValueMapping.class);

    /**
     * Necessary default constructor to ensure we can use the static objectMapper of the base class.
     */
    protected InstancePropertyValueMapping() {
        super(null);
    }

    /**
     * Convert the provided Egeria value into a Crux comparable form.
     * @param ipv Egeria value to translate to Crux-comparable value
     * @return Object value that Crux can compare
     */
    public static Object getValueForComparison(InstancePropertyValue ipv) {
        InstancePropertyCategory category = ipv.getInstancePropertyCategory();
        Object value = null;
        switch (category) {
            case PRIMITIVE:
                value = PrimitivePropertyValueMapping.getPrimitiveValueForComparison((PrimitivePropertyValue) ipv);
                break;
            case ENUM:
                value = EnumPropertyValueMapping.getEnumPropertyValueForComparison((EnumPropertyValue) ipv);
                break;
            case ARRAY:
                value = ArrayPropertyValueMapping.getArrayPropertyValueForComparison((ArrayPropertyValue) ipv);
                break;
            case MAP:
                value = MapPropertyValueMapping.getMapPropertyValueForComparison((MapPropertyValue) ipv);
                break;
            case STRUCT:
                value = StructPropertyValueMapping.getStructPropertyValueForComparison((StructPropertyValue) ipv);
                break;
            case UNKNOWN:
            default:
                log.warn("Unmapped value type: {}", category);
                break;
        }
        return value;
    }

    /**
     * Retrieve a single property value from the provided Crux representation.
     * @param cruxDoc from which to retrieve the value
     * @param namespace by which the property is qualified
     * @param propertyName of the property
     * @return InstancePropertyValue giving Egeria representation of the value
     */
    public static InstancePropertyValue getInstancePropertyValueFromDoc(CruxDocument cruxDoc,
                                                                        String namespace,
                                                                        String propertyName) {

        // We will only pull values from the '.json'-qualified portion, given this is a complete JSON serialization
        Object objValue = cruxDoc.get(getKeyword(namespace, propertyName + ".json"));
        IPersistentMap embeddedValue = (objValue instanceof IPersistentMap) ? (IPersistentMap) objValue : null;
        if (embeddedValue != null) {
            return getInstancePropertyValue(embeddedValue);
        }
        return null;

    }

    /**
     * Add a single property value to the provided Crux representation.
     * @param cruxConnector connectivity to the repository
     * @param instanceType describing the instance to which this property applies
     * @param builder through which to add the property
     * @param propertyName of the property to add / replace
     * @param namespace by which the property should be qualified
     * @param value of the property
     */
    public static void addInstancePropertyValueToDoc(CruxOMRSRepositoryConnector cruxConnector,
                                                     InstanceType instanceType,
                                                     CruxDocument.Builder builder,
                                                     String propertyName,
                                                     String namespace,
                                                     InstancePropertyValue value) {

        // Persist the serialized form in all cases
        builder.put(getSerializedPropertyKeyword(namespace, propertyName), getEmbeddedSerializedForm(value));

        // And then also persist a searchable form
        if (value != null) {
            InstancePropertyCategory category = value.getInstancePropertyCategory();
            switch (category) {
                case PRIMITIVE:
                    PrimitivePropertyValueMapping.addPrimitivePropertyValueToDoc(
                            cruxConnector,
                            instanceType,
                            builder,
                            propertyName,
                            namespace,
                            (PrimitivePropertyValue) value
                    );
                    break;
                case ENUM:
                    EnumPropertyValueMapping.addEnumPropertyValueToDoc(
                            cruxConnector,
                            instanceType,
                            builder,
                            propertyName,
                            namespace,
                            (EnumPropertyValue) value
                    );
                    break;
                case ARRAY:
                    ArrayPropertyValueMapping.addArrayPropertyValueToDoc(
                            cruxConnector,
                            instanceType,
                            builder,
                            propertyName,
                            namespace,
                            (ArrayPropertyValue) value
                    );
                    break;
                case MAP:
                    MapPropertyValueMapping.addMapPropertyValueToDoc(
                            cruxConnector,
                            instanceType,
                            builder,
                            propertyName,
                            namespace,
                            (MapPropertyValue) value
                    );
                    break;
                case STRUCT:
                    StructPropertyValueMapping.addStructPropertyValueToDoc(
                            cruxConnector,
                            instanceType,
                            builder,
                            propertyName,
                            namespace,
                            (StructPropertyValue) value
                    );
                    break;
                case UNKNOWN:
                default:
                    log.warn("No searchable mapping yet implemented for InstancePropertyValue category: {}", category);
                    break;
            }
        } else {
            // If the value is null, create a null mapping for it (so we explicitly set the property to null for searching)
            builder.put(getPropertyValueKeyword(cruxConnector, instanceType, propertyName, namespace), null);
        }

    }

    /**
     * Retrieve the keyword to use to store the serialized value of the property.
     * @param namespace by which to qualify the property
     * @param propertyName of the property
     * @return String giving the qualified keyword
     */
    protected static String getSerializedPropertyKeyword(String namespace, String propertyName) {
        return getKeyword(namespace, propertyName + ".json");
    }

    /**
     * Retrieve the qualified Crux name for the value of the property.
     * @param cruxConnector connectivity to the repository
     * @param instanceType of the instance for which this property applies
     * @param propertyName of the property
     * @param namespace by which to qualify the property
     * @return String
     */
    protected static String getPropertyValueKeyword(CruxOMRSRepositoryConnector cruxConnector,
                                                     InstanceType instanceType,
                                                     String propertyName,
                                                     String namespace) {
        // Note that different TypeDefinitions may include the same attribute name, but with different types (eg. 'position'
        // meaning order (int) as well as role (string) -- we must therefore fully-qualify the propertyName with the
        // name of the typedef in which it is defined -- and that must be done here so that it flows down to any
        // subclasses as well.
        Set<String> typesToConsider = new HashSet<>();
        typesToConsider.add(instanceType.getTypeDefName());
        Set<String> names = getNamesForProperty(cruxConnector.getRepositoryName(),
                cruxConnector.getRepositoryHelper(),
                propertyName,
                namespace,
                typesToConsider,
                null);
        String qualified = null;
        if (names.size() > 1) {
            log.error("Found more than one property in this instanceType ({}) with the name '{}': {}", instanceType.getTypeDefName(), propertyName, names);
        }
        for (String name : names) {
            qualified = name;
        }
        return qualified;
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
     * @return {@code Set<String>} of the property references
     */
    public static Set<String> getNamesForProperty(String repositoryName,
                                                  OMRSRepositoryHelper repositoryHelper,
                                                  String propertyName,
                                                  String namespace,
                                                  Set<String> limitToTypes,
                                                  InstancePropertyValue value) {
        final String methodName = "getNamesForProperty";

        // Start by determining all valid combinations of propertyName in every type name provided in limitToTypes
        Set<String> validTypesForProperty = repositoryHelper.getAllTypeDefsForProperty(repositoryName, propertyName, methodName);
        Set<String> qualifiedNames = new TreeSet<>();

        // since the property itself may actually be defined at the super-type level of one of the limited types, we
        // cannot simply do a set intersection between types but must traverse and take the appropriate (super)type name
        // for qualification
        for (String typeNameWithProperty : validTypesForProperty) {
            String candidateRef = getFullyQualifiedPropertyNameForValue(namespace, typeNameWithProperty, propertyName);
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
    public static Set<Keyword> getKeywordsForProperty(String repositoryName,
                                                      OMRSRepositoryHelper repositoryHelper,
                                                      String propertyName,
                                                      String namespace,
                                                      Set<String> limitToTypes,
                                                      InstancePropertyValue value) {
        Set<Keyword> keywords = new TreeSet<>();
        Set<String> strings = getNamesForProperty(repositoryName, repositoryHelper, propertyName, namespace, limitToTypes, value);
        for (String string : strings) {
            keywords.add(Keyword.intern(string));
        }
        return keywords;
    }

    /**
     * Retrieve a partially-qualified property name that can be used to compare a Lucene match using ends-with.
     * @param propertyName of the property to reference
     * @return String match-able ending to the property (without any type qualification)
     */
    public static String getEndsWithPropertyNameForMatching(String propertyName) {
        return "." + propertyName + ".value";
    }

    /**
     * Retrieve a fully-qualified property name that can be used for value comparison purposes (ie. searching).
     * @param namespace under which to qualify the property
     * @param typeName by which to qualify the property (ie. ensure it is type-specific)
     * @param propertyName of the property to reference
     * @return Keyword reference to the property (fully-qualified)
     */
    private static String getFullyQualifiedPropertyNameForValue(String namespace, String typeName, String propertyName) {
        return getKeyword(namespace, typeName + getEndsWithPropertyNameForMatching(propertyName));
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
                        // In the case of a primitive, the value must either be an array (necessary for IN comparison)
                        // or also be a primitive, in which case its primitive type must match
                        return (value.getInstancePropertyCategory().equals(InstancePropertyCategory.ARRAY)) ||
                                (value.getInstancePropertyCategory().equals(InstancePropertyCategory.PRIMITIVE)
                                && ((PrimitivePropertyValue) value).getPrimitiveDefCategory().equals(pdc));
                    case ENUM_DEF:
                        return (value.getInstancePropertyCategory().equals(InstancePropertyCategory.ARRAY)) ||
                                (value.getInstancePropertyCategory().equals(InstancePropertyCategory.ENUM));
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
