/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.model.search;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.crux.mapping.Constants;
import org.odpi.egeria.connectors.juxt.crux.mapping.EntitySummaryMapping;
import org.odpi.egeria.connectors.juxt.crux.mapping.RelationshipMapping;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures the structure of a query against Crux that spans the graph, and therefore could return
 * both relationships and entities together.
 */
public class CruxGraphQuery extends CruxQuery {

    private static final Symbol RELATIONSHIP = Symbol.intern("r");
    private static final Symbol TRANSITIVE = Symbol.intern("t");
    private static final Symbol START = Symbol.intern("s");
    private static final Keyword ENTITY_PROXIES = Keyword.intern(RelationshipMapping.ENTITY_PROXIES);
    private static final Symbol QUERY = Symbol.intern("q");

    /**
     * Default constructor for a new query.
     */
    public CruxGraphQuery() {
        super();
    }

    /**
     * Add condition(s) to limit the resulting relationships by the provided lists.
     * @param rootEntityGUID by which to narrow the relationships
     * @param relationshipTypeGUIDs of relationship type definition GUIDs by which to limit the results
     * @param limitResultsByStatus of relationship statuses by which to limit the results
     */
    public void addRelationshipLimiters(String rootEntityGUID, List<String> relationshipTypeGUIDs, List<InstanceStatus> limitResultsByStatus) {
        addFindElement(RELATIONSHIP);
        // [r :entityProxies e] [r :entityProxies "e_..."]
        conditions.add(getRelatedToCondition(DOC_ID));
        conditions.add(getRelatedToCondition(EntitySummaryMapping.getReference(rootEntityGUID)));
        if (relationshipTypeGUIDs != null && !relationshipTypeGUIDs.isEmpty()) {
            // [r :type.guids ...]
            conditions.addAll(getTypeCondition(RELATIONSHIP, TypeDefCategory.RELATIONSHIP_DEF, null, relationshipTypeGUIDs));
        }
        if (limitResultsByStatus != null && !limitResultsByStatus.isEmpty()) {
            conditions.addAll(getStatusLimiters(RELATIONSHIP, limitResultsByStatus));
        }
    }

    /**
     * Add condition(s) to limit the resulting entities by the provided criteria.
     * @param entityTypeGUIDs entity type definitions by which to limit
     * @param limitResultsByClassification entity classifications by which to limit
     */
    public void addEntityLimiters(List<String> entityTypeGUIDs, List<String> limitResultsByClassification) {
        if (entityTypeGUIDs != null && !entityTypeGUIDs.isEmpty()) {
            // [e :type.guids ...]
            conditions.addAll(getTypeCondition(DOC_ID, TypeDefCategory.ENTITY_DEF, null, entityTypeGUIDs));
        }
        if (limitResultsByClassification != null && !limitResultsByClassification.isEmpty()) {
            // [e :classifications ...]   ;; single classification, or for multiple:
            // [e :classifications classification] [(hash-set "..." "..." ...) cf] [(contains? cf classification)]
            conditions.addAll(getClassificationConditions(limitResultsByClassification));
        }
    }

    /**
     * Recursively traverse the graph using a rule-based query to find all (directly or indirectly) related entities to
     * the provided starting entity. This sets this as a nested query, to strictly focus on the related entities without
     * up-front applying any restrictions. Subsequent query restrictions can then be applied to the results of this
     * rule-based traversal to filter or sort the results.
     * @param startingDocId Crux document ID from which to radiate
     */
    public void addRelatedEntitiesNestedQuery(String startingDocId) {

        CruxQuery inner = new CruxQuery();

        Symbol related = Symbol.intern("related");

        // :where [(related s e)]
        List<Object> clause = new ArrayList<>();
        clause.add(related);
        clause.add(START);
        clause.add(DOC_ID);
        IPersistentList searchClause = PersistentList.create(clause);
        inner.conditions.add(searchClause);

        // :rules [(related [s] e)]
        clause = new ArrayList<>();
        clause.add(related);
        clause.add(PersistentVector.create(START));
        clause.add(DOC_ID);
        IPersistentList ruleClause = PersistentList.create(clause);

        // :where [[s :crux.db/id "..."]]
        inner.conditions.add(PersistentVector.create(START, Constants.CRUX_PK, startingDocId));

        List<Object> recurse = new ArrayList<>();
        recurse.add(related);
        recurse.add(TRANSITIVE);
        recurse.add(DOC_ID);

        // :rules [(related [s] e) [r :entityProxies s] [r :entityProxies e]]
        inner.rules.add(PersistentVector.create(ruleClause,
                getRelatedToCondition(START),
                getRelatedToCondition(DOC_ID)));

        // :rules [(related [s] e) [r :entityProxies s] [r :entityProxies t] (related t e)]
        inner.rules.add(PersistentVector.create(ruleClause,
                getRelatedToCondition(START),
                getRelatedToCondition(TRANSITIVE),
                PersistentList.create(recurse)));

        // :where [[(q {: find ... }) [[e]]]]
        List<Object> nested = new ArrayList<>();
        nested.add(QUERY);
        nested.add(inner.getQuery());
        conditions.add(PersistentVector.create(PersistentList.create(nested),
                PersistentVector.create((IPersistentVector) PersistentVector.create(DOC_ID))));

    }

    /**
     * Add condition(s) to limit the resulting entities by the provided classifications.
     * @param limitByClassifications of classifications on which to limit (must be at least one)
     * @return {@code List<IPersistentCollection>} of the conditions
     */
    protected List<IPersistentCollection> getClassificationConditions(List<String> limitByClassifications) {

        List<IPersistentCollection> classificationConditions = new ArrayList<>();
        Keyword classificationsRef = Keyword.intern(EntitySummaryMapping.N_CLASSIFICATIONS);

        if (limitByClassifications.size() == 1) {
            // If we need only match a single classification, add it directly
            classificationConditions.add(PersistentVector.create(DOC_ID, classificationsRef, limitByClassifications.get(0)));
        } else {

            // Otherwise, create a set of conditions looking up against a hash-set
            Symbol setVar = Symbol.intern("cf");
            Symbol classificationVar = Symbol.intern("classification");
            // [e :classifications classification]
            classificationConditions.add(PersistentVector.create(DOC_ID, classificationsRef, classificationVar));

            List<Object> set = new ArrayList<>();
            set.add(ConditionBuilder.SET_OPERATOR);
            set.addAll(limitByClassifications);
            // [(hash-set "..." "..." ...) cf]
            classificationConditions.add(PersistentVector.create(PersistentList.create(set), setVar));

            List<Object> contains = new ArrayList<>();
            contains.add(Symbol.intern("contains?"));
            contains.add(setVar);
            contains.add(classificationVar);
            // [(contains? cf classification)]
            classificationConditions.add(PersistentVector.create(PersistentList.create(contains)));

        }

        return classificationConditions;

    }

    /**
     * Add a condition to match the value of a property to a variable.
     * @param variable to match
     * @return PersistentVector for the condition
     */
    protected PersistentVector getRelatedToCondition(Symbol variable) {
        return PersistentVector.create(RELATIONSHIP, ENTITY_PROXIES, variable);
    }

    /**
     * Add a condition to match the value of a property to a literal.
     * @param literal to match
     * @return PersistentVector for the condition
     */
    protected PersistentVector getRelatedToCondition(String literal) {
        return PersistentVector.create(RELATIONSHIP, ENTITY_PROXIES, literal);
    }

}
