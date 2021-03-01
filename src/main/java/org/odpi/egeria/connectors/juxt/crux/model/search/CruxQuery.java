/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.model.search;

import clojure.lang.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.odpi.egeria.connectors.juxt.crux.mapping.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.MatchCriteria;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Captures the structure of a query against Crux.
 */
public class CruxQuery {

    private static final Logger log = LoggerFactory.getLogger(CruxQuery.class);

    // Variable names (for sorting)
    public static final Symbol DOC_ID = Symbol.intern("e");
    public static final Symbol CREATE_TIME = Symbol.intern("ct");
    public static final Symbol UPDATE_TIME = Symbol.intern("ut");
    public static final Symbol SORT_PROPERTY = Symbol.intern("sp");
    protected static final Symbol ELIDE = Symbol.intern("_");

    // Sort orders
    protected static final Keyword SORT_ASCENDING = Keyword.intern("asc");
    protected static final Keyword SORT_DESCENDING = Keyword.intern("desc");

    // Predicates (for comparisons)
    protected static final Symbol WILDCARD_TEXT_SEARCH_CI = Symbol.intern("wildcard-text-search-ci");
    protected static final Symbol WILDCARD_TEXT_SEARCH_CS = Symbol.intern("wildcard-text-search-cs");
    protected static final Symbol TEXT_SEARCH_CI = Symbol.intern("text-search-ci");
    protected static final Symbol TEXT_SEARCH_CS = Symbol.intern("text-search-cs");
    protected static final Symbol OR_OPERATOR = Symbol.intern("or");
    protected static final Symbol AND_OPERATOR = Symbol.intern("and");
    protected static final Symbol NOT_OPERATOR = Symbol.intern("not");
    protected static final Symbol OR_JOIN = Symbol.intern("or-join");
    protected static final Symbol GT_OPERATOR = Symbol.intern(">");
    protected static final Symbol GTE_OPERATOR = Symbol.intern(">=");
    protected static final Symbol LT_OPERATOR = Symbol.intern("<");
    protected static final Symbol LTE_OPERATOR = Symbol.intern("<=");
    protected static final Symbol IS_NULL_OPERATOR = Symbol.intern("nil?");
    protected static final Symbol NOT_NULL_OPERATOR = Symbol.intern("some?");
    protected static final Symbol REGEX_OPERATOR = Symbol.intern("re-matches");
    protected static final Symbol IN_OPERATOR = Symbol.intern("contains?");
    protected static final Symbol SET_OPERATOR = Symbol.intern("hash-set");

    // String predicates
    protected static final Symbol STARTS_WITH = Symbol.intern("clojure.string/starts-with?");
    protected static final Symbol CONTAINS = Symbol.intern("clojure.string/includes?");
    protected static final Symbol ENDS_WITH = Symbol.intern("clojure.string/ends-with?");
    protected static final Symbol STR_OPERATOR = Symbol.intern("str");

    protected static final Map<PropertyComparisonOperator, Symbol> PCO_TO_SYMBOL = createPropertyComparisonOperatorToSymbolMap();
    private static Map<PropertyComparisonOperator, Symbol> createPropertyComparisonOperatorToSymbolMap() {
        Map<PropertyComparisonOperator, Symbol> map = new HashMap<>();
        map.put(PropertyComparisonOperator.GT, GT_OPERATOR);
        map.put(PropertyComparisonOperator.GTE, GTE_OPERATOR);
        map.put(PropertyComparisonOperator.LT, LT_OPERATOR);
        map.put(PropertyComparisonOperator.LTE, LTE_OPERATOR);
        map.put(PropertyComparisonOperator.IS_NULL, IS_NULL_OPERATOR);
        map.put(PropertyComparisonOperator.NOT_NULL, NOT_NULL_OPERATOR);
        map.put(PropertyComparisonOperator.LIKE, REGEX_OPERATOR);
        map.put(PropertyComparisonOperator.IN, IN_OPERATOR);
        return map;
    }

    private static final Pattern ESCAPE_SPACES = Pattern.compile("(\\s)");

    private IPersistentMap query;
    private final List<Symbol> findElements;
    protected final List<IPersistentCollection> conditions;
    private final List<IPersistentVector> sequencing;

    /**
     * Default constructor for a new query.
     */
    public CruxQuery() {
        query = PersistentArrayMap.EMPTY;
        findElements = new ArrayList<>();
        findElements.add(DOC_ID); // Always have the DocID itself as the first element, to ease parsing of results
        conditions = new ArrayList<>();
        sequencing = new ArrayList<>();
    }

    /**
     * Add a condition to match either endpoint of a relationship to the provided reference (primary key).
     * @param reference the primary key value of an entity, used to match either end of a relationship
     */
    public void addRelationshipEndpointConditions(String reference) {
        List<Object> orConditions = new ArrayList<>();
        orConditions.add(OR_OPERATOR);
        orConditions.add(getReferenceCondition(Keyword.intern(RelationshipMapping.ENTITY_ONE_PROXY), reference));
        orConditions.add(getReferenceCondition(Keyword.intern(RelationshipMapping.ENTITY_TWO_PROXY), reference));
        conditions.add(PersistentList.create(orConditions));
    }

    /**
     * Add a condition to match the value of a property to a reference (primary key).
     * @param property to match
     * @param reference the primary key value to which the property should refer
     * @return PersistentVector for the condition
     */
    protected PersistentVector getReferenceCondition(Keyword property, String reference) {
        return PersistentVector.create(DOC_ID, property, reference);
    }

    /**
     * Add a condition to limit the type of the results by their TypeDef GUID.
     * @param typeGuid by which to limit the results (if null, will be ignored)
     * @param subtypeLimits limit the results to only these subtypes (if provided: ignored if typeGuid is null)
     */
    public void addTypeCondition(String typeGuid, List<String> subtypeLimits) {
        if (typeGuid != null) {
            conditions.add(getTypeCondition(DOC_ID, typeGuid, subtypeLimits));
        }
    }

    /**
     * Add a condition to limit the type of the results by their TypeDef GUID.
     * @param variable to resolve against the type
     * @param typeGuid by which to limit the results
     * @param subtypeLimits limit the results to only these subtypes (if provided)
     * @return IPersistentList of the conditions
     */
    protected IPersistentList getTypeCondition(Symbol variable, String typeGuid, List<String> subtypeLimits) {
        List<Object> orConditions = new ArrayList<>();
        orConditions.add(OR_OPERATOR);
        if (subtypeLimits != null && !subtypeLimits.isEmpty()) {
            // If subtypes were specified, search only for those (explicitly)
            for (String subtypeGuid : subtypeLimits) {
                orConditions.add(PersistentVector.create(variable, Keyword.intern(InstanceAuditHeaderMapping.TYPE_DEF_GUID), subtypeGuid));
                orConditions.add(PersistentVector.create(variable, Keyword.intern(InstanceAuditHeaderMapping.SUPERTYPE_DEF_GUIDS), subtypeGuid));
            }
        } else {
            // Otherwise, search for any matches against the typeGuid exactly or where it is a supertype
            // - exactly matching the TypeDef:  [e :type.guid "..."]
            orConditions.add(PersistentVector.create(variable, Keyword.intern(InstanceAuditHeaderMapping.TYPE_DEF_GUID), typeGuid));
            // - matching any of the super types:  [e :type.supers "...]
            orConditions.add(PersistentVector.create(variable, Keyword.intern(InstanceAuditHeaderMapping.SUPERTYPE_DEF_GUIDS), typeGuid));
        }
        return PersistentList.create(orConditions);
    }

