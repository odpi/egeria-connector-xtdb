/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.model.search;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.crux.mapping.Constants;
import org.odpi.egeria.connectors.juxt.crux.mapping.EntitySummaryMapping;
import org.odpi.egeria.connectors.juxt.crux.mapping.RelationshipMapping;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures the structure of a query against Crux that spans the graph, and therefore could return
 * both relationships and entities together.
 */
public class CruxGraphQuery extends CruxQuery {

    private static final Symbol ROOT_ENTITY = Symbol.intern("se");
    private static final Symbol RELATIONSHIP = Symbol.intern("r");

    private String rootEntityRef;

    /**
     * Default constructor for a new query.
     */
    public CruxGraphQuery() {
        super();
        rootEntityRef = null;
    }

    /**
     * Add a condition for the starting point of an entity-radiating graph query.
     * @param entityGUID of the entity from which to radiate
     */
    public void addEntityAnchorCondition(String entityGUID) {
        // [se :crux.db/id :entity/a-guid-here]
        rootEntityRef = EntitySummaryMapping.getReference(entityGUID);
        conditions.add(PersistentVector.create(ROOT_ENTITY, Constants.CRUX_PK, rootEntityRef));
    }

    /**
     * Add condition(s) to limit the resulting relationships by the provided lists.
     * @param relationshipTypeGUIDs of relationship type definition GUIDs by which to limit the results
     * @param limitResultsByStatus of relationship statuses by which to limit the results
     */
    public void addRelationshipLimiters(List<String> relationshipTypeGUIDs, List<InstanceStatus> limitResultsByStatus) {
        addFindElement(RELATIONSHIP);
        // (or (and [r :entityOneProxy se] [r :entityTwoProxy e])
        //     (and [r :entityOneProxy e] [r: entityTwoProxy se]))
        List<Object> orConditions = new ArrayList<>();
        orConditions.add(OR_OPERATOR);
        List<Object> nestedAnd = new ArrayList<>();
        nestedAnd.add(AND_OPERATOR);
        nestedAnd.add(getRelatedToCondition(RelationshipMapping.ENTITY_ONE_PROXY, ROOT_ENTITY));
        nestedAnd.add(getRelatedToCondition(RelationshipMapping.ENTITY_TWO_PROXY, DOC_ID));
        orConditions.add(PersistentList.create(nestedAnd));
        nestedAnd = new ArrayList<>();
        nestedAnd.add(AND_OPERATOR);
        nestedAnd.add(getRelatedToCondition(RelationshipMapping.ENTITY_ONE_PROXY, DOC_ID));
        nestedAnd.add(getRelatedToCondition(RelationshipMapping.ENTITY_TWO_PROXY, ROOT_ENTITY));
        orConditions.add(PersistentList.create(nestedAnd));
        conditions.add(PersistentList.create(orConditions));
        if (relationshipTypeGUIDs != null && !relationshipTypeGUIDs.isEmpty()) {
            // (or [r :type.guid ...] [r :type.supers ...])
            conditions.add(getTypeCondition(RELATIONSHIP, null, relationshipTypeGUIDs));
        }
        if (limitResultsByStatus != null && !limitResultsByStatus.isEmpty()) {
            conditions.add(getStatusLimiters(RELATIONSHIP, limitResultsByStatus));
        }
    }

    /**
     * Add condition(s) to limit hte resulting entities by the provided criteria.
     * @param entityTypeGUIDs entity type definitions by which to limit
     * @param limitResultsByClassification entity classifications by which to limit
     */
    public void addEntityLimiters(List<String> entityTypeGUIDs, List<String> limitResultsByClassification) {
        if (entityTypeGUIDs != null && !entityTypeGUIDs.isEmpty()) {
            // (or [e :type.guid ...] [e :type.supers ...])
            conditions.add(getTypeCondition(DOC_ID, null, entityTypeGUIDs));
        }
        if (limitResultsByClassification != null && !limitResultsByClassification.isEmpty()) {
            // (or [e :classifications ...] [e :classifications ...])
            conditions.add(getClassificationConditions(limitResultsByClassification));
        }
    }

    /**
     * Add condition(s) to limit the resulting entities by the provided classifications.
     * @param limitByClassifications of classifications on which to limit (must be at least one)
     * @return IPersistentCollection of the conditions
     */
    protected IPersistentCollection getClassificationConditions(List<String> limitByClassifications) {
        Keyword classificationsRef = Keyword.intern(EntitySummaryMapping.N_CLASSIFICATIONS);
        if (limitByClassifications.size() == 1) {
            return PersistentVector.create(DOC_ID, classificationsRef, limitByClassifications.get(0));
        } else {
            List<Object> orConditions = new ArrayList<>();
            orConditions.add(OR_OPERATOR);
            for (String classificationName : limitByClassifications) {
                orConditions.add(PersistentVector.create(DOC_ID, classificationsRef, classificationName));
            }
            return PersistentList.create(orConditions);
        }
    }

    /**
     * Add a condition to match the value of a property to a reference (primary key).
     * @param property to match
     * @param variable to match
     * @return PersistentVector for the condition
     */
    protected PersistentVector getRelatedToCondition(Keyword property, Symbol variable) {
        return PersistentVector.create(RELATIONSHIP, property, variable);
    }

}
