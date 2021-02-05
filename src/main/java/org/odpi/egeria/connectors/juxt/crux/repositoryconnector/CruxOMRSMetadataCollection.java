/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.repositoryconnector;

import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSErrorCode;
import org.odpi.openmetadata.frameworks.auditlog.AuditLog;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSDynamicTypeMetadataCollectionBase;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.MatchCriteria;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.ClassificationCondition;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchClassifications;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefSummary;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryValidator;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Provides the OMRSMetadataCollection implementation for Crux.
 */
public class CruxOMRSMetadataCollection extends OMRSDynamicTypeMetadataCollectionBase {

    private static final Logger log = LoggerFactory.getLogger(CruxOMRSMetadataCollection.class);

    private static final int CASCADE_DELETES_PAGE_SIZE = 10000;

    private final CruxOMRSRepositoryConnector cruxRepositoryConnector;

    /**
     * Default constructor
     *
     * @param parentConnector      connector that this metadata collection supports.
     *                             The connector has the information to call the metadata repository.
     * @param repositoryName       name of this repository.
     * @param repositoryHelper     helper that provides methods to repository connectors and repository event mappers
     *                             to build valid type definitions (TypeDefs), entities and relationships.
     * @param repositoryValidator  validator class for checking open metadata repository objects and parameters
     * @param metadataCollectionId unique identifier for the repository
     * @param auditLog             logging destination
     */
    public CruxOMRSMetadataCollection(CruxOMRSRepositoryConnector parentConnector,
                                      String repositoryName,
                                      OMRSRepositoryHelper repositoryHelper,
                                      OMRSRepositoryValidator repositoryValidator,
                                      String metadataCollectionId,
                                      AuditLog auditLog) {
        super(parentConnector, repositoryName, repositoryHelper, repositoryValidator, metadataCollectionId);
        log.debug("Constructing CruxOMRSMetadataCollection with name: {}", repositoryName);
        parentConnector.setRepositoryName(repositoryName);
        this.cruxRepositoryConnector = parentConnector;
        setAuditLog(auditLog);
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
            EntityDetail entity = cruxRepositoryConnector.getEntity(guid, null, false);
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
            summary = cruxRepositoryConnector.getEntity(guid, null, true);
        } catch (EntityProxyOnlyException e) {
            log.error("Caught exception that should never be thrown given 'true' on acceptProxies.", e);
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
        EntityDetail entity = cruxRepositoryConnector.getEntity(guid, asOfTime, false);
        repositoryValidator.validateEntityFromStore(repositoryName, guid, entity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entity, methodName);
        return entity;
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

        List<Relationship> entityRelationships = cruxRepositoryConnector.findRelationshipsForEntity(entityGUID,
                relationshipTypeGUID,
                fromRelationshipElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize);

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

        // TODO: rework this a bit, so that we retrieve the list of GUIDs and then loop through this
        //  list making calls to getEntityDetail here in the MetadataCollection (?) (Then all of its additional
        //  validations will be applied to each entity as well...)

        return cruxRepositoryConnector.findEntities(entityTypeGUID,
                entitySubtypeGUIDs,
                matchProperties,
                fromEntityElement,
                limitResultsByStatus,
                matchClassifications,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize);

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

        // Since matchCriteria passed are embedded with properties, the overall matchCriteria should be to all (?)
        searchClassifications.setMatchCriteria(MatchCriteria.ALL);

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
            FunctionNotSupportedException,
            UserNotAuthorizedException {

        super.findEntitiesByPropertyValueParameterValidation(userId, entityTypeGUID, searchCriteria, fromEntityElement, limitResultsByStatus, limitResultsByClassification, asOfTime, sequencingProperty, sequencingOrder, pageSize);

        SearchClassifications searchClassifications = repositoryHelper.getSearchClassificationsFromList(limitResultsByClassification);

        return cruxRepositoryConnector.findEntitiesByText(entityTypeGUID,
                searchCriteria,
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
    public Relationship isRelationshipKnown(String userId,
                                            String guid) throws
            InvalidParameterException,
            RepositoryErrorException {
        final String  methodName = "isRelationshipKnown";
        super.getInstanceParameterValidation(userId, guid, methodName);
        return cruxRepositoryConnector.getRelationship(guid, null);
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
        Relationship relationship = cruxRepositoryConnector.getRelationship(guid, asOfTime);
        repositoryValidator.validateRelationshipFromStore(repositoryName, guid, relationship, methodName);
        repositoryValidator.validateRelationshipIsNotDeleted(repositoryName, relationship, methodName);
        return relationship;
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

        // TODO: rework this a bit, so that we retrieve the list of GUIDs and then loop through this
        //  list making calls to getEntityDetail here in the MetadataCollection (?) (Then all of its additional
        //  validations will be applied to each entity as well...)

        return cruxRepositoryConnector.findRelationships(relationshipTypeGUID,
                relationshipSubtypeGUIDs,
                matchProperties,
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
            FunctionNotSupportedException,
            UserNotAuthorizedException {

        super.findRelationshipsByPropertyValueParameterValidation(userId, relationshipTypeGUID, searchCriteria, fromRelationshipElement, limitResultsByStatus, asOfTime, sequencingProperty, sequencingOrder, pageSize);

        return cruxRepositoryConnector.findRelationshipsByText(relationshipTypeGUID,
                searchCriteria,
                fromRelationshipElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize);

    }

    /**
     * {@inheritDoc}

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
        final String entityGUIDParameterName = "entityGUID";
        final String entityTypeGUIDParameterName = "entityTypeGUIDs";
        final String relationshipTypeGUIDParameterName = "relationshipTypeGUIDs";
        final String limitedResultsByClassificationParameterName = "limitResultsByClassification";
        final String asOfTimeParameter = "asOfTime";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateGUID(repositoryName, entityGUIDParameterName, entityGUID, methodName);
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

        // TODO: implement...

        /*
         * Time warp the stores

        Map<String, EntityDetail>   entityStore = repositoryStore.timeWarpEntityStore(asOfTime);
        Map<String, Relationship>   relationshipStore = repositoryStore.timeWarpRelationshipStore(asOfTime);

        InMemoryEntityNeighbourhood inMemoryEntityNeighbourhood = new InMemoryEntityNeighbourhood(repositoryHelper,
                repositoryName,
                repositoryValidator,
                entityStore,
                relationshipStore,
                entityGUID,
                entityTypeGUIDs,
                relationshipTypeGUIDs,
                limitResultsByStatus,
                limitResultsByClassification,
                level);


        return inMemoryEntityNeighbourhood.createInstanceGraph();

        return null;

    }         */

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

        // TODO: at the moment these validations do not appear to be confirming that required properties
        //  (like 'qualifiedName') are actually present in the entity...  Should these not validate for us?
        //  (If not, then why not, and where should this validation be done instead?)

        TypeDef typeDef = super.addEntityParameterValidation(userId, entityTypeGUID, initialProperties, initialClassifications, initialStatus, methodName);

        EntityDetail newEntity = repositoryHelper.getNewEntity(repositoryName,
                metadataCollectionId,
                InstanceProvenanceType.LOCAL_COHORT,
                userId,
                typeDef.getName(),
                initialProperties,
                initialClassifications);

        newEntity.setMetadataCollectionName(metadataCollectionName);
        // TODO: should we not add the calling user to the 'maintainedBy' list?

        if (initialStatus != null) {
            newEntity.setStatus(initialStatus);
        }

        return cruxRepositoryConnector.createEntity(newEntity);

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

        return cruxRepositoryConnector.createEntity(newEntity);

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
        EntityDetail entity = this.isEntityKnown(userId, entityProxy.getGUID());
        if (entity == null) {
            log.debug("Adding a proxy for: {}", entityProxy);
            cruxRepositoryConnector.createEntityProxy(entityProxy);
        }
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
        final String statusParameterName = "newStatus";

        this.updateInstanceStatusParameterValidation(userId, entityGUID, newStatus, methodName);

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entity, methodName);
        repositoryValidator.validateEntityCanBeUpdated(repositoryName, metadataCollectionId, entity, methodName);
        repositoryValidator.validateInstanceType(repositoryName, entity);

        TypeDef typeDef = super.getTypeDefForInstance(entity, methodName);

        repositoryValidator.validateNewStatus(repositoryName, statusParameterName, newStatus, typeDef, methodName);

        EntityDetail updatedEntity = new EntityDetail(entity);
        updatedEntity.setStatus(newStatus);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return cruxRepositoryConnector.updateEntity(updatedEntity);

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
        final String propertiesParameterName = "properties";

        this.updateInstancePropertiesPropertyValidation(userId, entityGUID, properties, methodName);

        EntityDetail entity;
        try {
            // Note that properties only exist on EntityDetail (not EntityProxy), so we will ensure that we have
            // a full entity and not only a proxy
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entity, methodName);
        repositoryValidator.validateEntityCanBeUpdated(repositoryName, metadataCollectionId, entity, methodName);
        repositoryValidator.validateInstanceType(repositoryName, entity);

        TypeDef typeDef = super.getTypeDefForInstance(entity, methodName);

        repositoryValidator.validateNewPropertiesForType(repositoryName, propertiesParameterName, typeDef, properties, methodName);

        EntityDetail updatedEntity = new EntityDetail(entity);
        updatedEntity.setProperties(properties);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return cruxRepositoryConnector.updateEntity(updatedEntity);

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
        final String parameterName = "entityGUID";

        super.manageInstanceParameterValidation(userId, entityGUID, parameterName, methodName);

        EntityDetail restoredEntity = cruxRepositoryConnector.restorePreviousVersionOfEntity(userId, entityGUID);

        // Note: these validations occur _after_ actually persisting the restoration (above)
        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, restoredEntity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, restoredEntity, methodName);

        return restoredEntity;

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

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(obsoleteEntityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            // Note that this should never be thrown here due to the 'true' on acceptProxies above
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    obsoleteEntityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, obsoleteEntityGUID, entity, methodName);
        repositoryValidator.validateTypeForInstanceDelete(repositoryName, typeDefGUID, typeDefName, entity, methodName);
        repositoryValidator.validateInstanceStatusForDelete(repositoryName, entity, methodName);

        try {
            List<Relationship> relationships = this.getRelationshipsForEntity(userId,
                    obsoleteEntityGUID,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    CASCADE_DELETES_PAGE_SIZE);
            if (relationships != null) {
                for (Relationship relationship : relationships) {
                    if (relationship != null) {
                        InstanceType type = relationship.getType();
                        if (type != null) {
                            this.deleteRelationship(userId,
                                    type.getTypeDefGUID(),
                                    type.getTypeDefName(),
                                    relationship.getGUID());
                        }
                    }
                }
            }
        } catch (Exception e) {
            auditLog.logException(methodName, CruxOMRSAuditCode.FAILED_RELATIONSHIP_DELETE_CASCADE.getMessageDefinition(obsoleteEntityGUID), e);
        }

        EntityDetail updatedEntity = new EntityDetail(entity);
        updatedEntity.setStatusOnDelete(entity.getStatus());
        updatedEntity.setStatus(InstanceStatus.DELETED);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return cruxRepositoryConnector.updateEntity(updatedEntity);

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

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(deletedEntityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    deletedEntityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, deletedEntityGUID, entity, methodName);
        repositoryValidator.validateTypeForInstanceDelete(repositoryName, typeDefGUID, typeDefName, entity, methodName);
        repositoryValidator.validateEntityIsDeleted(repositoryName, entity, methodName);

        // TODO: the InMemoryOMRSMetadataCollection makes this call and catches all exceptions (with no logging), but
        //  actually the call itself will ALWAYS give an EntityNotKnownException because we are validating just above
        //  that this entity is deleted, while the getRelationshipsForEntity call is validating that the entity is NOT
        //  deleted (and if it is, will throw an EntityNotKnownException). So it ALWAYS throws an EntityNotKnownException.
        //  We can therefore either remove this code entirely (as it does nothing but add Exception processing overhead),
        //  or we need to change the logic across the various repositories.
        /*try {
            List<Relationship> relationships = this.getRelationshipsForEntity(userId,
                    deletedEntityGUID,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    CASCADE_DELETES_PAGE_SIZE);
            if (relationships != null) {
                for (Relationship relationship : relationships) {
                    if (relationship != null) {
                        // Note: we will not call this.purgeRelationship() directly to avoid re-retrieving all of these
                        // relationships one-by-one prior to purging
                        cruxRepositoryConnector.purgeRelationship(relationship.getGUID());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Exception was thrown in purgeEntity.", e);
            auditLog.logException(methodName, CruxOMRSAuditCode.FAILED_RELATIONSHIP_DELETE_CASCADE.getMessageDefinition(deletedEntityGUID), e);
        }*/

        cruxRepositoryConnector.purgeEntity(entity.getGUID());

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

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(deletedEntityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    deletedEntityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, deletedEntityGUID, entity, methodName);
        repositoryValidator.validateEntityIsDeleted(repositoryName, entity, methodName);

        EntityDetail restoredEntity = new EntityDetail(entity);
        restoredEntity.setStatus(entity.getStatusOnDelete());
        restoredEntity.setStatusOnDelete(null);
        restoredEntity = repositoryHelper.incrementVersion(userId, entity, restoredEntity);

        return cruxRepositoryConnector.updateEntity(restoredEntity);

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
        final String entityGUIDParameterName = "entityGUID";
        final String classificationParameterName = "classificationName";
        final String propertiesParameterName = "classificationProperties";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateGUID(repositoryName, entityGUIDParameterName, entityGUID, methodName);

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entity, methodName);
        repositoryValidator.validateInstanceType(repositoryName, entity);

        InstanceType entityType = entity.getType();

        repositoryValidator.validateClassification(repositoryName, classificationParameterName, classificationName, entityType.getTypeDefName(), methodName);

        Classification newClassification;
        try {
            repositoryValidator.validateClassificationProperties(repositoryName, classificationName, propertiesParameterName, classificationProperties, methodName);
            if (externalSourceGUID == null) {
                newClassification = repositoryHelper.getNewClassification(repositoryName,
                        null,
                        InstanceProvenanceType.LOCAL_COHORT,
                        userId,
                        classificationName,
                        entityType.getTypeDefName(),
                        classificationOrigin == null ? ClassificationOrigin.ASSIGNED : classificationOrigin,
                        classificationOriginGUID,
                        classificationProperties);
            } else {
                newClassification = repositoryHelper.getNewClassification(repositoryName,
                        externalSourceGUID,
                        InstanceProvenanceType.EXTERNAL_SOURCE,
                        userId,
                        classificationName,
                        entityType.getTypeDefName(),
                        classificationOrigin == null ? ClassificationOrigin.ASSIGNED : classificationOrigin,
                        classificationOriginGUID,
                        classificationProperties);
                newClassification.setMetadataCollectionName(externalSourceName);
                newClassification.setReplicatedBy(metadataCollectionId);
            }
        } catch (TypeErrorException e) {
            throw new ClassificationErrorException(CruxOMRSErrorCode.INVALID_CLASSIFICATION_FOR_ENTITY.getMessageDefinition(
                    repositoryName, classificationName, entityType.getTypeDefName()), this.getClass().getName(), methodName, e);
        }

        EntityDetail updatedEntity = repositoryHelper.addClassificationToEntity(repositoryName, entity, newClassification, methodName);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return cruxRepositoryConnector.updateEntity(updatedEntity);

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

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entity, methodName);

        EntityDetail updatedEntity = repositoryHelper.deleteClassificationFromEntity(repositoryName, entity, classificationName, methodName);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return cruxRepositoryConnector.updateEntity(updatedEntity);

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

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entity, methodName);