    /**
     * Add a condition to limit the results to those with the provided type definition category (ie. only entities or
     * only relationships).
     * @param category by which to limit results
     */
    public void addTypeDefCategoryCondition(TypeDefCategory category) {
        if (category != null) {
            conditions.add(PersistentVector.create(DOC_ID, Keyword.intern(InstanceAuditHeaderMapping.TYPE_DEF_CATEGORY), category.getOrdinal()));
        }
    }

    /**
     * Add conditions to the search to find any text field that matches the supplied criteria (without a separate Lucene
     * index).
     * @param regexCriteria defining what should be matched
     * @param repositoryHelper through which we can introspect the type definitions and their properties
     * @param repositoryName of the repository (for logging)
     * @param typesToInclude defining which type definitions should be included in the search (to limit the properties)
     * @param namespace by which to qualify the properties
     * @param luceneEnabled indicates whether Lucene search index is configured (true) or not (false)
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     */
    public void addWildcardTextCondition(String regexCriteria,
                                         OMRSRepositoryHelper repositoryHelper,
                                         String repositoryName,
                                         Set<String> typesToInclude,
                                         String namespace,
                                         boolean luceneEnabled,
                                         boolean luceneRegexes) {

        final String methodName = "addWildcardTextCondition";
        log.debug("Falling back to a non-Lucene wildcard text condition (likely to be slow!): {}", regexCriteria);

        PrimitivePropertyValue string = new PrimitivePropertyValue();
        string.setPrimitiveDefCategory(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_STRING);
        string.setPrimitiveValue(regexCriteria);

        // Build up a Set of all of the unique string properties across all of the types that are to be included for
        // the search
        Set<Keyword> stringProperties = new HashSet<>();
        for (String typeDefName : typesToInclude) {
            TypeDef typeDef = repositoryHelper.getTypeDefByName(repositoryName, typeDefName);
            if (typeDef != null) {
                List<TypeDefAttribute> properties = repositoryHelper.getAllPropertiesForTypeDef(repositoryName, typeDef, methodName);
                for (TypeDefAttribute property : properties) {
                    Set<Keyword> propertyRefs = InstancePropertyValueMapping.getKeywordsForProperty(repositoryName,
                            repositoryHelper,
                            property.getAttributeName(),
                            namespace,
                            typesToInclude,
                            string);
                    stringProperties.addAll(propertyRefs);
                }
            }
        }

        List<Object> wrapped = new ArrayList<>();
        // Note that we will only wrap with OR if there is more than a single condition...
        if (stringProperties.size() > 1) {
            wrapped.add(OR_OPERATOR);
        }
        // For each string attribute, add an "or" condition that matches against the provided regex
        for (Keyword propertyRef : stringProperties) {
            Symbol var = Symbol.intern("v");
            List<IPersistentCollection> propertyConditions = getConditionForPropertyRef(
                    propertyRef,
                    PropertyComparisonOperator.LIKE,
                    string,
                    MatchCriteria.ANY,
                    var,
                    repositoryHelper,
                    luceneEnabled,
                    luceneRegexes
            );
            if (stringProperties.size() == 1) {
                // If there is only a single property, add it directly without any wrapping
                conditions.addAll(propertyConditions);
            } else {
                wrapped.addAll(propertyConditions);
            }
        }
        if (!wrapped.isEmpty()) {
            conditions.add(PersistentList.create(wrapped));
        }

    }

    /**
     * Adds conditions to the search to find any text field that matches the supplied criteria (leveraging a separate
     * Lucene index).
     * @param regexCriteria defining what should be matched
     * @param repositoryHelper through which we can check the regular expressions in the criteria
     * @param repositoryName of the repository (for logging)
     * @param typesToInclude defining which type definitions should be included in the search (to limit the properties)
     * @param namespace by which to qualify the properties
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     */
    public void addWildcardLuceneCondition(String regexCriteria,
                                           OMRSRepositoryHelper repositoryHelper,
                                           String repositoryName,
                                           Set<String> typesToInclude,
                                           String namespace,
                                           boolean luceneRegexes) {

        // Since a Lucene index has some limitations and will never support a full Java regex on its own, the idea here
        // will be to add the Lucene condition if we can, but if not possible we will fallback to a non-Lucene search
        if (regexCriteria != null && !regexCriteria.equals("")) {
            String searchString = getLuceneComparisonString(regexCriteria, repositoryHelper, luceneRegexes);
            if (searchString == null) {
                // If we cannot run a Lucene-optimised query, then we will fallback to a full OR-based text condition
                // comparison: which could be VERY slow, but as long as it does not exceed the query timeout threshold
                // should at least still return accurate results
                addWildcardTextCondition(
                        regexCriteria,
                        repositoryHelper,
                        repositoryName,
                        typesToInclude,
                        namespace,
                        true,
                        luceneRegexes
                );
            } else {
                // Otherwise, it is some Lucene-supported search clause so we can run it directly via Lucene
                // Add the lucene query: [(wildcard-text-search "text") [[e _]]]
                conditions.add(getLuceneWildcardClause(searchString, repositoryHelper.isCaseInsensitiveRegex(regexCriteria)));
            }
        }
    }

    /**
     * Translate the provided String expression into one that can be used against a Lucene index (or null if not possible).
     * @param regexCriteria expression to be translated
     * @param repositoryHelper through which we can check the regular expression in the criteria
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     * @return String that is usable for Lucene, or null if no Lucene search is possible
     */
    private String getLuceneComparisonString(String regexCriteria, OMRSRepositoryHelper repositoryHelper, boolean luceneRegexes) {
        if (repositoryHelper.isExactMatchRegex(regexCriteria)
                || repositoryHelper.isStartsWithRegex(regexCriteria)
                || repositoryHelper.isContainsRegex(regexCriteria)
                || repositoryHelper.isEndsWithRegex(regexCriteria)) {
            // For these basic regex conditions we can easily un-qualify to a plain string that we
            // can use to hit the Lucene index, so do so
            String searchString = repositoryHelper.getUnqualifiedLiteralString(regexCriteria);
            if (repositoryHelper.isStartsWithRegex(regexCriteria)) {
                searchString = escapeLucenePhrase(searchString) + "*";
            } else if (repositoryHelper.isEndsWithRegex(regexCriteria)) {
                searchString = "*" + escapeLucenePhrase(searchString);
            } else if (repositoryHelper.isContainsRegex(regexCriteria)) {
                searchString = "*" + escapeLucenePhrase(searchString) + "*";
            }
            return searchString;
        } else if (luceneRegexes) {
            // Otherwise, we must assume it is a more complex regex. If we are treating unquoted regexes as
            // Lucene-compatible, create a direct Lucene regex condition for it
            // Note: we must first ensure that the value is qualified as a regex for Lucene, by wrapping it in
            // forward slashes
            if (! (regexCriteria.startsWith("/") && regexCriteria.endsWith("/")) ) {
                regexCriteria = "/" + regexCriteria + "/";
            }
            return regexCriteria;
        }
        // In any other scenario, we are unable to handle the query via Lucene, so return a null
        return null;
    }

