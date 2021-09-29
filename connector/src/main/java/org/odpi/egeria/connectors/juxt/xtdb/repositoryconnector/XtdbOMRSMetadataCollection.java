/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector;

import org.odpi.egeria.connectors.juxt.xtdb.cache.TypeDefCache;
import org.odpi.egeria.connectors.juxt.xtdb.txnfn.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import xtdb.api.tx.Transaction;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.Constants;
import org.odpi.openmetadata.frameworks.auditlog.AuditLog;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSDynamicTypeMetadataCollectionBase;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.HistorySequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.MatchCriteria;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.ClassificationCondition;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchClassifications;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryValidator;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Provides the OMRSMetadataCollection implementation for XTDB.
 */
public class XtdbOMRSMetadataCollection extends OMRSDynamicTypeMetadataCollectionBase {

    private static final Logger log = LoggerFactory.getLogger(XtdbOMRSMetadataCollection.class);

    private final XtdbOMRSRepositoryConnector xtdbRepositoryConnector;

    /**
     * Default constructor
     *
     * @param parentConnector      connector that this metadata collection supports.
     *                             The connector has the information to call the metadata repository.
     * @param repositoryName       name of this repository.
     * @param repositoryHelper     helper that provides methods to repository connectors and repository event mappers
     *                             to build valid type definitions, entities and relationships.
     * @param repositoryValidator  validator class for checking open metadata repository objects and parameters
     * @param metadataCollectionId unique identifier for the repository
     * @param auditLog             logging destination
     */
    public XtdbOMRSMetadataCollection(XtdbOMRSRepositoryConnector parentConnector,
                                      String repositoryName,
                                      OMRSRepositoryHelper repositoryHelper,
                                      OMRSRepositoryValidator repositoryValidator,
                                      String metadataCollectionId,
                                      AuditLog auditLog) {
        super(parentConnector, repositoryName, repositoryHelper, repositoryValidator, metadataCollectionId);
        log.debug("Constructing XtdbOMRSMetadataCollection with name: {}", repositoryName);
        parentConnector.setRepositoryName(repositoryName);
        this.xtdbRepositoryConnector = parentConnector;
        setAuditLog(auditLog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTypeDef(String userId,
                           TypeDef newTypeDef) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefKnownException,
            TypeDefConflictException,
            InvalidTypeDefException,
            UserNotAuthorizedException {
        final String methodName = "addTypeDef";
        super.addTypeDef(userId, newTypeDef);
        try {
            TypeDefCache.addTypeDef(newTypeDef);
        } catch (InvalidParameterException e) {
            throw new InvalidTypeDefException(XtdbOMRSErrorCode.INVALID_TYPEDEF.getMessageDefinition(newTypeDef.getName()),
                    this.getClass().getName(), methodName, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAttributeTypeDef(String userId,
                                    AttributeTypeDef newAttributeTypeDef) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefKnownException,
            TypeDefConflictException,
            InvalidTypeDefException,
            UserNotAuthorizedException {
        super.addAttributeTypeDef(userId, newAttributeTypeDef);
        TypeDefCache.addAttributeTypeDef(newAttributeTypeDef);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeDef updateTypeDef(String userId,
                                 TypeDefPatch typeDefPatch) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException,
            PatchErrorException {
        final String methodName = "updateTypeDef";
        TypeDef updated = super.updateTypeDef(userId, typeDefPatch);
        try {
            TypeDefCache.addTypeDef(updated);
        } catch (InvalidParameterException e) {
            throw new PatchErrorException(XtdbOMRSErrorCode.INVALID_TYPEDEF.getMessageDefinition(updated.getName()),
                    this.getClass().getName(), methodName, e);
        }
        return updated;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteTypeDef(String userId,
                              String obsoleteTypeDefGUID,
                              String obsoleteTypeDefName) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException,
            TypeDefInUseException,
            UserNotAuthorizedException {
        super.deleteTypeDef(userId, obsoleteTypeDefGUID, obsoleteTypeDefName);
        TypeDefCache.removeTypeDef(obsoleteTypeDefGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAttributeTypeDef(String userId,
                                       String obsoleteTypeDefGUID,
                                       String obsoleteTypeDefName) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException,
            TypeDefInUseException,
            UserNotAuthorizedException {
        super.deleteAttributeTypeDef(userId, obsoleteTypeDefGUID, obsoleteTypeDefName);
        TypeDefCache.removeAttributeTypeDef(obsoleteTypeDefGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeDef reIdentifyTypeDef(String userId,
                                     String originalTypeDefGUID,
                                     String originalTypeDefName,
                                     String newTypeDefGUID,
                                     String newTypeDefName) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException,
            UserNotAuthorizedException {
        TypeDef updated = super.reIdentifyTypeDef(userId, originalTypeDefGUID, originalTypeDefName, newTypeDefGUID, newTypeDefName);
        TypeDefCache.addTypeDef(updated);
        TypeDefCache.removeTypeDef(originalTypeDefGUID);
        return updated;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeTypeDef reIdentifyAttributeTypeDef(String userId,
                                                       String originalAttributeTypeDefGUID,
                                                       String originalAttributeTypeDefName,
                                                       String newAttributeTypeDefGUID,
                                                       String newAttributeTypeDefName) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException,
            UserNotAuthorizedException {
        AttributeTypeDef updated = super.reIdentifyAttributeTypeDef(userId, originalAttributeTypeDefGUID, originalAttributeTypeDefName, newAttributeTypeDefGUID, newAttributeTypeDefName);
        TypeDefCache.addAttributeTypeDef(updated);
        TypeDefCache.removeAttributeTypeDef(originalAttributeTypeDefGUID);
        return updated;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail isEntityKnown(String userId,
                                      String guid) throws
            InvalidParameterException,
            RepositoryErrorException {
        final String methodName = "isEntityKnown";
        this.getInstanceParameterValidation(userId, guid, methodName);
        try {
            EntityDetail entity = xtdbRepositoryConnector.getEntity(guid, null, false);
            repositoryValidator.validateEntityFromStore(repositoryName, guid, entity, methodName);
            return entity;
        } catch (EntityNotKnownException e) {
            log.info("Entity with GUID {} does not exist in the repository.", guid);
        } catch (EntityProxyOnlyException e) {
            log.info("Entity with GUID {} exists, but is only a proxy in the repository.", guid);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitySummary getEntitySummary(String userId,
                                          String guid) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {
        final String methodName = "getEntitySummary";
        super.getInstanceParameterValidation(userId, guid, methodName);
        EntitySummary summary = null;
        try {
            summary = xtdbRepositoryConnector.getEntity(guid, null, true);
        } catch (EntityProxyOnlyException e) {
            xtdbRepositoryConnector.logProblem(this.getClass().getName(),
                    methodName,
                    XtdbOMRSAuditCode.UNEXPECTED_RUNTIME_ERROR,
                    e,
                    "exception raised for proxy despite allowing proxies",
                    e.getClass().getName());
        }
        repositoryValidator.validateEntityFromStore(repositoryName, guid, summary, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, summary, methodName);
        return summary;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail getEntityDetail(String userId,
                                        String guid) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            EntityProxyOnlyException {
        return getEntityDetail(userId, guid, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail getEntityDetail(String userId,
                                        String guid,
                                        Date asOfTime) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            EntityProxyOnlyException {
        final String methodName = "getEntityDetail";
        super.getInstanceParameterValidation(userId, guid, methodName);
        EntityDetail entity = xtdbRepositoryConnector.getEntity(guid, asOfTime, false);
        repositoryValidator.validateEntityFromStore(repositoryName, guid, entity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entity, methodName);
        return entity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntityDetail> getEntityDetailHistory(String userId,
                                                     String guid,
                                                     Date fromTime,
                                                     Date toTime,
                                                     int startFromElement,
                                                     int pageSize,
                                                     HistorySequencingOrder sequencingOrder) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {
        final String methodName = "getEntityDetailHistory";
        super.getInstanceHistoryParameterValidation(userId, guid, fromTime, toTime, methodName);
        return xtdbRepositoryConnector.getPreviousVersionsOfEntity(guid, fromTime, toTime, startFromElement, pageSize, sequencingOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Relationship> getRelationshipsForEntity(String userId,
                                                        String entityGUID,
                                                        String relationshipTypeGUID,
                                                        int fromRelationshipElement,
                                                        List<InstanceStatus> limitResultsByStatus,
                                                        Date asOfTime,
                                                        String sequencingProperty,
                                                        SequencingOrder sequencingOrder,
                                                        int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            EntityNotKnownException,
            PagingErrorException,
            UserNotAuthorizedException {

        final String methodName = "getRelationshipsForEntity";
        super.getRelationshipsForEntityParameterValidation(userId, entityGUID, relationshipTypeGUID, fromRelationshipElement, limitResultsByStatus, asOfTime, sequencingProperty, sequencingOrder, pageSize);

        EntitySummary entity = this.getEntitySummary(userId, entityGUID);

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entity, methodName);

        List<Relationship> entityRelationships = xtdbRepositoryConnector.findRelationshipsForEntity(entityGUID,
                relationshipTypeGUID,
                fromRelationshipElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId);

        return entityRelationships == null || entityRelationships.isEmpty() ? null : entityRelationships;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntityDetail> findEntitiesByProperty(String userId,
                                                     String entityTypeGUID,
                                                     InstanceProperties matchProperties,
                                                     MatchCriteria matchCriteria,
                                                     int fromEntityElement,
                                                     List<InstanceStatus> limitResultsByStatus,
                                                     List<String> limitResultsByClassification,
                                                     Date asOfTime,
                                                     String sequencingProperty,
                                                     SequencingOrder sequencingOrder,
                                                     int pageSize) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PropertyErrorException,
            PagingErrorException,
            UserNotAuthorizedException {

        super.findEntitiesByPropertyParameterValidation(userId, entityTypeGUID, matchProperties, matchCriteria, fromEntityElement, limitResultsByStatus, limitResultsByClassification, asOfTime, sequencingProperty, sequencingOrder, pageSize);

        SearchProperties searchProperties = repositoryHelper.getSearchPropertiesFromInstanceProperties(repositoryName, matchProperties, matchCriteria);
        SearchClassifications searchClassifications = repositoryHelper.getSearchClassificationsFromList(limitResultsByClassification);

        return findEntities(userId,
                entityTypeGUID,
                null,
                searchProperties,
                fromEntityElement,
                limitResultsByStatus,
                searchClassifications,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntityDetail> findEntities(String userId,
                                           String entityTypeGUID,
                                           List<String> entitySubtypeGUIDs,
                                           SearchProperties matchProperties,
                                           int fromEntityElement,
                                           List<InstanceStatus> limitResultsByStatus,
                                           SearchClassifications matchClassifications,
                                           Date asOfTime,
                                           String sequencingProperty,
                                           SequencingOrder sequencingOrder,
                                           int pageSize) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PagingErrorException {

        this.findEntitiesParameterValidation(userId, entityTypeGUID, entitySubtypeGUIDs, matchProperties, fromEntityElement, limitResultsByStatus, matchClassifications, asOfTime, sequencingProperty, sequencingOrder, pageSize);
        return xtdbRepositoryConnector.findEntities(entityTypeGUID,
                entitySubtypeGUIDs,
                matchProperties,
                fromEntityElement,
                limitResultsByStatus,
                matchClassifications,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntityDetail> findEntitiesByClassification(String userId,
                                                           String entityTypeGUID,
                                                           String classificationName,
                                                           InstanceProperties matchClassificationProperties,
                                                           MatchCriteria matchCriteria,
                                                           int fromEntityElement,
                                                           List<InstanceStatus> limitResultsByStatus,
                                                           Date asOfTime,
                                                           String sequencingProperty,
                                                           SequencingOrder sequencingOrder,
                                                           int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            ClassificationErrorException,
            PropertyErrorException,
            PagingErrorException,
            UserNotAuthorizedException {

        super.findEntitiesByClassificationParameterValidation(userId, entityTypeGUID, classificationName, matchClassificationProperties, matchCriteria, fromEntityElement, limitResultsByStatus, asOfTime, sequencingProperty, sequencingOrder, pageSize);

        SearchProperties searchProperties = repositoryHelper.getSearchPropertiesFromInstanceProperties(repositoryName, matchClassificationProperties, matchCriteria);
        SearchClassifications searchClassifications = new SearchClassifications();
        List<ClassificationCondition> classificationConditions = new ArrayList<>();
        ClassificationCondition condition = new ClassificationCondition();
        condition.setName(classificationName);
        condition.setMatchProperties(searchProperties);
        classificationConditions.add(condition);
        searchClassifications.setConditions(classificationConditions);

        // Since matchCriteria passed are embedded with properties, the overall matchCriteria should be all (?)
        searchClassifications.setMatchCriteria(MatchCriteria.ALL);

        return findEntities(userId,
                entityTypeGUID,
                null,
                null,
                fromEntityElement,
                limitResultsByStatus,
                searchClassifications,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntityDetail> findEntitiesByPropertyValue(String userId,
                                                          String entityTypeGUID,
                                                          String searchCriteria,
                                                          int fromEntityElement,
                                                          List<InstanceStatus> limitResultsByStatus,
                                                          List<String> limitResultsByClassification,
                                                          Date asOfTime,
                                                          String sequencingProperty,
                                                          SequencingOrder sequencingOrder,
                                                          int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            PropertyErrorException,
            PagingErrorException,
            UserNotAuthorizedException {

        super.findEntitiesByPropertyValueParameterValidation(userId, entityTypeGUID, searchCriteria, fromEntityElement, limitResultsByStatus, limitResultsByClassification, asOfTime, sequencingProperty, sequencingOrder, pageSize);
        SearchClassifications searchClassifications = repositoryHelper.getSearchClassificationsFromList(limitResultsByClassification);
        return xtdbRepositoryConnector.findEntitiesByText(entityTypeGUID,
                searchCriteria,
                fromEntityElement,
                limitResultsByStatus,
                searchClassifications,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship isRelationshipKnown(String userId,
                                            String guid) throws
            InvalidParameterException,
            RepositoryErrorException {
        final String  methodName = "isRelationshipKnown";
        super.getInstanceParameterValidation(userId, guid, methodName);
        return xtdbRepositoryConnector.getRelationship(guid, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship getRelationship(String userId,
                                        String guid) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException {
        final String methodName = "getRelationship";
        this.getInstanceParameterValidation(userId, guid, methodName);
        return getAndValidateRelationship(guid, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship getRelationship(String userId,
                                        String guid,
                                        Date asOfTime) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException {

        final String methodName = "getRelationship";
        this.getInstanceParameterValidation(userId, guid, asOfTime, methodName);
        return getAndValidateRelationship(guid, asOfTime);
    }

    private Relationship getAndValidateRelationship(String guid,
                                                    Date asOfTime) throws
            RepositoryErrorException,
            RelationshipNotKnownException {
        final String methodName = "getAndValidateRelationship";
        Relationship relationship = xtdbRepositoryConnector.getRelationship(guid, asOfTime);
        repositoryValidator.validateRelationshipFromStore(repositoryName, guid, relationship, methodName);
        repositoryValidator.validateRelationshipIsNotDeleted(repositoryName, relationship, methodName);
        return relationship;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Relationship> getRelationshipHistory(String userId,
                                                     String guid,
                                                     Date fromTime,
                                                     Date toTime,
                                                     int startFromElement,
                                                     int pageSize,
                                                     HistorySequencingOrder sequencingOrder) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException {
        final String methodName = "getRelationshipHistory";
        super.getInstanceHistoryParameterValidation(userId, guid, fromTime, toTime, methodName);
        return xtdbRepositoryConnector.getPreviousVersionsOfRelationship(guid, fromTime, toTime, startFromElement, pageSize, sequencingOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Relationship> findRelationships(String userId,
                                                String relationshipTypeGUID,
                                                List<String> relationshipSubtypeGUIDs,
                                                SearchProperties matchProperties,
                                                int fromRelationshipElement,
                                                List<InstanceStatus> limitResultsByStatus,
                                                Date asOfTime,
                                                String sequencingProperty,
                                                SequencingOrder sequencingOrder,
                                                int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            PagingErrorException {

        super.findRelationshipsParameterValidation(userId, relationshipTypeGUID, relationshipSubtypeGUIDs, matchProperties, fromRelationshipElement, limitResultsByStatus, asOfTime, sequencingProperty, sequencingOrder, pageSize);
        return xtdbRepositoryConnector.findRelationships(relationshipTypeGUID,
                relationshipSubtypeGUIDs,
                matchProperties,
                fromRelationshipElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Relationship> findRelationshipsByProperty(String userId,
                                                          String relationshipTypeGUID,
                                                          InstanceProperties matchProperties,
                                                          MatchCriteria matchCriteria,
                                                          int fromRelationshipElement,
                                                          List<InstanceStatus> limitResultsByStatus,
                                                          Date asOfTime,
                                                          String sequencingProperty,
                                                          SequencingOrder sequencingOrder,
                                                          int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            PropertyErrorException,
            PagingErrorException,
            UserNotAuthorizedException {

        super.findRelationshipsByPropertyParameterValidation(userId, relationshipTypeGUID, matchProperties, matchCriteria, fromRelationshipElement, limitResultsByStatus, asOfTime, sequencingProperty, sequencingOrder, pageSize);
        SearchProperties searchProperties = repositoryHelper.getSearchPropertiesFromInstanceProperties(repositoryName, matchProperties, matchCriteria);
        return findRelationships(userId,
                relationshipTypeGUID,
                null,
                searchProperties,
                fromRelationshipElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Relationship> findRelationshipsByPropertyValue(String userId,
                                                               String relationshipTypeGUID,
                                                               String searchCriteria,
                                                               int fromRelationshipElement,
                                                               List<InstanceStatus> limitResultsByStatus,
                                                               Date asOfTime,
                                                               String sequencingProperty,
                                                               SequencingOrder sequencingOrder,
                                                               int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            PropertyErrorException,
            PagingErrorException,
            UserNotAuthorizedException {

        super.findRelationshipsByPropertyValueParameterValidation(userId, relationshipTypeGUID, searchCriteria, fromRelationshipElement, limitResultsByStatus, asOfTime, sequencingProperty, sequencingOrder, pageSize);
        return xtdbRepositoryConnector.findRelationshipsByText(relationshipTypeGUID,
                searchCriteria,
                fromRelationshipElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InstanceGraph getLinkingEntities(String userId,
                                            String startEntityGUID,
                                            String endEntityGUID,
                                            List<InstanceStatus> limitResultsByStatus,
                                            Date asOfTime) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            PropertyErrorException,
            UserNotAuthorizedException {

        final String methodName = "getLinkingEntities";

        this.getLinkingEntitiesParameterValidation(userId,
                startEntityGUID,
                endEntityGUID,
                limitResultsByStatus,
                asOfTime);

        // If the two GUIDs are equal, just return the one entity and nothing else (no relationships)
        if (startEntityGUID.equals(endEntityGUID)) {
            try {
                InstanceGraph one = new InstanceGraph();
                List<EntityDetail> list = new ArrayList<>();
                EntityDetail entity = xtdbRepositoryConnector.getEntity(startEntityGUID, asOfTime, false);
                list.add(entity);
                one.setEntities(list);
                return one;
            } catch (EntityProxyOnlyException e) {
                throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                        startEntityGUID, repositoryName), this.getClass().getName(), methodName, e);
            }
        }

        return xtdbRepositoryConnector.getTraversalsBetweenEntities(startEntityGUID,
                endEntityGUID,
                limitResultsByStatus,
                asOfTime);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InstanceGraph getEntityNeighborhood(String userId,
                                               String entityGUID,
                                               List<String> entityTypeGUIDs,
                                               List<String> relationshipTypeGUIDs,
                                               List<InstanceStatus> limitResultsByStatus,
                                               List<String> limitResultsByClassification,
                                               Date asOfTime,
                                               int level) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException {

        final String methodName = "getEntityNeighborhood";
        final String entityTypeGUIDParameterName = "entityTypeGUIDs";
        final String relationshipTypeGUIDParameterName = "relationshipTypeGUIDs";
        final String limitedResultsByClassificationParameterName = "limitResultsByClassification";
        final String asOfTimeParameter = "asOfTime";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateGUID(repositoryName, Constants.ENTITY_GUID, entityGUID, methodName);
        repositoryValidator.validateAsOfTime(repositoryName, asOfTimeParameter, asOfTime, methodName);

        if (entityTypeGUIDs != null) {
            for (String guid : entityTypeGUIDs) {
                repositoryValidator.validateTypeGUID(repositoryName, entityTypeGUIDParameterName, guid, methodName);
            }
        }

        if (relationshipTypeGUIDs != null) {
            for (String guid : relationshipTypeGUIDs) {
                repositoryValidator.validateTypeGUID(repositoryName, relationshipTypeGUIDParameterName, guid, methodName);
            }
        }

        if (limitResultsByClassification != null) {
            for (String classificationName : limitResultsByClassification) {
                repositoryValidator.validateClassificationName(repositoryName,
                        limitedResultsByClassificationParameterName,
                        classificationName,
                        methodName);
            }
        }

        return xtdbRepositoryConnector.findNeighborhood(entityGUID,
                entityTypeGUIDs,
                relationshipTypeGUIDs,
                limitResultsByStatus,
                limitResultsByClassification,
                asOfTime,
                level,
                true);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public  List<EntityDetail> getRelatedEntities(String userId,
                                                  String startEntityGUID,
                                                  List<String> entityTypeGUIDs,
                                                  int fromEntityElement,
                                                  List<InstanceStatus> limitResultsByStatus,
                                                  List<String> limitResultsByClassification,
                                                  Date asOfTime,
                                                  String sequencingProperty,
                                                  SequencingOrder sequencingOrder,
                                                  int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            EntityNotKnownException,
            PropertyErrorException,
            PagingErrorException,
            UserNotAuthorizedException {

        this.getRelatedEntitiesParameterValidation(userId,
                startEntityGUID,
                entityTypeGUIDs,
                fromEntityElement,
                limitResultsByStatus,
                limitResultsByClassification,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize);

        final String methodName = "getRelatedEntities";
        final String limitedResultsByClassificationParameterName = "limitResultsByClassification";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);
        repositoryValidator.validateUserId(repositoryName, userId, methodName);

        if (limitResultsByClassification != null) {
            for (String classificationName : limitResultsByClassification) {
                repositoryValidator.validateClassificationName(repositoryName,
                        limitedResultsByClassificationParameterName,
                        classificationName,
                        methodName);
            }
        }

        // Retrieve ALL (full depth) neighborhood from the starting entity (not retrieving any relationships)
        InstanceGraph adjacent = xtdbRepositoryConnector.findNeighborhood(startEntityGUID,
                entityTypeGUIDs,
                null,
                limitResultsByStatus,
                limitResultsByClassification,
                asOfTime,
                -1,
                false);

        if (adjacent != null) {
            // ... and then simply limit the entity results according to the sequencing and paging parameters
            return repositoryHelper.formatEntityResults(adjacent.getEntities(), fromEntityElement, sequencingProperty, sequencingOrder, pageSize);
        }
        return null;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail addEntity(String userId,
                                  String entityTypeGUID,
                                  InstanceProperties initialProperties,
                                  List<Classification> initialClassifications,
                                  InstanceStatus initialStatus) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PropertyErrorException,
            ClassificationErrorException,
            StatusNotSupportedException {

        final String methodName = "addEntity";

        // Note that these validations should not confirm required or unique properties: that is the responsibility
        // of the layer above (OMAS)

        TypeDef typeDef = super.addEntityParameterValidation(userId, entityTypeGUID, initialProperties, initialClassifications, initialStatus, methodName);
        EntityDetail newEntity = repositoryHelper.getNewEntity(repositoryName,
                metadataCollectionId,
                InstanceProvenanceType.LOCAL_COHORT,
                userId,
                typeDef.getName(),
                initialProperties,
                initialClassifications);
        newEntity.setMetadataCollectionName(metadataCollectionName);

        if (initialStatus != null) {
            newEntity.setStatus(initialStatus);
        }

        return xtdbRepositoryConnector.createEntity(newEntity);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail addExternalEntity(String userId,
                                          String entityTypeGUID,
                                          String externalSourceGUID,
                                          String externalSourceName,
                                          InstanceProperties initialProperties,
                                          List<Classification> initialClassifications,
                                          InstanceStatus initialStatus) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PropertyErrorException,
            ClassificationErrorException,
            StatusNotSupportedException,
            UserNotAuthorizedException {

        final String methodName = "addExternalEntity";

        TypeDef typeDef = super.addExternalEntityParameterValidation(userId, entityTypeGUID, externalSourceGUID, initialProperties, initialClassifications, initialStatus, methodName);
        EntityDetail newEntity = repositoryHelper.getNewEntity(repositoryName,
                externalSourceGUID,
                InstanceProvenanceType.EXTERNAL_SOURCE,
                userId,
                typeDef.getName(),
                initialProperties,
                initialClassifications);
        newEntity.setMetadataCollectionName(externalSourceName);
        newEntity.setReplicatedBy(metadataCollectionId);

        if (initialStatus != null) {
            newEntity.setStatus(initialStatus);
        }

        return xtdbRepositoryConnector.createEntity(newEntity);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEntityProxy(String userId,
                               EntityProxy entityProxy) throws
            InvalidParameterException,
            RepositoryErrorException {
        super.addEntityProxyParameterValidation(userId, entityProxy);
        AddEntityProxy.transact(xtdbRepositoryConnector, entityProxy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail updateEntityStatus(String userId,
                                           String entityGUID,
                                           InstanceStatus newStatus) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            StatusNotSupportedException {
        final String methodName = "updateEntityStatus";
        this.updateInstanceStatusParameterValidation(userId, entityGUID, newStatus, methodName);
        return UpdateEntityStatus.transact(xtdbRepositoryConnector, userId, entityGUID, newStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail updateEntityProperties(String userId,
                                               String entityGUID,
                                               InstanceProperties properties) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            PropertyErrorException {
        final String methodName = "updateEntityProperties";
        this.updateInstancePropertiesPropertyValidation(userId, entityGUID, properties, methodName);
        return UpdateEntityProperties.transact(xtdbRepositoryConnector, userId, entityGUID, properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail undoEntityUpdate(String userId,
                                         String entityGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {
        final String methodName = "undoEntityUpdate";
        super.manageInstanceParameterValidation(userId, entityGUID, Constants.ENTITY_GUID, methodName);
        return UndoEntityUpdate.transact(xtdbRepositoryConnector, userId, entityGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail deleteEntity(String userId,
                                     String typeDefGUID,
                                     String typeDefName,
                                     String obsoleteEntityGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {
        final String methodName = "deleteEntity";
        final String parameterName = "obsoleteEntityGUID";
        super.manageInstanceParameterValidation(userId, typeDefGUID, typeDefName, obsoleteEntityGUID, parameterName, methodName);
        return DeleteEntity.transact(xtdbRepositoryConnector, userId, obsoleteEntityGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void purgeEntity(String userId,
                            String typeDefGUID,
                            String typeDefName,
                            String deletedEntityGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            EntityNotDeletedException {
        final String methodName = "purgeEntity";
        final String parameterName = "deletedEntityGUID";
        this.manageInstanceParameterValidation(userId, typeDefGUID, typeDefName, deletedEntityGUID, parameterName, methodName);
        PurgeEntity.transactWithValidation(xtdbRepositoryConnector, deletedEntityGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail restoreEntity(String userId,
                                      String deletedEntityGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            EntityNotDeletedException {
        final String methodName = "restoreEntity";
        final String parameterName = "deletedEntityGUID";
        super.manageInstanceParameterValidation(userId, deletedEntityGUID, parameterName, methodName);
        return RestoreEntity.transact(xtdbRepositoryConnector, userId, deletedEntityGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail classifyEntity(String userId,
                                       String entityGUID,
                                       String classificationName,
                                       InstanceProperties classificationProperties) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            ClassificationErrorException,
            PropertyErrorException {

        return classifyEntity(userId,
                entityGUID,
                classificationName,
                null,
                null,
                null,
                null,
                classificationProperties);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail classifyEntity(String userId,
                                       String entityGUID,
                                       String classificationName,
                                       String externalSourceGUID,
                                       String externalSourceName,
                                       ClassificationOrigin classificationOrigin,
                                       String classificationOriginGUID,
                                       InstanceProperties classificationProperties) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            ClassificationErrorException,
            PropertyErrorException {

        final String methodName = "classifyEntity";
        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);
        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateGUID(repositoryName, Constants.ENTITY_GUID, entityGUID, methodName);
        return ClassifyEntity.transact(xtdbRepositoryConnector,
                userId,
                entityGUID,
                classificationName,
                externalSourceGUID,
                externalSourceName,
                classificationOrigin,
                classificationOriginGUID,
                classificationProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail declassifyEntity(String userId,
                                         String entityGUID,
                                         String classificationName) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            ClassificationErrorException {
        final String methodName = "declassifyEntity";
        super.declassifyEntityParameterValidation(userId, entityGUID, classificationName, methodName);
        return DeclassifyEntity.transact(xtdbRepositoryConnector, entityGUID, classificationName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail updateEntityClassification(String userId,
                                                   String entityGUID,
                                                   String classificationName,
                                                   InstanceProperties properties) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            ClassificationErrorException,
            PropertyErrorException {
        final String methodName = "updateEntityClassification";
        super.classifyEntityParameterValidation(userId, entityGUID, classificationName, properties, methodName);
        return UpdateEntityClassification.transact(xtdbRepositoryConnector, userId, entityGUID, classificationName, properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship addRelationship(String userId,
                                        String relationshipTypeGUID,
                                        InstanceProperties initialProperties,
                                        String entityOneGUID,
                                        String entityTwoGUID,
                                        InstanceStatus initialStatus) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PropertyErrorException,
            EntityNotKnownException,
            StatusNotSupportedException,
            UserNotAuthorizedException {

        final String methodName = "addRelationship";

        TypeDef typeDef = super.addRelationshipParameterValidation(userId, relationshipTypeGUID, initialProperties, entityOneGUID, entityTwoGUID, initialStatus, methodName);

        Relationship relationship = repositoryHelper.getNewRelationship(repositoryName,
                metadataCollectionId,
                InstanceProvenanceType.LOCAL_COHORT,
                userId,
                typeDef.getName(),
                initialProperties);
        if (initialStatus != null) {
            relationship.setStatus(initialStatus);
        }

        return addRelationship(relationship, entityOneGUID, entityTwoGUID);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship addExternalRelationship(String userId,
                                                String relationshipTypeGUID,
                                                String externalSourceGUID,
                                                String externalSourceName,
                                                InstanceProperties initialProperties,
                                                String entityOneGUID,
                                                String entityTwoGUID,
                                                InstanceStatus initialStatus) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PropertyErrorException,
            EntityNotKnownException,
            StatusNotSupportedException,
            UserNotAuthorizedException {

        final String methodName = "addExternalRelationship";

        TypeDef typeDef = super.addRelationshipParameterValidation(userId, relationshipTypeGUID, initialProperties, entityOneGUID, entityTwoGUID, initialStatus, methodName);

        Relationship relationship = repositoryHelper.getNewRelationship(repositoryName,
                externalSourceGUID,
                InstanceProvenanceType.EXTERNAL_SOURCE,
                userId,
                typeDef.getName(),
                initialProperties);

        relationship.setMetadataCollectionName(externalSourceName);
        relationship.setReplicatedBy(metadataCollectionId);
        if (initialStatus != null) {
            relationship.setStatus(initialStatus);
        }

        return addRelationship(relationship, entityOneGUID, entityTwoGUID);

    }

    private Relationship addRelationship(Relationship relationship,
                                         String entityOneGUID,
                                         String entityTwoGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {
        return AddRelationship.transact(xtdbRepositoryConnector, relationship, entityOneGUID, entityTwoGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship updateRelationshipStatus(String userId,
                                                 String relationshipGUID,
                                                 InstanceStatus newStatus) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException,
            StatusNotSupportedException {
        final String methodName = "updateRelationshipStatus";
        this.updateInstanceStatusParameterValidation(userId, relationshipGUID, newStatus, methodName);
        return UpdateRelationshipStatus.transact(xtdbRepositoryConnector, userId, relationshipGUID, newStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship updateRelationshipProperties(String userId,
                                                     String relationshipGUID,
                                                     InstanceProperties properties) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException,
            PropertyErrorException {
        final String methodName = "updateRelationshipProperties";
        this.updateInstancePropertiesPropertyValidation(userId, relationshipGUID, properties, methodName);
        return UpdateRelationshipProperties.transact(xtdbRepositoryConnector, userId, relationshipGUID, properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship undoRelationshipUpdate(String userId,
                                               String relationshipGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException {
        final String methodName = "undoRelationshipUpdate";
        this.manageInstanceParameterValidation(userId, relationshipGUID, Constants.RELATIONSHIP_GUID, methodName);
        return UndoRelationshipUpdate.transact(xtdbRepositoryConnector, userId, relationshipGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship deleteRelationship(String userId,
                                           String typeDefGUID,
                                           String typeDefName,
                                           String obsoleteRelationshipGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException {
        final String methodName = "deleteRelationship";
        final String parameterName = "obsoleteRelationshipGUID";
        this.manageInstanceParameterValidation(userId, typeDefGUID, typeDefName, obsoleteRelationshipGUID, parameterName, methodName);
        return DeleteRelationship.transact(xtdbRepositoryConnector, userId, obsoleteRelationshipGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void purgeRelationship(String userId,
                                  String typeDefGUID,
                                  String typeDefName,
                                  String deletedRelationshipGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException,
            RelationshipNotDeletedException {
        final String methodName = "purgeRelationship";
        final String parameterName = "deletedRelationshipGUID";
        this.manageInstanceParameterValidation(userId, typeDefGUID, typeDefName, deletedRelationshipGUID, parameterName, methodName);
        PurgeRelationship.transactWithValidation(xtdbRepositoryConnector, deletedRelationshipGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship restoreRelationship(String userId,
                                            String deletedRelationshipGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException,
            RelationshipNotDeletedException {
        final String methodName = "restoreRelationship";
        final String parameterName = "deletedRelationshipGUID";
        this.manageInstanceParameterValidation(userId, deletedRelationshipGUID, parameterName, methodName);
        return RestoreRelationship.transact(xtdbRepositoryConnector, userId, deletedRelationshipGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail reIdentifyEntity(String userId,
                                         String typeDefGUID,
                                         String typeDefName,
                                         String entityGUID,
                                         String newEntityGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            UserNotAuthorizedException {
        final String methodName = "reIdentifyEntity";
        final String newInstanceParameterName = "newEntityGUID";
        this.reIdentifyInstanceParameterValidation(userId, typeDefGUID, typeDefName, entityGUID, Constants.ENTITY_GUID, newEntityGUID, newInstanceParameterName, methodName);
        return ReIdentifyEntity.transact(xtdbRepositoryConnector, userId, entityGUID, newEntityGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail reTypeEntity(String userId,
                                     String entityGUID,
                                     TypeDefSummary currentTypeDefSummary,
                                     TypeDefSummary newTypeDefSummary) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PropertyErrorException,
            ClassificationErrorException,
            EntityNotKnownException {
        final String methodName = "reTypeEntity";
        super.reTypeInstanceParameterValidation(userId, entityGUID, Constants.ENTITY_GUID, TypeDefCategory.ENTITY_DEF, currentTypeDefSummary, newTypeDefSummary, methodName);
        return ReTypeEntity.transact(xtdbRepositoryConnector, userId, entityGUID, newTypeDefSummary);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail reHomeEntity(String userId,
                                     String entityGUID,
                                     String typeDefGUID,
                                     String typeDefName,
                                     String homeMetadataCollectionId,
                                     String newHomeMetadataCollectionId,
                                     String newHomeMetadataCollectionName) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {

        final String methodName = "reHomeEntity";

        super.reHomeInstanceParameterValidation(userId, entityGUID, Constants.ENTITY_GUID, typeDefGUID, typeDefName, homeMetadataCollectionId, newHomeMetadataCollectionId, methodName);

        EntityDetail entity;
        try {
            entity = xtdbRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityCanBeRehomed(repositoryName, metadataCollectionId, entity, methodName);

        EntityDetail updatedEntity = new EntityDetail(entity);
        updatedEntity.setMetadataCollectionId(newHomeMetadataCollectionId);
        updatedEntity.setMetadataCollectionName(newHomeMetadataCollectionName);
        updatedEntity.setInstanceProvenanceType(InstanceProvenanceType.LOCAL_COHORT);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return xtdbRepositoryConnector.updateEntity(updatedEntity);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship reIdentifyRelationship(String userId,
                                               String typeDefGUID,
                                               String typeDefName,
                                               String relationshipGUID,
                                               String newRelationshipGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException,
            UserNotAuthorizedException {
        final String methodName = "reIdentifyRelationship";
        final String newInstanceParameterName = "newRelationshipGUID";
        this.reIdentifyInstanceParameterValidation(userId, typeDefGUID, typeDefName, relationshipGUID, Constants.RELATIONSHIP_GUID, newRelationshipGUID, newInstanceParameterName, methodName);
        return ReIdentifyRelationship.transact(xtdbRepositoryConnector, userId, relationshipGUID, newRelationshipGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship reTypeRelationship(String userId,
                                           String relationshipGUID,
                                           TypeDefSummary currentTypeDefSummary,
                                           TypeDefSummary newTypeDefSummary) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PropertyErrorException,
            RelationshipNotKnownException {
        final String methodName = "reTypeRelationship";
        super.reTypeInstanceParameterValidation(userId, relationshipGUID, Constants.RELATIONSHIP_GUID, TypeDefCategory.RELATIONSHIP_DEF, currentTypeDefSummary, newTypeDefSummary, methodName);
        return ReTypeRelationship.transact(xtdbRepositoryConnector, userId, relationshipGUID, newTypeDefSummary);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship reHomeRelationship(String userId,
                                           String relationshipGUID,
                                           String typeDefGUID,
                                           String typeDefName,
                                           String homeMetadataCollectionId,
                                           String newHomeMetadataCollectionId,
                                           String newHomeMetadataCollectionName) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException {

        final String methodName = "reHomeRelationship";
        final String guidParameterName = "typeDefGUID";
        final String nameParameterName = "typeDefName";
        final String newHomeParameterName = "newHomeMetadataCollectionId";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateGUID(repositoryName, Constants.RELATIONSHIP_GUID, relationshipGUID, methodName);
        repositoryValidator.validateTypeDefIds(repositoryName, guidParameterName, nameParameterName, typeDefGUID, typeDefName, methodName);
        repositoryValidator.validateHomeMetadataGUID(repositoryName, Constants.HOME_METADATA_COLLECTION_ID, homeMetadataCollectionId, methodName);
        repositoryValidator.validateHomeMetadataGUID(repositoryName, newHomeParameterName, newHomeMetadataCollectionId, methodName);

        Relationship relationship = this.getRelationship(userId, relationshipGUID);

        repositoryValidator.validateRelationshipCanBeRehomed(repositoryName, metadataCollectionId, relationship, methodName);

        Relationship updatedRelationship = new Relationship(relationship);
        updatedRelationship.setMetadataCollectionId(newHomeMetadataCollectionId);
        updatedRelationship.setMetadataCollectionName(newHomeMetadataCollectionName);
        updatedRelationship.setInstanceProvenanceType(InstanceProvenanceType.LOCAL_COHORT);
        updatedRelationship = repositoryHelper.incrementVersion(userId, relationship, updatedRelationship);

        return xtdbRepositoryConnector.updateRelationship(updatedRelationship);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveEntityReferenceCopy(String userId,
                                        EntityDetail entity) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityConflictException {

        final String methodName = "saveEntityReferenceCopy";
        final String instanceParameterName = "entity";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);
        repositoryValidator.validateReferenceInstanceHeader(repositoryName, metadataCollectionId, instanceParameterName, entity, methodName);

        // Note: the validation immediately above will throw an InvalidParameterException if this is a non-reference copy,
        // so HomeEntityException will never actually be thrown
        xtdbRepositoryConnector.saveReferenceCopy(entity);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Classification> getHomeClassifications(String userId,
                                                       String entityGUID) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {

        final String methodName = "getHomeClassifications";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateGUID(repositoryName, Constants.ENTITY_GUID, entityGUID, methodName);

        EntitySummary retrievedEntity = null;
        try {
            retrievedEntity = xtdbRepositoryConnector.getEntity(entityGUID, null, true);
        } catch (EntityProxyOnlyException e) {
            xtdbRepositoryConnector.logProblem(this.getClass().getName(),
                    methodName,
                    XtdbOMRSAuditCode.UNEXPECTED_RUNTIME_ERROR,
                    e,
                    "exception raised for proxy despite allowing proxies",
                    e.getClass().getName());
        }

        List<Classification> homeClassifications = new ArrayList<>();

        if (retrievedEntity != null) {
            List<Classification> retrievedClassifications = retrievedEntity.getClassifications();
            if (retrievedClassifications != null) {
                for (Classification retrievedClassification : retrievedClassifications) {
                    if (retrievedClassification != null) {
                        if (metadataCollectionId.equals(retrievedClassification.getMetadataCollectionId())) {
                            homeClassifications.add(retrievedClassification);
                        }
                    }
                }
            }
        } else {
            super.reportEntityNotKnown(entityGUID, methodName);
        }

        return homeClassifications.isEmpty() ? null : homeClassifications;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void purgeEntityReferenceCopy(String userId,
                                         String entityGUID,
                                         String typeDefGUID,
                                         String typeDefName,
                                         String homeMetadataCollectionId) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException,
            UserNotAuthorizedException {
        final String methodName = "purgeEntityReferenceCopy";
        this.manageReferenceInstanceParameterValidation(userId, entityGUID, typeDefGUID, typeDefName, Constants.ENTITY_GUID, homeMetadataCollectionId, Constants.HOME_METADATA_COLLECTION_ID, methodName);
        PurgeEntity.transactWithoutValidation(xtdbRepositoryConnector, entityGUID);
    }

    /**
     * Ensure that the entity to be classified already exists, or is a reference copy.
     * @param entity to be validated
     * @param methodName of reference copy action
     * @return EntityDetail to be handled as a reference copy
     * @throws RepositoryErrorException there is a problem communicating with the metadata repository where
     *                                  the metadata collection is stored.
     */
    private EntityDetail validateEntityToClassify(EntityDetail entity,
                                                  String methodName) throws
            RepositoryErrorException {

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        EntityDetail retrievedEntity = null;
        try {
            retrievedEntity = xtdbRepositoryConnector.getEntity(entity.getGUID(), null, false);
        } catch (EntityProxyOnlyException e) {
            log.debug("Entity with GUID {} only a proxy, continuing...", entity.getGUID());
        }

        if ((retrievedEntity == null) && (!metadataCollectionId.equals(entity.getMetadataCollectionId()))) {
            // If the entity is a reference copy then it can be stored in the repository.
            retrievedEntity = entity;
        }

        return retrievedEntity;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveClassificationReferenceCopy(String userId,
                                                EntityDetail entity,
                                                Classification classification) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            EntityConflictException,
            PropertyErrorException {

        final String methodName = "saveClassificationReferenceCopy";
        final String classificationParameterName = "classification";
        final String propertiesParameterName = "classification.getProperties()";

        EntityDetail retrievedEntity = validateEntityToClassify(entity, methodName);
        if (retrievedEntity != null) {
            try {
                repositoryValidator.validateEntityFromStore(repositoryName, entity.getGUID(), retrievedEntity, methodName);
                repositoryValidator.validateEntityIsNotDeleted(repositoryName, retrievedEntity, methodName);
                repositoryValidator.validateInstanceType(repositoryName, entity);

                InstanceType entityType = entity.getType();

                repositoryValidator.validateClassification(repositoryName, classificationParameterName, classification.getName(), entityType.getTypeDefName(), methodName);
                repositoryValidator.validateClassificationProperties(repositoryName, classification.getName(), propertiesParameterName, classification.getProperties(), methodName);

                EntityDetail updatedEntity = repositoryHelper.addClassificationToEntity(repositoryName,
                        retrievedEntity,
                        classification,
                        methodName);

                if (metadataCollectionId.equals(entity.getMetadataCollectionId())) {
                    xtdbRepositoryConnector.updateEntity(updatedEntity);
                } else {
                    xtdbRepositoryConnector.saveReferenceCopy(updatedEntity);
                }
            } catch (EntityNotKnownException e) {
                // Ignore since the entity has been removed since the classification was added
                log.debug("Entity with GUID {} was not known, so classification not stored.", entity.getGUID());
            } catch (ClassificationErrorException e) {
                throw new TypeErrorException(e);
            }
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void purgeClassificationReferenceCopy(String userId,
                                                 EntityDetail entity,
                                                 Classification classification) throws
            EntityConflictException,
            RepositoryErrorException {

        final String methodName = "purgeClassificationReferenceCopy";

        EntityDetail retrievedEntity = validateEntityToClassify(entity, methodName);
        if (retrievedEntity != null) {
            try {
                EntityDetail updatedEntity = repositoryHelper.deleteClassificationFromEntity(repositoryName,
                        entity,
                        classification.getName(),
                        methodName);

                if (metadataCollectionId.equals(entity.getMetadataCollectionId())) {
                    updatedEntity = repositoryHelper.incrementVersion(userId, retrievedEntity, updatedEntity);
                    xtdbRepositoryConnector.updateEntity(updatedEntity);
                } else {
                    xtdbRepositoryConnector.saveReferenceCopy(entity);
                }

            } catch (ClassificationErrorException e) {
                // Do nothing: this simply means the repository did not have the classification reference copy stored
                // anyway, so nothing to remove (no-op)
                log.debug("Entity with GUID {} had no classification {}, nothing to purge.", entity.getGUID(), classification.getName());
            }
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveRelationshipReferenceCopy(String userId,
                                              Relationship relationship) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipConflictException,
            UserNotAuthorizedException {

        final String methodName = "saveRelationshipReferenceCopy";
        final String instanceParameterName = "relationship";

        super.referenceInstanceParameterValidation(userId, relationship, instanceParameterName, methodName);

        // Note: we will first check whether each proxy already exists prior to forcing its creation (as the creation
        // will force the 'proxy' flag to true, even if the entity already exists (which we do not want to happen)
        EntityDetail one = null;
        try {
            one = getEntityDetail(userId, relationship.getEntityOneProxy().getGUID());
            log.debug("EntityProxyOne is already known and not a proxy, will not overwrite it.");
        } catch (EntityNotKnownException | EntityProxyOnlyException e) {
            log.debug("EntityProxyOne is either not known or only a proxy, will overwrite it.");
        }
        EntityDetail two = null;
        try {
            two = getEntityDetail(userId, relationship.getEntityTwoProxy().getGUID());
            log.debug("EntityProxyTwo is already known and not a proxy, will not overwrite it.");
        } catch (EntityNotKnownException | EntityProxyOnlyException e) {
            log.debug("EntityProxyTwo is either not known or only a proxy, will overwrite it.");
        }

        // Only create entity proxies if the above retrievals indicated that they do not yet exist
        Transaction.Builder tx = Transaction.builder();
        if (one == null) {
            xtdbRepositoryConnector.addCreateEntityProxyStatements(tx, relationship.getEntityOneProxy());
        }
        if (two == null) {
            xtdbRepositoryConnector.addCreateEntityProxyStatements(tx, relationship.getEntityTwoProxy());
        }
        xtdbRepositoryConnector.addSaveReferenceCopyStatements(tx, relationship);
        xtdbRepositoryConnector.runTx(tx.build());

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void purgeRelationshipReferenceCopy(String userId,
                                               String relationshipGUID,
                                               String typeDefGUID,
                                               String typeDefName,
                                               String homeMetadataCollectionId) throws
            InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException,
            UserNotAuthorizedException {
        final String methodName = "purgeRelationshipReferenceCopy";
        this.manageReferenceInstanceParameterValidation(userId, relationshipGUID, typeDefGUID, typeDefName, Constants.RELATIONSHIP_GUID, homeMetadataCollectionId, Constants.HOME_METADATA_COLLECTION_ID, methodName);
        PurgeRelationship.transactWithoutValidation(xtdbRepositoryConnector, relationshipGUID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected EntityProxy getEntityProxy(String userId,
                                         String entityGUID,
                                         String methodName) throws EntityNotKnownException {
        EntityProxy entityProxy = xtdbRepositoryConnector.getEntityProxy(entityGUID);
        if (entityProxy == null) {
            reportEntityNotKnown(entityGUID, methodName);
        }
        return entityProxy;
    }

}