        Classification classification = repositoryHelper.getClassificationFromEntity(repositoryName, entity, classificationName, methodName);
        Classification newClassification = new Classification(classification);
        newClassification.setProperties(properties);
        repositoryHelper.incrementVersion(userId, classification, newClassification);

        EntityDetail updatedEntity = repositoryHelper.updateClassificationInEntity(repositoryName,
                userId,
                entity,
                newClassification,
                methodName);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return cruxRepositoryConnector.updateEntity(updatedEntity);

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

        return addRelationship(userId, relationship, typeDef, entityOneGUID, entityTwoGUID, initialStatus);

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

        return addRelationship(userId, relationship, typeDef, entityOneGUID, entityTwoGUID, initialStatus);

    }

    private Relationship addRelationship(String userId,
                                         Relationship relationship,
                                         TypeDef typeDef,
                                         String entityOneGUID,
                                         String entityTwoGUID,
                                         InstanceStatus initialStatus) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {

        final String methodName = "addRelationship";

        EntityProxy entityOneProxy = getEntityProxy(userId, entityOneGUID, methodName);
        repositoryValidator.validateEntityFromStore(repositoryName, entityOneGUID, entityOneProxy, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entityOneProxy, methodName);

        EntityProxy entityTwoProxy = getEntityProxy(userId, entityTwoGUID, methodName);
        repositoryValidator.validateEntityFromStore(repositoryName, entityTwoGUID, entityTwoProxy, methodName);
        repositoryValidator.validateEntityIsNotDeleted(repositoryName, entityTwoProxy, methodName);

        repositoryValidator.validateRelationshipEnds(repositoryName, entityOneProxy, entityTwoProxy, typeDef, methodName);

        relationship.setEntityOneProxy(entityOneProxy);
        relationship.setEntityTwoProxy(entityTwoProxy);

        if (initialStatus != null) {
            relationship.setStatus(initialStatus);
        }

        return cruxRepositoryConnector.createRelationship(relationship);

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
        final String statusParameterName = "newStatus";

        this.updateInstanceStatusParameterValidation(userId, relationshipGUID, newStatus, methodName);

        Relationship relationship = this.getRelationship(userId, relationshipGUID);
        repositoryValidator.validateRelationshipCanBeUpdated(repositoryName, metadataCollectionId, relationship, methodName);
        repositoryValidator.validateInstanceType(repositoryName, relationship);

        TypeDef typeDef = super.getTypeDefForInstance(relationship, methodName);

        repositoryValidator.validateNewStatus(repositoryName, statusParameterName, newStatus, typeDef, methodName);

        Relationship updatedRelationship = new Relationship(relationship);
        updatedRelationship.setStatus(newStatus);
        updatedRelationship = repositoryHelper.incrementVersion(userId, relationship, updatedRelationship);

        return cruxRepositoryConnector.updateRelationship(updatedRelationship);

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
        final String propertiesParameterName = "properties";

        this.updateInstancePropertiesPropertyValidation(userId, relationshipGUID, properties, methodName);

        Relationship relationship = this.getRelationship(userId, relationshipGUID);

        repositoryValidator.validateRelationshipFromStore(repositoryName, relationshipGUID, relationship, methodName);
        repositoryValidator.validateRelationshipIsNotDeleted(repositoryName, relationship, methodName);
        repositoryValidator.validateRelationshipCanBeUpdated(repositoryName, metadataCollectionId, relationship, methodName);
        repositoryValidator.validateInstanceType(repositoryName, relationship);

        TypeDef typeDef = super.getTypeDefForInstance(relationship, methodName);

        repositoryValidator.validateNewPropertiesForType(repositoryName, propertiesParameterName, typeDef, properties, methodName);

        Relationship updatedRelationship = new Relationship(relationship);
        updatedRelationship.setProperties(properties);
        updatedRelationship = repositoryHelper.incrementVersion(userId, relationship, updatedRelationship);

        return cruxRepositoryConnector.updateRelationship(updatedRelationship);

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
        final String parameterName = "relationshipGUID";

        this.manageInstanceParameterValidation(userId, relationshipGUID, parameterName, methodName);

        Relationship restoredRelationship = cruxRepositoryConnector.restorePreviousVersionOfRelationship(userId, relationshipGUID);

        // Note: these validations occur _after_ actually persisting the restoration (above)
        repositoryValidator.validateRelationshipFromStore(repositoryName, relationshipGUID, restoredRelationship, methodName);
        repositoryValidator.validateRelationshipIsNotDeleted(repositoryName, restoredRelationship, methodName);

        return restoredRelationship;

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

        Relationship relationship = this.getRelationship(userId, obsoleteRelationshipGUID);

        repositoryValidator.validateTypeForInstanceDelete(repositoryName, typeDefGUID, typeDefName, relationship, methodName);

        Relationship updatedRelationship = new Relationship(relationship);
        updatedRelationship.setStatusOnDelete(relationship.getStatus());
        updatedRelationship.setStatus(InstanceStatus.DELETED);
        updatedRelationship = repositoryHelper.incrementVersion(userId, relationship, updatedRelationship);

        return cruxRepositoryConnector.updateRelationship(updatedRelationship);

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

        Relationship relationship = cruxRepositoryConnector.getRelationship(deletedRelationshipGUID, null);

        repositoryValidator.validateRelationshipFromStore(repositoryName, deletedRelationshipGUID, relationship, methodName);
        repositoryValidator.validateTypeForInstanceDelete(repositoryName, typeDefGUID, typeDefName, relationship, methodName);
        repositoryValidator.validateRelationshipIsDeleted(repositoryName, relationship, methodName);

        cruxRepositoryConnector.purgeRelationship(relationship.getGUID());

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

        Relationship relationship = cruxRepositoryConnector.getRelationship(deletedRelationshipGUID, null);

        repositoryValidator.validateRelationshipFromStore(repositoryName, deletedRelationshipGUID, relationship, methodName);
        repositoryValidator.validateRelationshipIsDeleted(repositoryName, relationship, methodName);

        Relationship restoredRelationship = new Relationship(relationship);
        restoredRelationship.setStatus(relationship.getStatusOnDelete());
        restoredRelationship.setStatusOnDelete(null);
        restoredRelationship = repositoryHelper.incrementVersion(userId, relationship, restoredRelationship);

        restoredRelationship = cruxRepositoryConnector.updateRelationship(restoredRelationship);

        // Note: these validations are called _after_ the relationship restoration has already been persisted
        repositoryValidator.validateRelationshipFromStore(repositoryName, deletedRelationshipGUID, restoredRelationship, methodName);
        repositoryValidator.validateRelationshipIsNotDeleted(repositoryName, restoredRelationship, methodName);

        return restoredRelationship;

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
        final String instanceParameterName = "entityGUID";
        final String newInstanceParameterName = "newEntityGUID";

        this.reIdentifyInstanceParameterValidation(userId, typeDefGUID, typeDefName, entityGUID, instanceParameterName, newEntityGUID, newInstanceParameterName, methodName);

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityCanBeUpdated(repositoryName, metadataCollectionId, entity, methodName);

        EntityDetail updatedEntity = new EntityDetail(entity);
        updatedEntity.setGUID(newEntityGUID);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        List<List<?>> statements = cruxRepositoryConnector.getPurgeEntityStatements(entity.getGUID());
        statements.addAll(cruxRepositoryConnector.getCreateEntityStatements(updatedEntity));
        cruxRepositoryConnector.runSynchronousTx(statements);

        return updatedEntity;

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
        final String entityParameterName = "entityGUID";
        final String currentTypeDefParameterName = "currentTypeDefSummary";
        final String newTypeDefParameterName = "newTypeDefSummary";

        super.reTypeInstanceParameterValidation(userId, entityGUID, entityParameterName, TypeDefCategory.ENTITY_DEF, currentTypeDefSummary, newTypeDefSummary, methodName);

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityCanBeUpdated(repositoryName, metadataCollectionId, entity, methodName);
        repositoryValidator.validateInstanceType(repositoryName, entity, currentTypeDefParameterName, currentTypeDefParameterName, currentTypeDefSummary.getGUID(), currentTypeDefSummary.getName());
        repositoryValidator.validatePropertiesForType(repositoryName, newTypeDefParameterName, newTypeDefSummary, entity.getProperties(), methodName);
        repositoryValidator.validateClassificationList(repositoryName, entityParameterName, entity.getClassifications(), newTypeDefSummary.getName(), methodName);

        EntityDetail updatedEntity = new EntityDetail(entity);
        InstanceType newInstanceType = repositoryHelper.getNewInstanceType(repositoryName, newTypeDefSummary);
        updatedEntity.setType(newInstanceType);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return cruxRepositoryConnector.updateEntity(updatedEntity);

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
        final String entityParameterName = "entityGUID";

        super.reHomeInstanceParameterValidation(userId, entityGUID, entityParameterName, typeDefGUID, typeDefName, homeMetadataCollectionId, newHomeMetadataCollectionId, methodName);

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        repositoryValidator.validateEntityFromStore(repositoryName, entityGUID, entity, methodName);
        repositoryValidator.validateEntityCanBeRehomed(repositoryName, metadataCollectionId, entity, methodName);

        EntityDetail updatedEntity = new EntityDetail(entity);
        updatedEntity.setMetadataCollectionId(newHomeMetadataCollectionId);
        updatedEntity.setMetadataCollectionName(newHomeMetadataCollectionName);
        updatedEntity.setInstanceProvenanceType(InstanceProvenanceType.LOCAL_COHORT);
        updatedEntity = repositoryHelper.incrementVersion(userId, entity, updatedEntity);

        return cruxRepositoryConnector.updateEntity(updatedEntity);

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
        final String instanceParameterName = "relationshipGUID";
        final String newInstanceParameterName = "newRelationshipGUID";

        this.reIdentifyInstanceParameterValidation(userId, typeDefGUID, typeDefName, relationshipGUID, instanceParameterName, newRelationshipGUID, newInstanceParameterName, methodName);

        Relationship relationship = this.getRelationship(userId, relationshipGUID);

        repositoryValidator.validateRelationshipCanBeUpdated(repositoryName, metadataCollectionId, relationship, methodName);

        Relationship updatedRelationship = new Relationship(relationship);
        updatedRelationship.setGUID(newRelationshipGUID);
        updatedRelationship = repositoryHelper.incrementVersion(userId, relationship, updatedRelationship);

        List<List<?>> statements = cruxRepositoryConnector.getPurgeRelationshipStatements(relationshipGUID);
        statements.addAll(cruxRepositoryConnector.getCreateRelationshipStatements(updatedRelationship));
        cruxRepositoryConnector.runSynchronousTx(statements);

        return updatedRelationship;

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
        final String relationshipParameterName = "relationshipGUID";
        final String currentTypeDefParameterName = "currentTypeDefSummary";
        final String newTypeDefParameterName = "newTypeDefSummary";

        super.reTypeInstanceParameterValidation(userId, relationshipGUID, relationshipParameterName, TypeDefCategory.RELATIONSHIP_DEF, currentTypeDefSummary, newTypeDefSummary, methodName);

        Relationship relationship = this.getRelationship(userId, relationshipGUID);

        repositoryValidator.validateRelationshipCanBeUpdated(repositoryName, metadataCollectionId, relationship, methodName);
        repositoryValidator.validateInstanceType(repositoryName, relationship, currentTypeDefParameterName, currentTypeDefParameterName, currentTypeDefSummary.getGUID(), currentTypeDefSummary.getName());
        repositoryValidator.validatePropertiesForType(repositoryName, newTypeDefParameterName, newTypeDefSummary, relationship.getProperties(), methodName);

        Relationship updatedRelationship = new Relationship(relationship);
        InstanceType newInstanceType = repositoryHelper.getNewInstanceType(repositoryName, newTypeDefSummary);
        updatedRelationship.setType(newInstanceType);
        updatedRelationship = repositoryHelper.incrementVersion(userId, relationship, updatedRelationship);

        return cruxRepositoryConnector.updateRelationship(updatedRelationship);

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
        final String relationshipParameterName = "relationshipGUID";
        final String homeParameterName = "homeMetadataCollectionId";
        final String newHomeParameterName = "newHomeMetadataCollectionId";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateGUID(repositoryName, relationshipParameterName, relationshipGUID, methodName);
        repositoryValidator.validateTypeDefIds(repositoryName, guidParameterName, nameParameterName, typeDefGUID, typeDefName, methodName);
        repositoryValidator.validateHomeMetadataGUID(repositoryName, homeParameterName, homeMetadataCollectionId, methodName);
        repositoryValidator.validateHomeMetadataGUID(repositoryName, newHomeParameterName, newHomeMetadataCollectionId, methodName);

        Relationship relationship = this.getRelationship(userId, relationshipGUID);

        repositoryValidator.validateRelationshipCanBeRehomed(repositoryName, metadataCollectionId, relationship, methodName);

        Relationship updatedRelationship = new Relationship(relationship);
        updatedRelationship.setMetadataCollectionId(newHomeMetadataCollectionId);
        updatedRelationship.setMetadataCollectionName(newHomeMetadataCollectionName);
        updatedRelationship.setInstanceProvenanceType(InstanceProvenanceType.LOCAL_COHORT);
        updatedRelationship = repositoryHelper.incrementVersion(userId, relationship, updatedRelationship);

        return cruxRepositoryConnector.updateRelationship(updatedRelationship);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveEntityReferenceCopy(String userId,
                                        EntityDetail entity) throws
            InvalidParameterException,
            RepositoryErrorException,
            HomeEntityException,
            EntityConflictException {

        final String methodName = "saveEntityReferenceCopy";
        final String instanceParameterName = "entity";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);
        repositoryValidator.validateReferenceInstanceHeader(repositoryName, metadataCollectionId, instanceParameterName, entity, methodName);

        // TODO: would have expected the parameter validation above to do things like HomeEntityException (?)
        if (metadataCollectionId.equals(entity.getMetadataCollectionId())) {
            throw new HomeEntityException(
                    CruxOMRSErrorCode.ENTITY_HOME_COLLECTION_REFERENCE.getMessageDefinition(
                            entity.getGUID(), repositoryName
                    ),
                    this.getClass().getName(),
                    methodName
            );
        } else {
            cruxRepositoryConnector.saveReferenceCopy(entity);
        }

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
        final String guidParameterName = "entityGUID";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateGUID(repositoryName, guidParameterName, entityGUID, methodName);

        EntityDetail retrievedEntity;
        try {
            retrievedEntity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
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
        final String entityParameterName = "entityGUID";
        final String homeParameterName = "homeMetadataCollectionId";

        this.manageReferenceInstanceParameterValidation(userId, entityGUID, typeDefGUID, typeDefName, entityParameterName, homeMetadataCollectionId, homeParameterName, methodName);

        EntityDetail entity;
        try {
            entity = cruxRepositoryConnector.getEntity(entityGUID, null, false);
        } catch (EntityProxyOnlyException e) {
            log.error("Attempted to purgeEntityReferenceCopy on an EntityProxy -- should this actually work?", e);
            throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                    entityGUID, repositoryName), this.getClass().getName(), methodName, e);
        }

        if (entity != null) {
            cruxRepositoryConnector.purgeEntity(entityGUID);
        } else {
            super.reportEntityNotKnown(entityGUID, methodName);
        }

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

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        EntityDetail retrievedEntity = null;
        try {
            retrievedEntity = cruxRepositoryConnector.getEntity(entity.getGUID(), null, false);
        } catch (EntityProxyOnlyException e) {
            log.debug("Entity with GUID {} only a proxy, continuing...", entity.getGUID());
        }

        if ((retrievedEntity == null) && (!metadataCollectionId.equals(entity.getMetadataCollectionId()))) {
             // If the entity is a reference copy then it can be stored in the repository.
            retrievedEntity = entity;
        }

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
                    updatedEntity = repositoryHelper.incrementVersion(userId, retrievedEntity, updatedEntity);
                    cruxRepositoryConnector.updateEntity(updatedEntity);
                } else {
                    cruxRepositoryConnector.saveReferenceCopy(entity);
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
            TypeErrorException,
            EntityConflictException,
            RepositoryErrorException {

        final String methodName = "purgeClassificationReferenceCopy";

        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        EntityDetail retrievedEntity = null;
        try {
            retrievedEntity = cruxRepositoryConnector.getEntity(entity.getGUID(), null, false);
        } catch (EntityProxyOnlyException e) {
            log.debug("Entity with GUID {} only a proxy, continuing...", entity.getGUID());
        }

        if ((retrievedEntity == null) && (!metadataCollectionId.equals(entity.getMetadataCollectionId()))) {
             // If the entity is a reference copy then it can be stored in the repository.
            retrievedEntity = entity;
        }

        if (retrievedEntity != null) {
            try {
                EntityDetail updatedEntity = repositoryHelper.deleteClassificationFromEntity(repositoryName,
                        entity,
                        classification.getName(),
                        methodName);

                if (metadataCollectionId.equals(entity.getMetadataCollectionId())) {
                    updatedEntity = repositoryHelper.incrementVersion(userId, retrievedEntity, updatedEntity);
                    cruxRepositoryConnector.updateEntity(updatedEntity);
                } else {
                    cruxRepositoryConnector.saveReferenceCopy(entity);
                }

            } catch (ClassificationErrorException e) {
                throw new TypeErrorException(e);
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
        // TODO: there may be a more optimal way of achieving this directly through the Crux statements -- future optimisation?
        List<List<?>> statements = new ArrayList<>();
        if (one == null) {
            statements.addAll(cruxRepositoryConnector.getCreateEntityProxyStatements(relationship.getEntityOneProxy()));
        }
        if (two == null) {
            statements.addAll(cruxRepositoryConnector.getCreateEntityProxyStatements(relationship.getEntityTwoProxy()));
        }
        statements.addAll(cruxRepositoryConnector.getSaveReferenceCopyStatements(relationship));
        cruxRepositoryConnector.runSynchronousTx(statements);

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
        final String relationshipParameterName = "relationshipGUID";
        final String homeParameterName = "homeMetadataCollectionId";

        this.manageReferenceInstanceParameterValidation(userId, relationshipGUID, typeDefGUID, typeDefName, relationshipParameterName, homeMetadataCollectionId, homeParameterName, methodName);

        Relationship relationship = cruxRepositoryConnector.getRelationship(relationshipGUID, null);
        if (relationship != null) {
            cruxRepositoryConnector.purgeRelationship(relationshipGUID);
        } else {
            super.reportRelationshipNotKnown(relationshipGUID, methodName);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected EntityProxy getEntityProxy(String userId,
                                         String entityGUID,
                                         String methodName) throws EntityNotKnownException {
        EntityProxy entityProxy = cruxRepositoryConnector.getEntityProxy(entityGUID);
        if (entityProxy == null) {
            reportEntityNotKnown(entityGUID, methodName);
        }
        return entityProxy;
    }

}