    /**
     * Retrieve a Lucene-oriented <code>wildcard-text-search</code> clause of the form: <code>[(wildcard-text-search-[ci|cs] "searchString") [[e _]]]</code>
     * @param searchString to use for the wildcard-text-search
     * @param isCaseInsensitive indicates whether the query should be case insensitive (true) or case sensitive (false)
     * @return IPersistentVector of the form <code>[(wildcard-text-search-[ci|cs] "searchString") [[e _]]]</code>
     */
    private IPersistentVector getLuceneWildcardClause(String searchString, boolean isCaseInsensitive) {
        List<Object> luceneCriteria = new ArrayList<>();
        if (isCaseInsensitive) {
            luceneCriteria.add(WILDCARD_TEXT_SEARCH_CI);
        } else {
            luceneCriteria.add(WILDCARD_TEXT_SEARCH_CS);
        }
        luceneCriteria.add(searchString);
        IPersistentVector deStructured = PersistentVector.create((IPersistentVector)PersistentVector.create(DOC_ID, ELIDE));
        List<IPersistentCollection> luceneQuery = new ArrayList<>();
        luceneQuery.add(PersistentList.create(luceneCriteria));
        luceneQuery.add(deStructured);
        return PersistentVector.create(luceneQuery);
    }

    /**
     * Retrieve a Lucene-oriented <code>text-search</code> clause of the form: <code>[(text-search-[ci|cs] :property "searchString") [[e _]]]</code>
     * @param propertyRef property whose value the text should be matched against
     * @param searchString to use for the text-search
     * @param isCaseInsensitive indicates whether the query should be case insensitive (true) or case sensitive (false)
     * @return IPersistentVector of the form <code>[(text-search-[ci|cs] :property "searchString") [[e _]]]</code>
     */
    private IPersistentVector getLuceneTermClause(Keyword propertyRef, String searchString, boolean isCaseInsensitive) {
        List<Object> luceneCriteria = new ArrayList<>();
        if (isCaseInsensitive) {
            luceneCriteria.add(TEXT_SEARCH_CI);
        } else {
            luceneCriteria.add(TEXT_SEARCH_CS);
        }
        luceneCriteria.add(propertyRef);
        luceneCriteria.add(searchString);
        IPersistentVector deStructured = PersistentVector.create((IPersistentVector)PersistentVector.create(DOC_ID, ELIDE));
        List<IPersistentCollection> luceneQuery = new ArrayList<>();
        luceneQuery.add(PersistentList.create(luceneCriteria));
        luceneQuery.add(deStructured);
        return PersistentVector.create(luceneQuery);
    }

    /**
     * Retrieve the set of conditions appropriate to Crux for the provided Egeria conditions.
     * @param searchProperties to translate
     * @param namespace by which to qualify properties
     * @param typeNames of all of the types we are including in the search
     * @param repositoryHelper through which we can lookup type information and properties
     * @param repositoryName of the repository (for logging)
     * @param luceneEnabled indicates whether Lucene search index is configured (true) or not (false)
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     */
    public void addPropertyConditions(SearchProperties searchProperties,
                                      String namespace,
                                      Set<String> typeNames,
                                      OMRSRepositoryHelper repositoryHelper,
                                      String repositoryName,
                                      boolean luceneEnabled,
                                      boolean luceneRegexes) {
        List<IPersistentCollection> cruxConditions = getPropertyConditions(
                searchProperties,
                namespace,
                false,
                typeNames,
                repositoryHelper,
                repositoryName,
                luceneEnabled,
                luceneRegexes
        );
        if (cruxConditions != null) {
            conditions.addAll(cruxConditions);
        }
    }

    /**
     * Retrieve the set of conditions appropriate to Crux for the provided Egeria conditions.
     * @param searchClassifications to translate
     * @param typeNames of all of the types we are including in the search
     * @param repositoryHelper through which we can lookup type information and properties
     * @param repositoryName of the repository (for logging)
     * @param luceneEnabled indicates whether Lucene search index is configured (true) or not (false)
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     */
    public void addClassificationConditions(SearchClassifications searchClassifications,
                                            Set<String> typeNames,
                                            OMRSRepositoryHelper repositoryHelper,
                                            String repositoryName,
                                            boolean luceneEnabled,
                                            boolean luceneRegexes) {
        List<IPersistentCollection> cruxConditions = getClassificationConditions(
                searchClassifications,
                typeNames,
                repositoryHelper,
                repositoryName,
                luceneEnabled,
                luceneRegexes
        );
        if (cruxConditions != null) {
            conditions.addAll(cruxConditions);
        }
    }

    /**
     * Retrieve a set of translated Crux conditions appropriate to the provided Egeria conditions.
     * @param searchClassifications to translate
     * @param typeNames of all of the types we are including in the search
     * @param repositoryHelper through which we can lookup type information and properties
     * @param repositoryName of the repository (for logging)
     * @param luceneEnabled indicates whether Lucene search index is configured (true) or not (false)
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     * @return {@code List<IPersistentCollection>}
     */
    protected List<IPersistentCollection> getClassificationConditions(SearchClassifications searchClassifications,
                                                                      Set<String> typeNames,
                                                                      OMRSRepositoryHelper repositoryHelper,
                                                                      String repositoryName,
                                                                      boolean luceneEnabled,
                                                                      boolean luceneRegexes) {
        if (searchClassifications != null) {
            // Since classifications can only be applied to entities, we can draw the namespace directly
            String namespace = EntitySummaryMapping.N_CLASSIFICATIONS;
            List<ClassificationCondition> classificationConditions = searchClassifications.getConditions();
            MatchCriteria matchCriteria = searchClassifications.getMatchCriteria();
            if (classificationConditions != null && !classificationConditions.isEmpty()) {
                List<IPersistentCollection> allConditions = new ArrayList<>();
                for (ClassificationCondition condition : classificationConditions) {
                    String classificationName = condition.getName();
                    // TODO: if there are multiple classification names, is there a risk this is interpreted as an AND
                    //  that the entity needs to possess ALL of these classifications (irrespective of the MatchCriteria)?
                    allConditions.add(PersistentVector.create(DOC_ID, Keyword.intern(namespace), classificationName));
                    String qualifiedNamespace = ClassificationMapping.getNamespaceForClassification(namespace, classificationName);
                    List<IPersistentCollection> matchConditions = getPropertyConditions(
                            condition.getMatchProperties(),
                            qualifiedNamespace,
                            matchCriteria.equals(MatchCriteria.ANY),
                            typeNames,
                            repositoryHelper,
                            repositoryName,
                            luceneEnabled,
                            luceneRegexes
                    );
                    if (matchConditions != null) {
                        allConditions.addAll(matchConditions);
                    }
                }
                return allConditions;
            }
        }
        return null;
    }

