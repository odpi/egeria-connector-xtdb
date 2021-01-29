/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.repositoryconnector;

import clojure.lang.*;
import crux.api.Crux;
import crux.api.ICruxAPI;
import crux.api.ICruxDatasource;
import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.crux.mapping.*;
import org.odpi.egeria.connectors.juxt.crux.model.search.CruxQuery;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.EndpointProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchClassifications;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.ffdc.OMRSErrorCode;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityConflictException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityProxyOnlyException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.OMRSLogicErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RelationshipConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * Provides all connectivity and API-based interaction with a Crux back-end.
 */
public class CruxOMRSRepositoryConnector extends OMRSRepositoryConnector {

    private static final Logger log = LoggerFactory.getLogger(CruxOMRSRepositoryConnector.class);

    // TODO: as this is required, probably best to receive it as a configurable parameter
    private static final Duration timeout = Duration.ofSeconds(30);

    private ICruxAPI cruxAPI = null;

    /**
     * Default constructor used by the OCF Connector Provider.
     */
    public CruxOMRSRepositoryConnector() {
        // nothing to do (yet)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMetadataCollectionId(String metadataCollectionId) {
        final String methodName = "setMetadataCollectionId";
        super.metadataCollectionId = metadataCollectionId;
        if (metadataCollectionId != null) {
            try {
                metadataCollection = new CruxOMRSMetadataCollection(this,
                        repositoryName,
                        repositoryHelper,
                        repositoryValidator,
                        metadataCollectionId);
            } catch (Exception e) {
                throw new OMRSLogicErrorException(OMRSErrorCode.NULL_METADATA_COLLECTION.getMessageDefinition(repositoryName),
                        this.getClass().getName(),
                        methodName,
                        e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws ConnectorCheckedException {

        super.start();
        final String methodName = "start";

        auditLog.logMessage(methodName, CruxOMRSAuditCode.REPOSITORY_SERVICE_STARTING.getMessageDefinition());

        EndpointProperties endpointProperties = connectionProperties.getEndpoint();
        if (endpointProperties == null) {
            // If there is no endpoint defined, then we are creating a local instance of Crux
            // TODO: for now this is an in-memory JVM-embedded node, useful only for experimentation, testing, and
            //  possibly as an in-memory cache. Should we update this to have some minimal local persistent
            //  store as a default configuration (?)
            log.info("Setting up a default in-memory crux node...");
            try {
                cruxAPI = Crux.startNode();
                log.debug(" ... node: {}", cruxAPI);
            } catch (Exception e) {
                throw new ConnectorCheckedException(CruxOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                        this.getClass().getName(), methodName, e);
            }
        } else {
            // TODO: Otherwise, we should setup remote connectivity to a pre-existing Crux instance
            log.error("Remote connectivity is not yet implemented.");
            /*
            String address = endpointProperties.getProtocol() + "://" + endpointProperties.getAddress();

            String igcUser = connectionProperties.getUserId();
            String igcPass = connectionProperties.getClearPassword();

            boolean successfulInit = false;

            auditLog.logMessage(methodName, CruxOMRSAuditCode.CONNECTING_TO_CRUX.getMessageDefinition(address));

            // Create new REST API client (opens a new session)
            try {
                this.igcRestClient = new IGCRestClient(address, igcUser, igcPass);
                if (this.igcRestClient.start()) {
                    if (getMaxPageSize() > 0) {
                        this.igcRestClient.setDefaultPageSize(getMaxPageSize());
                    }
                    // Set the version based on the IGC client's auto-determination of the IGC environment's version
                    this.igcVersion = this.igcRestClient.getIgcVersion();
                    successfulInit = true;
                }
            } catch (Exception e) {
                raiseConnectorCheckedException(CruxOMRSErrorCode.REST_CLIENT_FAILURE, methodName, e, "<null>");
            }

            if (!successfulInit) {
                raiseConnectorCheckedException(CruxOMRSErrorCode.REST_CLIENT_FAILURE, methodName, null, "<null>");
            }

            auditLog.logMessage(methodName, CruxOMRSAuditCode.CONNECTED_TO_CRUX.getMessageDefinition(address));
             */
        }

        auditLog.logMessage(methodName, CruxOMRSAuditCode.REPOSITORY_SERVICE_STARTED.getMessageDefinition(getServerName(), "<null>"));

    }

    /**
     * Free up any resources held since the connector is no longer needed.
     */
    @Override
    public void disconnect() {

        final String methodName = "disconnect";

        // Ready the embedded Crux node for GC
        try {
            this.cruxAPI.close();
        } catch (IOException e) {
            auditLog.logException(methodName, CruxOMRSAuditCode.FAILED_DISCONNECT.getMessageDefinition(), e);
        }
        if (auditLog != null) {
            auditLog.logMessage(methodName, CruxOMRSAuditCode.REPOSITORY_SERVICE_SHUTDOWN.getMessageDefinition(getServerName()));
        }

    }

    /**
     * Create the provided entity instance in the Crux repository.
     * @param entity to create
     */
    public void createEntityProxy(EntityProxy entity) {
        Map<Keyword, ?> results = runSynchronousTx(getCreateEntityProxyStatements(entity));
        log.debug(" ... results: {}", results);
    }

    /**
     * Retrieve the statements that need to be executed against Crux to create (persist) the entity proxy provided.
     * @param entity to create
     * @return {@code List<List<?>>} of statements
     */
    public List<List<?>> getCreateEntityProxyStatements(EntityProxy entity) {
        List<List<?>> list = new ArrayList<>();
        EntityProxyMapping epm = new EntityProxyMapping(this, entity);
        list.add(epm.toCrux());
        return list;
    }

    /**
     * Create the provided entity instance in the Crux repository.
     * @param entity to create
     * @return EntityDetail that was created
     */
    public EntityDetail createEntity(EntityDetail entity) {
        Map<Keyword, ?> results = runSynchronousTx(getCreateEntityStatements(entity));
        log.debug(" ... results: {}", results);
        return entity;
    }

    /**
     * Retrieve the statements that need to be executed against Crux to create (persist) the entity provided.
     * @param entity to be created
     * @return {@code List<List<?>>} of statements
     */
    public List<List<?>> getCreateEntityStatements(EntityDetail entity) {
        List<List<?>> list = new ArrayList<>();
        EntityDetailMapping edm = new EntityDetailMapping(this, entity);
        list.add(edm.toCrux());
        return list;
    }

    /**
     * Permanently delete the entity (and all of its history) from the Crux repository.
     * Note that this operation is NOT reversible!
     * @param guid of the entity to permanently delete
     */
    public void purgeEntity(String guid) {
        runSynchronousTx(getPurgeEntityStatements(guid));
    }

    /**
     * Retrieve the statements that need to be executed against Crux to permanently delete the entity (and all of its
     * history) from the Crux repository.
     * @param guid of the entity to permanently delete
     * @return {@code List<List<?>>} of statements
     */
    public List<List<?>> getPurgeEntityStatements(String guid) {
        return getEvictDocStatements(EntitySummaryMapping.getReference(guid));
    }

    /**
     * Update the provided entity instance in the Crux repository.
     * @param entity to update
     * @return EntityDetail that was updated
     */
    public EntityDetail updateEntity(EntityDetail entity) {
        return createEntity(entity);
    }

    /**
     * Retrieve the requested entity, as a proxy, from the Crux repository.
     * @param guid of the entity to retrieve
     * @return EntityProxy of the current version of the entity
     */
    public EntityProxy getEntityProxy(String guid) {
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(EntityProxyMapping.getReference(guid), null);
        return EntityProxyMapping.getFromMap(this, cruxDoc);
    }

    /**
     * Retrieve the requested entity from the Crux repository.
     * @param guid of the entity to retrieve
     * @return EntitySummary of the current version of the entity
     */
    public EntitySummary getEntitySummary(String guid) {
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(EntitySummaryMapping.getReference(guid), null);
        log.debug("Found results: {}", cruxDoc);
        EntitySummaryMapping esm = new EntitySummaryMapping(this, cruxDoc);
        return esm.toEgeria();
    }

    /**
     * Retrieve the requested entity from the Crux repository.
     * @param guid of the entity to retrieve
     * @param asOfTime view of the entity at this particular point in time
     * @return EntityDetail as it existed at the specified point in time
     * @throws EntityProxyOnlyException if the entity requested is only an EntityProxy
     */
    public EntityDetail getEntity(String guid, Date asOfTime) throws EntityProxyOnlyException {
        final String methodName = "getEntity";
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(EntityDetailMapping.getReference(guid), asOfTime);
        log.debug("Found results: {}", cruxDoc);
        if (cruxDoc == null) {
            return null;
        }
        if (EntityProxyMapping.isOnlyAProxy(cruxDoc)) {
            throw new EntityProxyOnlyException(
                    CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                            guid, repositoryName
                    ),
                    this.getClass().getName(),
                    methodName
            );
        }
        EntityDetailMapping edm = new EntityDetailMapping(this, cruxDoc);
        return edm.toEgeria();
    }

    /**
     * Search based on the provided parameters.
     * @param entityTypeGUID see CruxOMRSMetadataCollection#findEntities
     * @param entitySubtypeGUIDs see CruxOMRSMetadataCollection#findEntities
     * @param matchProperties see CruxOMRSMetadataCollection#findEntities
     * @param fromEntityElement see CruxOMRSMetadataCollection#findEntities
     * @param limitResultsByStatus see CruxOMRSMetadataCollection#findEntities
     * @param matchClassifications see CruxOMRSMetadataCollection#findEntities
     * @param asOfTime see CruxOMRSMetadataCollection#findEntities
     * @param sequencingProperty see CruxOMRSMetadataCollection#findEntities
     * @param sequencingOrder see CruxOMRSMetadataCollection#findEntities
     * @param pageSize see CruxOMRSMetadataCollection#findEntities
     * @return {@code List<EntityDetail>}
     * @see CruxOMRSMetadataCollection#findEntities(String, String, List, SearchProperties, int, List, SearchClassifications, Date, String, SequencingOrder, int)
     */
    public List<EntityDetail> findEntities(String entityTypeGUID,
                                           List<String> entitySubtypeGUIDs,
                                           SearchProperties matchProperties,
                                           int fromEntityElement,
                                           List<InstanceStatus> limitResultsByStatus,
                                           SearchClassifications matchClassifications,
                                           Date asOfTime,
                                           String sequencingProperty,
                                           SequencingOrder sequencingOrder,
                                           int pageSize) {
        Collection<List<?>> cruxResults = searchCrux(
                entityTypeGUID,
                entitySubtypeGUIDs,
                matchProperties,
                fromEntityElement,
                limitResultsByStatus,
                matchClassifications,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                EntityDetailMapping.ENTITY_PROPERTIES_NS
        );
        log.debug("Found results: {}", cruxResults);
        List<EntityDetail> results = null;
        if (cruxResults != null) {
            results = new ArrayList<>();
            for (List<?> cruxResult : cruxResults) {
                Keyword docRef = (Keyword) cruxResult.get(0);
                Map<Keyword, Object> cruxDoc = getCruxObjectByReference(docRef, asOfTime);
                if (cruxDoc == null) {
                    log.warn("Unable to resolve search result into full doc: {}", cruxResult);
                } else {
                    EntityDetailMapping edm = new EntityDetailMapping(this, cruxDoc);
                    EntityDetail ed = edm.toEgeria();
                    if (ed != null) {
                        results.add(ed);
                    } else {
                        log.warn("Unable to translate Crux result into EntityDetail: {}", cruxDoc);
                    }
                }
            }
        }
        return results;
    }

    /**
     * Search based on the provided parameters.
     * @param relationshipTypeGUID see CruxOMRSMetadataCollection#findRelationships
     * @param relationshipSubtypeGUIDs see CruxOMRSMetadataCollection#findRelationships
     * @param matchProperties see CruxOMRSMetadataCollection#findRelationships
     * @param fromRelationshipElement see CruxOMRSMetadataCollection#findRelationships
     * @param limitResultsByStatus see CruxOMRSMetadataCollection#findRelationships
     * @param asOfTime see CruxOMRSMetadataCollection#findRelationships
     * @param sequencingProperty see CruxOMRSMetadataCollection#findRelationships
     * @param sequencingOrder see CruxOMRSMetadataCollection#findRelationships
     * @param pageSize see CruxOMRSMetadataCollection#findRelationships
     * @return {@code List<Relationship>}
     * @see CruxOMRSMetadataCollection#findRelationships(String, String, List, SearchProperties, int, List, Date, String, SequencingOrder, int)
     */
    public List<Relationship> findRelationships(String relationshipTypeGUID,
                                           List<String> relationshipSubtypeGUIDs,
                                           SearchProperties matchProperties,
                                           int fromRelationshipElement,
                                           List<InstanceStatus> limitResultsByStatus,
                                           Date asOfTime,
                                           String sequencingProperty,
                                           SequencingOrder sequencingOrder,
                                           int pageSize) {

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (and ensure we close it later).
        ICruxDatasource db;
        if (asOfTime != null) {
            db = cruxAPI.openDB(asOfTime);
        } else {
            db = cruxAPI.openDB();
        }

        Collection<List<?>> cruxResults = searchCrux(db,
                relationshipTypeGUID,
                relationshipSubtypeGUIDs,
                matchProperties,
                fromRelationshipElement,
                limitResultsByStatus,
                null,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                RelationshipMapping.RELATIONSHIP_PROPERTIES_NS
        );

        log.debug("Found results: {}", cruxResults);
        List<Relationship> results = null;
        if (cruxResults != null) {
            results = new ArrayList<>();
            for (List<?> cruxResult : cruxResults) {
                Keyword docRef = (Keyword) cruxResult.get(0);
                Map<Keyword, Object> cruxDoc = getCruxObjectByReference(db, docRef);
                if (cruxDoc == null) {
                    log.warn("Unable to resolve search result into full doc: {}", cruxResult);
                } else {
                    RelationshipMapping rm = new RelationshipMapping(this, cruxDoc, db);
                    Relationship relationship = rm.toEgeria();
                    if (relationship != null) {
                        results.add(relationship);
                    } else {
                        log.warn("Unable to translate Crux result into Relationship: {}", cruxDoc);
                    }
                }
            }
        }

        try {
            // 4. Ensure that we close the open DB resource now that we're finished with it
            db.close();
        } catch (IOException e) {
            log.error("Unable to close the open DB resource.", e);
        }

        return results;

    }

    /**
     * Create the provided relationship instance in the Crux repository.
     * @param relationship to create
     * @return Relationship that was created
     */
    public Relationship createRelationship(Relationship relationship) {
        Map<Keyword, ?> results = runSynchronousTx(getCreateRelationshipStatements(relationship));
        log.debug(" ... results: {}", results);
        return relationship;
    }

    /**
     * Retrieve the statements that need to be executed against Crux to create (persist) the relationship provided.
     * @param relationship to be created
     * @return {@code List<List<?>>} of statements
     */
    public List<List<?>> getCreateRelationshipStatements(Relationship relationship) {
        List<List<?>> list = new ArrayList<>();
        RelationshipMapping rm = new RelationshipMapping(this, relationship);
        list.add(rm.toCrux());
        return list;
    }

    /**
     * Permanently delete the relationship (and all of its history) from the Crux repository.
     * Note that this operation is NOT reversible!
     * @param guid of the relationship to permanently delete
     */
    public void purgeRelationship(String guid) {
        runSynchronousTx(getPurgeRelationshipStatements(guid));
    }

    /**
     * Retrieve the statements that need to be executed against Crux to permanently delete the relationship (and all of
     * its history) from the Crux repository.
     * @param guid of the relationship to permanently delete
     * @return {@code List<List<?>>} of statements
     */
    public List<List<?>> getPurgeRelationshipStatements(String guid) {
        return getEvictDocStatements(RelationshipMapping.getReference(guid));
    }

    /**
     * Update the provided relationship instance in the Crux repository.
     * @param relationship to update
     * @return Relationship that was updated
     */
    public Relationship updateRelationship(Relationship relationship) {
        return createRelationship(relationship);
    }

    /**
     * Retrieve the statements that need to be executed against Crux to update the provided relationship instance in the
     * Crux repository.
     * @param relationship to update
     * @return {@code List<List<?>>} of statements
     */
    public List<List<?>> getUpdateRelationshipStatements(Relationship relationship) {
        return getCreateRelationshipStatements(relationship);
    }

    /**
     * Retrieve the requested relationship from the Crux repository.
     * @param guid of the relationship to retrieve
     * @param asOfTime view of the relationship at this particular point in time
     * @return Relationship as it existed at the specified point in time
     */
    public Relationship getRelationship(String guid, Date asOfTime) {

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (and ensure we close it later).
        ICruxDatasource db;
        if (asOfTime != null) {
             db = cruxAPI.openDB(asOfTime);
        } else {
            db = cruxAPI.openDB();
        }

        // 1. Retrieve the relationship document itself, first
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(db, RelationshipMapping.getReference(guid));
        log.debug("Found results: {}", cruxDoc);

        // 2. Pass the relationship document and the open DB connection to the mapping
        //  (so it can retrieve the entity proxies from that same point-in-time)
        RelationshipMapping rm = new RelationshipMapping(this, cruxDoc, db);

        // 3. Map through the result (involves additional queries, so cannot close DB yet)
        Relationship result = rm.toEgeria();

        try {
            // 4. Ensure that we close the open DB resource now that we're finished with it
            db.close();
        } catch (IOException e) {
            log.error("Unable to close the open DB resource.", e);
        }

        // 5. Return the resulting relationship
        return result;

    }

    /**
     * Save the provided entity as a reference copy. This is only possible if there is no existing entity with the same
     * GUID, or if there is an existing entity with this GUID and its metadataCollectionId matches. Any other scenario
     * is an error for a reference copy.
     * @param entity to save as a reference copy
     * @throws EntityConflictException the entity conflicts with an existing entity (different metadata collection IDs)
     */
    public void saveReferenceCopy(EntityDetail entity) throws EntityConflictException {

        final String methodName = "saveReferenceCopy";

        String rcGuid = entity.getGUID();
        String rcMetadataCollectionId = entity.getMetadataCollectionId();

        // Retrieve it as a Summary, since we do not care whether it is a Proxy or a full Detail for this operation
        EntitySummary existingEntity = getEntitySummary(rcGuid);
        if (existingEntity == null) {
            createEntity(entity);
        } else {
            String exMetadataCollectionId = existingEntity.getMetadataCollectionId();
            if (!rcMetadataCollectionId.equals(exMetadataCollectionId)) {
                throw new EntityConflictException(
                        CruxOMRSErrorCode.METADATA_COLLECTION_CONFLICT.getMessageDefinition(
                                entity.getGUID(), repositoryName
                        ),
                        this.getClass().getName(),
                        methodName
                );
            } else {
                updateEntity(entity);
            }
        }

    }

    /**
     * Retrieve the statements that need to be executed against Crux to save the provided relationship as a reference
     * copy. This is only possible if there is no existing relationship with the same GUID, or if there is an existing
     * relationship with this GUID and its metadataCollectionId matches. Any other scenario is an error for a reference
     * copy.
     * @param relationship to save as a reference copy
     * @return {@code List<List<?>>} of statements
     * @throws RelationshipConflictException the relationship conflicts with an existing relationship (different metadata collection IDs)
     */
    public List<List<?>> getSaveReferenceCopyStatements(Relationship relationship) throws RelationshipConflictException {

        final String methodName = "getSaveReferenceCopyStatements";

        String rcGuid = relationship.getGUID();
        String rcMetadataCollectionId = relationship.getMetadataCollectionId();

        Relationship existingRelationship = getRelationship(rcGuid, null);
        if (existingRelationship == null) {
            return getCreateRelationshipStatements(relationship);
        } else {
            String exMetadataCollectionId = existingRelationship.getMetadataCollectionId();
            if (!rcMetadataCollectionId.equals(exMetadataCollectionId)) {
                throw new RelationshipConflictException(
                        CruxOMRSErrorCode.METADATA_COLLECTION_CONFLICT.getMessageDefinition(
                                relationship.getGUID(), repositoryName), this.getClass().getName(), methodName);
            } else {
                return getUpdateRelationshipStatements(relationship);
            }
        }

    }

    /**
     * Retrieve the requested reference's details from the Crux repository.
     * @param reference indicating the primary key of the Crux object to retrieve
     * @param asOfTime view of the object at this particular point in time (or null for current)
     * @return {@code Map<Keyword, Object>} of the object's properties
     */
    public Map<Keyword, Object> getCruxObjectByReference(Keyword reference, Date asOfTime) {
        if (asOfTime != null) {
            return cruxAPI.db(asOfTime).entity(reference);
        } else {
            return cruxAPI.db().entity(reference);
        }
    }

    /**
     * Retrieve the requested reference's details from an already-open Crux repository.
     * @param db from which to retrieve the details
     * @param reference indicating the primary key of the Crux object to retrieve
     * @return {@code Map<Keyword, Object>} of the object's properties
     */
    public Map<Keyword, Object> getCruxObjectByReference(ICruxDatasource db, Keyword reference) {
        return db.entity(reference);
    }

    /**
     * Search Crux based on the provided parameters (should work across both Entities and Relationships).
     * @param typeGuid to limit the search by type (optional)
     * @param subtypeGuids to limit the search to a set of subtypes (optional)
     * @param matchProperties by which to limit the results (optional)
     * @param fromElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param asOfTime for a historical search (optional, null will give current results)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @param namespace by which to qualify the matchProperties
     * @return {@code Collection<List<?>>} list of the Crux document references that match
     */
    public Collection<List<?>> searchCrux(String typeGuid,
                                          List<String> subtypeGuids,
                                          SearchProperties matchProperties,
                                          int fromElement,
                                          List<InstanceStatus> limitResultsByStatus,
                                          SearchClassifications matchClassifications,
                                          Date asOfTime,
                                          String sequencingProperty,
                                          SequencingOrder sequencingOrder,
                                          int pageSize,
                                          String namespace) {
        CruxQuery query = new CruxQuery();
        query.addTypeCondition(typeGuid, subtypeGuids);
        query.addSequencing(sequencingOrder, sequencingProperty, namespace);
        query.addPaging(fromElement, pageSize);
        query.addPropertyConditions(matchProperties, namespace);
        query.addClassificationConditions(matchClassifications);
        query.addStatusLimiters(limitResultsByStatus);
        log.debug("Querying with: {}", query.getQuery());
        return cruxAPI.db(asOfTime).query(query.getQuery());
    }

    /**
     * Search Crux based on the provided parameters, using an already-opened point-in-time view of the database (should
     * work across both Entities and Relationships).
     * @param db already opened point-in-time view of the database
     * @param typeGuid to limit the search by type (optional)
     * @param subtypeGuids to limit the search to a set of subtypes (optional)
     * @param matchProperties by which to limit the results (optional)
     * @param fromElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optinoal, will default to GUID)
     * @param pageSize maximum number of results per page
     * @param namespace by which to qualify the matchProperties
     * @return {@code Collection<List<?>>} list of the Crux document references that match
     */
    public Collection<List<?>> searchCrux(ICruxDatasource db,
                                          String typeGuid,
                                          List<String> subtypeGuids,
                                          SearchProperties matchProperties,
                                          int fromElement,
                                          List<InstanceStatus> limitResultsByStatus,
                                          SearchClassifications matchClassifications,
                                          String sequencingProperty,
                                          SequencingOrder sequencingOrder,
                                          int pageSize,
                                          String namespace) {
        CruxQuery query = new CruxQuery();
        query.addTypeCondition(typeGuid, subtypeGuids);
        query.addSequencing(sequencingOrder, sequencingProperty, namespace);
        query.addPaging(fromElement, pageSize);
        query.addPropertyConditions(matchProperties, namespace);
        query.addClassificationConditions(matchClassifications);
        query.addStatusLimiters(limitResultsByStatus);
        log.debug("Querying with: {}", query.getQuery());
        return db.query(query.getQuery());
    }

    /**
     * Run multiple statements through Crux as a single transaction.
     * @param statements to submit in the same transaction
     * @return {@code Map<Keyword, ?>} transaction details
     */
    public Map<Keyword, ?> runMultiStatementTx(List<List<?>> statements) {
        return runSynchronousTx(statements);
    }

    private Map<Keyword, ?> runSynchronousTx(List<List<?>> statements) {
        log.debug("Synchronously transacting with: {}", statements);
        Map<Keyword, ?> tx = cruxAPI.submitTx(statements);
        return cruxAPI.awaitTx(tx, timeout);
    }

    private List<List<?>> getEvictDocStatements(Keyword docRef) {
        List<List<?>> statements = new ArrayList<>();
        statements.add(Constants.evict(docRef));
        return statements;
    }

}