    /**
     * Retrieve a set of translated Crux conditions appropriate to the provided Egeria conditions.
     * @param searchProperties to translate
     * @param namespace by which to qualify properties
     * @param orNested true iff searchProperties is a set of conditions nested inside an OR (match criteria = ANY)
     * @param typeNames of all of the types we are including in the search
     * @param repositoryHelper through which we can lookup type information and properties
     * @param repositoryName of the repository (for logging)
     * @param luceneEnabled indicates whether Lucene search index is configured (true) or not (false)
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     * @return {@code List<IPersistentCollection>}
     */
    protected List<IPersistentCollection> getPropertyConditions(SearchProperties searchProperties,
                                                                String namespace,
                                                                boolean orNested,
                                                                Set<String> typeNames,
                                                                OMRSRepositoryHelper repositoryHelper,
                                                                String repositoryName,
                                                                boolean luceneEnabled,
                                                                boolean luceneRegexes) {
        if (searchProperties != null) {
            List<PropertyCondition> propertyConditions = searchProperties.getConditions();
            MatchCriteria matchCriteria = searchProperties.getMatchCriteria();
            if (propertyConditions != null && !propertyConditions.isEmpty()) {
                List<IPersistentCollection> allConditions = new ArrayList<>();
                for (PropertyCondition condition : propertyConditions) {
                    // Ensure every condition, whether nested or singular, is added to the 'allConditions' list
                    List<IPersistentCollection> cruxConditions = getSinglePropertyCondition(
                            condition,
                            matchCriteria,
                            namespace,
                            typeNames,
                            repositoryHelper,
                            repositoryName,
                            luceneEnabled,
                            luceneRegexes
                    );
                    if (cruxConditions != null && !cruxConditions.isEmpty()) {
                        allConditions.addAll(cruxConditions);
                    }
                }
                // apply the matchCriteria against the full set of nested property conditions
                List<Object> predicatedConditions = new ArrayList<>();
                switch (matchCriteria) {
                    case ALL:
                        if (orNested && allConditions.size() > 1) {
                            // we should only wrap with an 'AND' predicate if we're nested inside an 'OR' predicate and
                            // there is more than a single condition
                            predicatedConditions.add(AND_OPERATOR);
                            predicatedConditions.addAll(allConditions);
                        } else {
                            // otherwise, we can return the conditions directly (nothing more to process on them)
                            return allConditions;
                        }
                        break;
                    case ANY:
                        if (allConditions.size() == 1) {
                            // If only a single condition, return it directly (no wrapping necessary)
                            return allConditions;
                        }
                        // (or (and [e :property var] [(predicate ... var)]) )
                        predicatedConditions.add(OR_OPERATOR);
                        predicatedConditions.addAll(allConditions);
                        break;
                    case NONE:
                        // (not (or ... ) )
                        predicatedConditions.add(NOT_OPERATOR);
                        List<Object> or = new ArrayList<>();
                        or.add(OR_OPERATOR);
                        or.addAll(allConditions);
                        predicatedConditions.add(PersistentList.create(or));
                        break;
                    default:
                        log.warn("Unmapped match criteria: {}", matchCriteria);
                        break;
                }
                if (!predicatedConditions.isEmpty()) {
                    List<IPersistentCollection> wrapped = new ArrayList<>();
                    wrapped.add(getCruxCondition(predicatedConditions));
                    return wrapped;
                }
            }
        }
        return null;
    }

    /**
     * Translate the provided condition, considered on its own, into a Crux query condition. Handles both single
     * property conditions and nested conditions (though the latter simply recurse back to getPropertyConditions)
     * @param singleCondition to translate (should not contain nested condition)
     * @param outerCriteria the outer match criteria in which this condition is contained
     * @param namespace by which to qualify the properties in the condition
     * @param typeNames of all of the types we are including in the search
     * @param repositoryHelper through which we can lookup type information and properties
     * @param repositoryName of the repository (for logging)
     * @param luceneEnabled indicates whether Lucene search index is configured (true) or not (false)
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     * @return {@code List<IPersistentCollection>} giving the appropriate Crux query condition(s)
     * @see #getPropertyConditions(SearchProperties, String, boolean, Set, OMRSRepositoryHelper, String, boolean, boolean)
     */
    protected List<IPersistentCollection> getSinglePropertyCondition(PropertyCondition singleCondition,
                                                                     MatchCriteria outerCriteria,
                                                                     String namespace,
                                                                     Set<String> typeNames,
                                                                     OMRSRepositoryHelper repositoryHelper,
                                                                     String repositoryName,
                                                                     boolean luceneEnabled,
                                                                     boolean luceneRegexes) {
        SearchProperties nestedConditions = singleCondition.getNestedConditions();
        if (nestedConditions != null) {
            // If the conditions are nested, simply recurse back on getPropertyConditions
            MatchCriteria matchCriteria = nestedConditions.getMatchCriteria();
            return getPropertyConditions(
                    nestedConditions,
                    namespace,
                    matchCriteria.equals(MatchCriteria.ANY),
                    typeNames,
                    repositoryHelper,
                    repositoryName,
                    luceneEnabled,
                    luceneRegexes
            );
        } else {
            // Otherwise, parse through and process a single value condition
            String simpleName = singleCondition.getProperty();
            PropertyComparisonOperator comparator = singleCondition.getOperator();
            InstancePropertyValue value = singleCondition.getValue();
            if (InstanceAuditHeaderMapping.KNOWN_PROPERTIES.contains(simpleName)) {
                // InstanceAuditHeader properties should neither be namespace-d nor '.value' qualified, as they are not
                // InstanceValueProperties but simple native types
                Keyword propertyRef = getAuditHeaderPropertyRef(namespace, simpleName);
                return getConditionForPropertyRef(
                        propertyRef,
                        comparator,
                        value,
                        outerCriteria,
                        Symbol.intern(simpleName),
                        repositoryHelper,
                        luceneEnabled,
                        luceneRegexes
                );
            } else {
                // Any others we should assume are InstanceProperties, which will need namespace AND type AND '.value'
                // qualification to be searchable (of which there could be multiple, for a single given property, if
                // we are searching from high up the supertype tree)
                Set<Keyword> qualifiedSearchProperties;
                if (namespace.startsWith(EntitySummaryMapping.N_CLASSIFICATIONS) && !ClassificationMapping.KNOWN_PROPERTIES.contains(simpleName)) {
                    // Once again, if they are classification-specific instance properties, they need further qualification
                    String classificationNamespace = namespace + "." + ClassificationMapping.CLASSIFICATION_PROPERTIES_NS;
                    // Given the namespace qualification places into classificationProperties, we should ONLY need to
                    // search the classification type(s) for this property -- not all types
                    Set<String> classificationTypes = new HashSet<>();
                    String classificationTypeName = ClassificationMapping.getClassificationNameFromNamespace(EntitySummaryMapping.N_CLASSIFICATIONS, namespace);
                    classificationTypes.add(classificationTypeName);
                    qualifiedSearchProperties = InstancePropertyValueMapping.getKeywordsForProperty(repositoryName,
                            repositoryHelper,
                            simpleName,
                            classificationNamespace,
                            classificationTypes,
                            value);
                } else {
                    qualifiedSearchProperties = InstancePropertyValueMapping.getKeywordsForProperty(repositoryName,
                            repositoryHelper,
                            simpleName,
                            namespace,
                            typeNames,
                            value);
                }
                if (qualifiedSearchProperties.isEmpty()) {
                    log.warn("The provided property '{}' (as {}) did not match any of the type definition restrictions: {} -- forcing no results.", simpleName, value, typeNames);
                    return getNoResultsCondition();
                } else {
                    // Since depending on the types by which we are limiting the search there could be different variations
                    // of the same property name, we need to include criteria to find all of them -- so we must iterate
                    // through and build up a set of conditions for each variation of the property
                    List<IPersistentCollection> allPropertyConditions = new ArrayList<>();
                    Symbol symbolForVariable = Symbol.intern(simpleName);
                    List<List<IPersistentCollection>> conditionAggregator = new ArrayList<>();
                    for (Keyword qualifiedPropertyRef : qualifiedSearchProperties) {
                        List<IPersistentCollection> conditionsForOneProperty = getConditionForPropertyRef(
                                qualifiedPropertyRef,
                                comparator,
                                value,
                                outerCriteria,
                                symbolForVariable,
                                repositoryHelper,
                                luceneEnabled,
                                luceneRegexes
                        );
                        conditionAggregator.add(conditionsForOneProperty);
                    }
                    if (conditionAggregator.size() == 1) {
                        // If there is only a single condition, we can just add it directly:
                        // - ALL and ANY are the same with a single condition
                        // - NONE will already be wrapping the condition appropriately
                        allPropertyConditions.addAll(conditionAggregator.get(0));
                    } else {
                        // Otherwise, there are multiple conditions...
                        if (MatchCriteria.ALL.equals(outerCriteria)) {
                            // If the outer criteria is ALL, and there is more than one property variation to check, then we will
                            // need to OR the combined set of conditions, as only one of the property variations needs to
                            // match to meet that criteria. (Note: this is costly, but unavoidable so long as there are no
                            // type limiters specified by the caller)
                            List<Object> or = new ArrayList<>();
                            or.add(OR_OPERATOR);
                            for (List<IPersistentCollection> subList : conditionAggregator) {
                                if (subList.size() == 1) {
                                    // If there is only a single clause, add it directly to the OR
                                    or.addAll(subList);
                                } else {
                                    // If there are multiple, however, wrap them with an AND
                                    List<Object> andList = new ArrayList<>();
                                    andList.add(AND_OPERATOR);
                                    andList.addAll(subList);
                                    or.add(PersistentList.create(andList));
                                }
                            }
                            allPropertyConditions.add(PersistentList.create(or));
                        } else {
                            // For a NONE, we actually want to ensure that all of the conditions are AND-ed, so no need to
                            // wrap.
                            // For an ANY, there will already be an OR wrapped around the conditions by the
                            // caller of this method, so no need to wrap them again here.
                            // Therefore, in both cases, we just need to put all the conditions together and return them.
                            for (List<IPersistentCollection> subList : conditionAggregator) {
                                allPropertyConditions.addAll(subList);
                            }
                        }
                    }
                    return allPropertyConditions;
                }
            }
        }
    }

    /**
     * Retrieve the reference to use for an audit header property (these are generally unqualified (no namespace),
     * unless they are embedded within a classification).
     * @param namespace by which to qualify the property (for classifications)
     * @param propertyName for which to retrieve a reference
     * @return Keyword
     */
    protected Keyword getAuditHeaderPropertyRef(String namespace, String propertyName) {
        Keyword propertyRef;
        // InstanceAuditHeader properties should neither be namespace-d nor '.value' qualified, as they are not
        // InstanceValueProperties but simple native types
        if (namespace.startsWith(EntitySummaryMapping.N_CLASSIFICATIONS)) {
            // However, if they are instance headers embedded in a classification, they still need the base-level
            // classification namespace qualifier
            propertyRef = Keyword.intern(namespace, propertyName);
        } else {
            propertyRef = Keyword.intern(propertyName);
        }
        return propertyRef;
    }

    /**
     * Retrieve the Crux query condition(s) for the specified property and comparison operations.
     * @param propertyRef to compare
     * @param comparator comparison to carry out
     * @param value against which to compare
     * @param outerCriteria matching criteria inside of which this condition will exist
     * @param variable to which to compare
     * @param repositoryHelper through which we can introspect regular expressions
     * @param luceneEnabled indicates whether Lucene search index is configured (true) or not (false)
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     * @return {@code List<IPersistentCollection>} of the conditions
     */
    protected List<IPersistentCollection> getConditionForPropertyRef(Keyword propertyRef,
                                                                     PropertyComparisonOperator comparator,
                                                                     InstancePropertyValue value,
                                                                     MatchCriteria outerCriteria,
                                                                     Symbol variable,
                                                                     OMRSRepositoryHelper repositoryHelper,
                                                                     boolean luceneEnabled,
                                                                     boolean luceneRegexes) {

        List<IPersistentCollection> propertyConditions = new ArrayList<>();
        List<IPersistentCollection> clauseConditions = new ArrayList<>();

        if (comparator.equals(PropertyComparisonOperator.EQ)) {
            // For equality we can compare directly to the value and short-circuit any additional processing
            propertyConditions.add(getEqualsConditions(propertyRef, value));
            return propertyConditions;
        } else if (comparator.equals(PropertyComparisonOperator.NEQ)) {
            // Similarly for inequality, just by wrapping in a NOT predicate and short-circuit any additional processing
            propertyConditions.add(getNotEqualsConditions(propertyRef, value));
            return propertyConditions;
        } else {
            Symbol predicate = getPredicateForOperator(comparator);
            if (REGEX_OPERATOR.equals(predicate)) {
                // This method already handles wrapping, if needed, so we can return its results directly
                return getRegexConditions(propertyRef, value, outerCriteria, variable, repositoryHelper, luceneEnabled, luceneRegexes);
            } else if (IN_OPERATOR.equals(predicate)) {
                // For the IN comparison, we need an extra condition to setup the set to compare against
                // [(hash-set 1 2 3) las]    - needed for lists, to ensure the list is a unique set of keys to check against
                Symbol listAsSet = Symbol.intern("las");
                List<Object> forceSet = new ArrayList<>();
                forceSet.add(SET_OPERATOR);
                Object toCompare = InstancePropertyValueMapping.getValueForComparison(value);
                if (toCompare instanceof List) {
                    // add all elements of the array to the list
                    forceSet.addAll((List<?>)toCompare);
                }
                List<Object> set = new ArrayList<>();
                set.add(PersistentList.create(forceSet));
                set.add(listAsSet);
                IPersistentVector enforceSet = PersistentVector.create(set);
                // [(contains? las variable)]
                clauseConditions.add(enforceSet);
                List<Object> predicateComparison = new ArrayList<>();
                predicateComparison.add(predicate);
                predicateComparison.add(listAsSet);
                predicateComparison.add(variable);
                clauseConditions.add(PersistentVector.create(PersistentList.create(predicateComparison)));
            } else {
                // For everything else, we need a (predicate variable value) pattern
                // Setup a predicate comparing that variable to the value (with appropriate comparison operator)
                //  [(predicate variable "value")] - for a non-string predicate
                List<Object> predicateComparison = new ArrayList<>();
                predicateComparison.add(predicate);
                predicateComparison.add(variable);
                predicateComparison.add(InstancePropertyValueMapping.getValueForComparison(value));
                clauseConditions.add(PersistentVector.create(PersistentList.create(predicateComparison)));
            }

            // If we have not short-circuited, we need to translate the property's value into a variable
            //  [e :property variable]            - always needed, to define how to map the property's value to a variable
            IPersistentVector propertyToVariable = PersistentVector.create(DOC_ID, propertyRef, variable);

            // Start by wrapping everything with an 'and' predicate (only needed if outer condition is ANY (will be OR-wrapped))
            if (MatchCriteria.ANY.equals(outerCriteria) && (clauseConditions.size() > 1)) {
                // Since the variables involved across multiple conditions can be different, and an OR predicate
                // requires all of the variables to be the same, if the outer criteria is ANY we need to wrap these
                // two conditions together with an AND predicate (if there is more than one condition)
                List<Object> andWrapper = new ArrayList<>();
                andWrapper.add(AND_OPERATOR);
                andWrapper.add(propertyToVariable);
                andWrapper.addAll(clauseConditions);
                propertyConditions.add(PersistentList.create(andWrapper));
            } else {
                // Otherwise (NONE and ALL) we do not need any wrapping here (calling method will wrap with a
                // 'not' for a NONE, but we do not need an inner AND wrapping as it is implicit for a not)
                propertyConditions.add(propertyToVariable);
                propertyConditions.addAll(clauseConditions);
            }
        }

        return propertyConditions;

    }

    /**
     * Retrieve the optimal regular expression query conditions for the provided inputs.
     * @param propertyRef to compare
     * @param value against which to compare
     * @param outerCriteria matching criteria inside of which this condition will exist
     * @param variable to which to compare
     * @param repositoryHelper through which we can introspect regular expressions
     * @param luceneEnabled indicates whether Lucene search index is configured (true) or not (false)
     * @param luceneRegexes indicates whether unquoted regexes should be treated as Lucene compatible (true) or not (false)
     * @return {@code List<IPersistentCollection>} of the conditions
     */
    protected List<IPersistentCollection> getRegexConditions(Keyword propertyRef,
                                                             InstancePropertyValue value,
                                                             MatchCriteria outerCriteria,
                                                             Symbol variable,
                                                             OMRSRepositoryHelper repositoryHelper,
                                                             boolean luceneEnabled,
                                                             boolean luceneRegexes) {

        List<IPersistentCollection> propertyConditions = new ArrayList<>();
        List<IPersistentCollection> clauseConditions = new ArrayList<>();

        Object compareTo = InstancePropertyValueMapping.getValueForComparison(value);

        if (compareTo instanceof String) {

            // "easy" cases -- direct query is possible
            String regexSearchString = (String) compareTo;
            if (repositoryHelper.isExactMatchRegex(regexSearchString, false)) {
                // If we are looking for an exact match, we will short-circuit out of this clause-based
                // query and just do an equality condition -- should be faster
                String unqualifiedLiteralString = repositoryHelper.getUnqualifiedLiteralString(regexSearchString);
                propertyConditions.add(getEqualsConditions(propertyRef, unqualifiedLiteralString));
                return propertyConditions;
            } else if (luceneEnabled) {
                // If Lucene is enabled, use it's search clauses here rather than reverting straight to a full Java regex
                String searchString = getLuceneComparisonString(regexSearchString, repositoryHelper, luceneRegexes);
                if (searchString != null) {
                    // If we are able to do a Lucene query, short-circuit out of this with the direct Lucene query clause:
                    // [(text-search :property "text") [[e _]]]
                    propertyConditions.add(getLuceneTermClause(propertyRef, searchString, repositoryHelper.isCaseInsensitiveRegex(regexSearchString)));
                    return propertyConditions;
                }
                // If we cannot run a Lucene-optimised query (searchString is null), then we will fallback to a
                // full OR-based text condition comparison (immediately below): which could be VERY slow, but as
                // long as it does not exceed the query timeout threshold should at least still return accurate
                // results
            }

            // "fallback" cases -- must do a full regex comparison
            // Otherwise we will retrieve a clause-based comparison depending on the regex requested
            //  [e :property variable]            - always needed, to define how to map the property's value to a variable
            IPersistentVector propertyToVariable = PersistentVector.create(DOC_ID, propertyRef, variable);
            //  [(str variable) s_variable]       - needed for strings, to ensure the string is non-null (sets value to "" for nil)
            Symbol nonNullStringVar = Symbol.intern("sv");
            List<Object> forceString = new ArrayList<>();
            forceString.add(STR_OPERATOR);
            forceString.add(variable);
            IPersistentVector enforceNonNullStringValue = PersistentVector.create(PersistentList.create(forceString), nonNullStringVar);
            clauseConditions.add(enforceNonNullStringValue);
            //  [(re-matches #"regex" s_variable)] - for a regex-based (string) predicate
            List<Object> predicateComparison = getRegexCondition(regexSearchString, nonNullStringVar, repositoryHelper);
            clauseConditions.add(PersistentVector.create(PersistentList.create(predicateComparison)));
            // Start by wrapping everything with an 'and' predicate (only needed if outer condition is ANY (will be OR-wrapped))
            if (MatchCriteria.ANY.equals(outerCriteria)) {
                // Since the variables involved across multiple conditions can be different, and an OR predicate
                // requires all of the variables to be the same, if the outer criteria is ANY we need to wrap these
                // two conditions together with an AND predicate
                List<Object> andWrapper = new ArrayList<>();
                andWrapper.add(AND_OPERATOR);
                andWrapper.add(propertyToVariable);
                andWrapper.addAll(clauseConditions);
                propertyConditions.add(PersistentList.create(andWrapper));
            } else {
                // Otherwise (NONE and ALL) we do not need any wrapping here (calling method will wrap with a
                // 'not' for a NONE, but we do not need an inner AND wrapping as it is implicit for a not)
                propertyConditions.add(propertyToVariable);
                propertyConditions.addAll(clauseConditions);
            }

        } else {
            log.warn("Requested a regex-based search without providing a regex -- cannot add condition: {}", value);
        }

        return propertyConditions;

    }

    /**
     * Retrieve conditions to match where the provided property's value equals the provided value.
     * @param propertyRef whose value should be compared
     * @param value to compare against
     * @return IPersistentCollection giving the conditions
     */
    protected IPersistentCollection getEqualsConditions(Keyword propertyRef, InstancePropertyValue value) {
        return PersistentVector.create(DOC_ID, propertyRef, InstancePropertyValueMapping.getValueForComparison(value));
    }

    /**
     * Retrieve conditions to match where the provided property's value equals the provided string value.
     * @param propertyRef whose value should be compared
     * @param value to compare against
     * @return IPersistentCollection giving the conditions
     */
    protected IPersistentCollection getEqualsConditions(Keyword propertyRef, String value) {
        return PersistentVector.create(DOC_ID, propertyRef, value);
    }

    /**
     * Retrieve conditions to match where the provided property's value does not equal the provided value.
     * @param propertyRef whose value should be compared
     * @param value to compare against
     * @return IPersistentCollection giving the conditions
     */
    protected IPersistentCollection getNotEqualsConditions(Keyword propertyRef, InstancePropertyValue value) {
        List<Object> predicateComparison = new ArrayList<>();
        predicateComparison.add(NOT_OPERATOR);
        predicateComparison.add(PersistentVector.create(DOC_ID, propertyRef, InstancePropertyValueMapping.getValueForComparison(value)));
        return PersistentList.create(predicateComparison);
    }

    /**
     * Retrieve conditions to match the provided regular expression against the provided variable's value.
     * @param regexSearchString regular expression to match against
     * @param variable whose value should be compared against
     * @param repositoryHelper through which we can introspect regular expressions
     * @return {@code List<Object>} of the condition
     */
    protected List<Object> getRegexCondition(String regexSearchString,
                                             Symbol variable,
                                             OMRSRepositoryHelper repositoryHelper) {
        List<Object> predicateComparison = new ArrayList<>();
        // The equality case should already have been handled before coming into this method: we will now use
        // Clojure's built-in string comparisons for simple regexes (startsWith, contains, endsWith), and only
        // fall-back to a full regex comparison if the requested regex is for case-insensitive matches or some
        // more complicated expression
        if (repositoryHelper.isStartsWithRegex(regexSearchString, false)) {
            predicateComparison.add(STARTS_WITH);
            predicateComparison.add(variable);
            predicateComparison.add(repositoryHelper.getUnqualifiedLiteralString(regexSearchString));
        } else if (repositoryHelper.isContainsRegex(regexSearchString, false)) {
            predicateComparison.add(CONTAINS);
            predicateComparison.add(variable);
            predicateComparison.add(repositoryHelper.getUnqualifiedLiteralString(regexSearchString));
        } else if (repositoryHelper.isEndsWithRegex(regexSearchString, false)) {
            predicateComparison.add(ENDS_WITH);
            predicateComparison.add(variable);
            predicateComparison.add(repositoryHelper.getUnqualifiedLiteralString(regexSearchString));
        } else {
            // For all other regexes, we need a (predicate #"value" variable) pattern, so compile one
            Pattern regex = Pattern.compile(regexSearchString);
            predicateComparison.add(REGEX_OPERATOR);
            predicateComparison.add(regex);
            predicateComparison.add(variable);
        }
        return predicateComparison;
    }

    /**
     * Retrieve the Crux predicate for the provided comparison operation.
     * @param comparator to translate into a Crux predicate
     * @return Symbol giving the appropriate Crux predicate
     */
    protected Symbol getPredicateForOperator(PropertyComparisonOperator comparator) {
        Symbol toUse = PCO_TO_SYMBOL.getOrDefault(comparator, null);
        if (toUse == null) {
            log.warn("Unmapped comparison operator: {}", comparator);
        }
        return toUse;
    }

    /**
     * Translate the provided condition into the appropriate Crux representation (List for predicated-conditions, Vector
     * for any other conditions)
     * @param condition to translate
     * @return IPersistentCollection of the appropriate Crux representation
     */
    protected IPersistentCollection getCruxCondition(List<Object> condition) {
        if (condition != null && !condition.isEmpty()) {
            Object first = condition.get(0);
            if (first instanceof Symbol) {
                // If the first element is a Symbol, it's an OR, OR-JOIN, AND, NOT or NOT-JOIN -- create a list
                return PersistentList.create(condition);
            } else {
                // Otherwise (ie. single condition) assume it's a Vector -- create a Vector accordingly
                return PersistentVector.create(condition);
            }
        }
        return null;
    }

    /**
     * Add the provided statuses as limiters on which results should be retrieved from the query.
     * @param limitResultsByStatus list of statuses by which to limit results
     */
    public void addStatusLimiters(List<InstanceStatus> limitResultsByStatus) {
        if (limitResultsByStatus != null && !limitResultsByStatus.isEmpty()) {
            IPersistentCollection statusCondition = getStatusLimiters(DOC_ID, limitResultsByStatus);
            if (statusCondition != null) {
                conditions.add(statusCondition);
            }
        }
    }

    /**
     * Retrieve the status condition(s) for the provided status limiters.
     * @param variable that should be limited
     * @param limitResultsByStatus list of statuses by which to limit results
     * @return IPersistentCollection of the condition(s)
     */
    protected IPersistentCollection getStatusLimiters(Symbol variable, List<InstanceStatus> limitResultsByStatus) {
        IPersistentCollection result = null;
        List<IPersistentVector> statusConditions = new ArrayList<>();
        for (InstanceStatus limitByStatus : limitResultsByStatus) {
            Integer ordinal = EnumPropertyValueMapping.getOrdinalForInstanceStatus(limitByStatus);
            if (ordinal != null) {
                statusConditions.add(PersistentVector.create(variable, Keyword.intern(InstanceAuditHeaderMapping.CURRENT_STATUS), ordinal));
            }
        }
        if (!statusConditions.isEmpty()) {
            if (statusConditions.size() == 1) {
                // If there is only one, return it directly
                result = statusConditions.get(0);
            } else {
                // Otherwise, wrap the conditions in an OR-predicate, and add that list to the conditions
                List<Object> wrapped = new ArrayList<>();
                wrapped.add(OR_OPERATOR);
                wrapped.addAll(statusConditions);
                result = PersistentList.create(wrapped);
            }
        }
        return result;
    }

    /**
     * Add the sequencing information onto the query. (If both are null, will default to sorting by GUID.)
     * @param sequencingOrder by which to sequence the results
     * @param sequencingProperty by which to sequence the results (required if sorting by property, otherwise ignored)
     * @param namespace by which to qualify the sorting property (required if sorting by property, otherwise ignored)
     * @param typeNames of all of the types we are including in the search (required if sorting by property, otherwise ignored)
     * @param repositoryHelper through which we can lookup type information and properties (required if sorting by property, otherwise ignored)
     * @param repositoryName of the repository (for logging)
     */
    public void addSequencing(SequencingOrder sequencingOrder,
                              String sequencingProperty,
                              String namespace,
                              Set<String> typeNames,
                              OMRSRepositoryHelper repositoryHelper,
                              String repositoryName) {
        Set<Keyword> qualifiedSortProperties = null;
        if (sequencingProperty != null) {
            // Translate the provided sequencingProperty name into all of its possible appropriate property name
            // references (depends on the type limiting used for the search)
            qualifiedSortProperties = InstancePropertyValueMapping.getKeywordsForProperty(repositoryName, repositoryHelper, sequencingProperty, namespace, typeNames, null);
        }
        if (sequencingOrder == null) {
            // Default to sorting by GUID, if no sorting is defined (for consistent result ordering, paging, etc)
            sequencingOrder = SequencingOrder.GUID;
        }
        // Note: for sorting by anything other than document ID we need to ensure we also add the
        // element to the conditions and sequence (unless there already as part of another search criteria), hence the
        // 'addFindElement' logic.
        switch (sequencingOrder) {
            case LAST_UPDATE_OLDEST:
                addFindElement(UPDATE_TIME);
                conditions.add(PersistentVector.create(DOC_ID, Keyword.intern(InstanceAuditHeaderMapping.UPDATE_TIME), UPDATE_TIME));
                sequencing.add(PersistentVector.create(UPDATE_TIME, SORT_ASCENDING));
                break;
            case LAST_UPDATE_RECENT:
                addFindElement(UPDATE_TIME);
                conditions.add(PersistentVector.create(DOC_ID, Keyword.intern(InstanceAuditHeaderMapping.UPDATE_TIME), UPDATE_TIME));
                sequencing.add(PersistentVector.create(UPDATE_TIME, SORT_DESCENDING));
                break;
            case CREATION_DATE_OLDEST:
                addFindElement(CREATE_TIME);
                conditions.add(PersistentVector.create(DOC_ID, Keyword.intern(InstanceAuditHeaderMapping.CREATE_TIME), CREATE_TIME));
                sequencing.add(PersistentVector.create(CREATE_TIME, SORT_ASCENDING));
                break;
            case CREATION_DATE_RECENT:
                addFindElement(CREATE_TIME);
                conditions.add(PersistentVector.create(DOC_ID, Keyword.intern(InstanceAuditHeaderMapping.CREATE_TIME), CREATE_TIME));
                sequencing.add(PersistentVector.create(CREATE_TIME, SORT_DESCENDING));
                break;
            case PROPERTY_ASCENDING:
                if (qualifiedSortProperties == null || qualifiedSortProperties.isEmpty()) {
                    log.warn("Requested sort by property, but no valid property was provided ({}) given type limiters ({}) -- skipping sort.", sequencingProperty, typeNames);
                } else {
                    addPropertyBasedSorting(qualifiedSortProperties, SORT_ASCENDING);
                }
                break;
            case PROPERTY_DESCENDING:
                if (qualifiedSortProperties == null || qualifiedSortProperties.isEmpty()) {
                    log.warn("Requested sort by property, but no valid property was provided ({}) given type limiters ({}) -- skipping sort.", sequencingProperty, typeNames);
                } else {
                    addPropertyBasedSorting(qualifiedSortProperties, SORT_DESCENDING);
                }
                break;
            case ANY:
            case GUID:
            default:
                sequencing.add(PersistentVector.create(DOC_ID, SORT_ASCENDING));
                break;
        }
    }

    /**
     * Add the necessary conditions for sorting based on a property: somewhat complex as we need to ensure that the
     * property being used in the sort is included in the search results themselves, and given the qualification of
     * property names this could mean several different properties we ultimately need to attempt to sort by, and hence
     * this separate method.
     * @param qualifiedSortProperties the set of properties by which we will sort
     * @param order indicating ascending or descending
     */
    protected void addPropertyBasedSorting(Set<Keyword> qualifiedSortProperties, Keyword order) {
        addFindElement(SORT_PROPERTY);
        if (qualifiedSortProperties.size() == 1) {
            // If there is only a single condition for sorting, just add it directly
            for (Keyword propertyRef : qualifiedSortProperties) {
                conditions.add(PersistentVector.create(DOC_ID, propertyRef, SORT_PROPERTY));
            }
        } else {
            // Otherwise, we need to combine the conditions together as an or-join (use the first result against any
            // of them for a given instance for sorting that instance)
            List<Object> orJoinConditions = new ArrayList<>();
            orJoinConditions.add(OR_JOIN);
            orJoinConditions.add(PersistentVector.create(SORT_PROPERTY));
            for (Keyword propertyRef : qualifiedSortProperties) {
                orJoinConditions.add(PersistentVector.create(DOC_ID, propertyRef, SORT_PROPERTY));
            }
            conditions.add(PersistentList.create(orJoinConditions));
        }
        sequencing.add(PersistentVector.create(SORT_PROPERTY, order));
    }

    /**
     * Retrieve the query object, as ready-to-be-submitted to Crux API's query method.
     * @return IPersistentMap containing the query
     */
    public IPersistentMap getQuery() {
        // Add the elements to be found:  :find [ e ... ]
        query = query.assoc(Keyword.intern("find"), PersistentVector.create(findElements));
        // Add the conditions to the query:  :where [[ ... condition ...], [ ... condition ... ], ... ]
        query = query.assoc(Keyword.intern("where"), PersistentVector.create(conditions));
        // Add the sequencing information to the query:  :order-by [[ ... ]]
        query = query.assoc(Keyword.intern("order-by"), PersistentVector.create(sequencing));
        return query;
    }

    /**
     * Add the specified symbol to the list of those that are discovered by the search conditions (if not already in
     * the list)
     * @param element to add (if not already in the list)
     */
    protected void addFindElement(Symbol element) {
        if (!findElements.contains(element)) {
            findElements.add(element);
        }
    }

    /**
     * Retrieve a condition that will ensure no results are returned by a query.
     * @return {@code List<IPersistentCollection>}
     */
    protected static List<IPersistentCollection> getNoResultsCondition() {
        List<IPersistentCollection> conditions = new ArrayList<>();
        conditions.add(PersistentVector.create(DOC_ID, Keyword.intern(InstanceAuditHeaderMapping.TYPE_DEF_GUID), "NON_EXISTENT_TO_FORCE_NO_RESULTS"));
        return conditions;
    }

    /**
     * Escape the provided string so that it is interpreted as a complete literal phrase by Lucene.
     * @param phrase to escape
     * @return String that should be interpreted by Lucene as a complete phrase
     */
    private String escapeLucenePhrase(String phrase) {
        if (phrase != null) {
            String escaped = QueryParser.escape(phrase);
            // In addition to escaping special characters, we need to also escape spaces to avoid
            // the query parser interpreting a phrase as multiple words that each need to be matched as
            // anchored terms (which will likely always fail given a KeywordAnalyzer) -- for the sake of completeness
            // we will do this strictly against Java whitespace (space, tabs, newlines, etc) to ensure we match
            // even in those scenarios where there are multiple spaces together (each one will be individually escaped)
            return ESCAPE_SPACES.matcher(escaped).replaceAll("\\\\$1");
        }
        return null;
    }

}
