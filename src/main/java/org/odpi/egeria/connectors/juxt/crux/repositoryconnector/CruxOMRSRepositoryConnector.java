/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.repositoryconnector;

import clojure.lang.*;
import crux.api.Crux;
import crux.api.HistoryOptions;
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
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.ffdc.OMRSErrorCode;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
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
                        super.serverName,
                        repositoryHelper,
                        repositoryValidator,
                        metadataCollectionId,
                        auditLog);
            } catch (Exception e) {
                throw new OMRSLogicErrorException(OMRSErrorCode.NULL_METADATA_COLLECTION.getMessageDefinition(super.serverName),
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
                // TODO: receive these options through the connection properties
                Map<String, Map<String, String>> configOptions = new HashMap<>();
                configOptions.put("crux.lucene/lucene-store", new HashMap<>());
                configOptions.get("crux.lucene/lucene-store").put("db-dir", "crux-lucene");
                cruxAPI = Crux.startNode(configOptions);
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
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(EntityProxyMapping.getReference(guid));
        return EntityProxyMapping.getFromMap(this, cruxDoc);
    }

    /**
     * Retrieve the requested entity from the Crux repository.
     * @param guid of the entity to retrieve
     * @return EntitySummary of the current version of the entity
     */
    public EntitySummary getEntitySummary(String guid) {
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(EntitySummaryMapping.getReference(guid));
        log.debug("Found results: {}", cruxDoc);
        EntitySummaryMapping esm = new EntitySummaryMapping(this, cruxDoc);
        return esm.toEgeria();
    }

    /**
     * Retrieve the requested entity from the Crux repository.
     * @param guid of the entity to retrieve
     * @param asOfTime view of the entity at this particular point in time
     * @param acceptProxies if true, allow proxies to be returned as EntityDetails
     * @return EntityDetail as it existed at the specified point in time
     * @throws EntityProxyOnlyException if the entity requested is only an EntityProxy (and acceptProxies is false)
     */
    public EntityDetail getEntity(String guid, Date asOfTime, boolean acceptProxies) throws EntityProxyOnlyException {
        final String methodName = "getEntity";
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(EntityDetailMapping.getReference(guid), asOfTime);
        log.debug("Found results: {}", cruxDoc);
        if (cruxDoc == null) {
            return null;
        }
        if (!acceptProxies && EntityProxyMapping.isOnlyAProxy(cruxDoc)) {
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
     * @throws TypeErrorException if a requested type for searching is not known to the repository
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
                                           int pageSize) throws TypeErrorException {
        Collection<List<?>> cruxResults = searchCrux(
                TypeDefCategory.ENTITY_DEF,
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
        return translateEntityResults(cruxResults, asOfTime);
    }

    /**
     * Search based on the provided parameters.
     * @param entityTypeGUID see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param searchCriteria see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param fromEntityElement see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param limitResultsByStatus see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param matchClassifications see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param asOfTime see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param sequencingProperty see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param sequencingOrder see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param pageSize see CruxOMRSMetadataCollection#findEntitiesByPropertyValue
     * @return {@code List<EntityDetail>}
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws FunctionNotSupportedException if the regular expression defined in searchCriteria cannot be efficiently searched across all text properties
     * @see CruxOMRSMetadataCollection#findEntitiesByPropertyValue(String, String, String, int, List, List, Date, String, SequencingOrder, int)
     */
    public List<EntityDetail> findEntitiesByText(String entityTypeGUID,
                                                 String searchCriteria,
                                                 int fromEntityElement,
                                                 List<InstanceStatus> limitResultsByStatus,
                                                 SearchClassifications matchClassifications,
                                                 Date asOfTime,
                                                 String sequencingProperty,
                                                 SequencingOrder sequencingOrder,
                                                 int pageSize) throws TypeErrorException, FunctionNotSupportedException {
        Collection<List<?>> cruxResults = searchCruxLucene(
                TypeDefCategory.ENTITY_DEF,
                entityTypeGUID,
                searchCriteria,
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
        return translateEntityResults(cruxResults, asOfTime);
    }

    /**
     * Translate the provided list of Crux document references into a list of full EntityDetail results.
     * @param cruxResults list of Crux document references (ie. from a search)
     * @param asOfTime point in time view of the data (or null for current)
     * @return {@code List<EntityDetail>} list of Egeria representation of the results
     */
    private List<EntityDetail> translateEntityResults(Collection<List<?>> cruxResults, Date asOfTime) {
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
     * Find the relationships, limited by the specified criteria, for the provided entity.
     * @param entityGUID of the entity for which to find relationships
     * @param relationshipTypeGUID to limit the relationship types to retrieve (optional)
     * @param fromRelationshipElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param asOfTime view of the relationships at this particular point in time
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @return {@code List<Relationship>} list of the matching relationships
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    public List<Relationship> findRelationshipsForEntity(String entityGUID,
                                                         String relationshipTypeGUID,
                                                         int fromRelationshipElement,
                                                         List<InstanceStatus> limitResultsByStatus,
                                                         Date asOfTime,
                                                         String sequencingProperty,
                                                         SequencingOrder sequencingOrder,
                                                         int pageSize) throws TypeErrorException {

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (and ensure we close it later).
        ICruxDatasource db;
        if (asOfTime != null) {
            db = cruxAPI.openDB(asOfTime);
        } else {
            db = cruxAPI.openDB();
        }

        Collection<List<?>> cruxResults = findEntityRelationships(db,
                entityGUID,
                relationshipTypeGUID,
                fromRelationshipElement,
                limitResultsByStatus,
                sequencingProperty,
                sequencingOrder,
                pageSize);

        log.debug("Found results: {}", cruxResults);
        List<Relationship> results = resultsToList(db, cruxResults);

        // Ensure that we close the open DB resource now that we're finished with it
        closeDb(db);

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
     * @throws TypeErrorException if a requested type for searching is not known to the repository
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
                                                int pageSize) throws TypeErrorException {

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (and ensure we close it later).
        ICruxDatasource db;
        if (asOfTime != null) {
            db = cruxAPI.openDB(asOfTime);
        } else {
            db = cruxAPI.openDB();
        }

        Collection<List<?>> cruxResults = searchCrux(db,
                TypeDefCategory.RELATIONSHIP_DEF,
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
        List<Relationship> results = resultsToList(db, cruxResults);

        // Ensure that we close the open DB resource now that we're finished with it
        closeDb(db);

        return results;

    }

    /**
     * Search based on the provided parameters.
     * @param relationshipTypeGUID see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param searchCriteria see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param fromRelationshipElement see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param limitResultsByStatus see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param asOfTime see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param sequencingProperty see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param sequencingOrder see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param pageSize see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @return {@code List<Relationship>}
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws FunctionNotSupportedException if the regular expression defined in searchCriteria cannot be efficiently searched across all text properties
     * @see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue(String, String, String, int, List, Date, String, SequencingOrder, int)
     */
    public List<Relationship> findRelationshipsByText(String relationshipTypeGUID,
                                                      String searchCriteria,
                                                      int fromRelationshipElement,
                                                      List<InstanceStatus> limitResultsByStatus,
                                                      Date asOfTime,
                                                      String sequencingProperty,
                                                      SequencingOrder sequencingOrder,
                                                      int pageSize) throws TypeErrorException, FunctionNotSupportedException {

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (and ensure we close it later).
        ICruxDatasource db;
        if (asOfTime != null) {
            db = cruxAPI.openDB(asOfTime);
        } else {
            db = cruxAPI.openDB();
        }

        Collection<List<?>> cruxResults = searchCruxLucene(db,
                TypeDefCategory.RELATIONSHIP_DEF,
                relationshipTypeGUID,
                searchCriteria,
                fromRelationshipElement,
                limitResultsByStatus,
                null,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                RelationshipMapping.RELATIONSHIP_PROPERTIES_NS
        );

        log.debug("Found results: {}", cruxResults);
        List<Relationship> results = resultsToList(db, cruxResults);

        // Ensure that we close the open DB resource now that we're finished with it
        closeDb(db);

        return results;

    }

    /**
     * Translate the set of Crux document IDs into a list of Egeria Relationships.
     * @param db already opened point-in-time view of the database
     * @param cruxResults list of document IDs, eg. from a search
     * @return {@code List<Relationship>}
     * @see #searchCrux(ICruxDatasource, TypeDefCategory, String, List, SearchProperties, int, List, SearchClassifications, String, SequencingOrder, int, String)
     * @see #findEntityRelationships(ICruxDatasource, String, String, int, List, String, SequencingOrder, int)
     */
    private List<Relationship> resultsToList(ICruxDatasource db, Collection<List<?>> cruxResults) {
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

        // 4. Ensure that we close the open DB resource now that we're finished with it
        closeDb(db);

        // 5. Return the resulting relationship
        return result;

    }

    /**
     * Restore the previous version of the provided entity, and return the restored version (or null if there was no
     * previous version and hence no restoration).
     * @param userId of the user requesting the restoration
     * @param guid of the entity for which to restore the previous version
     * @return EntityDetail giving the restored version (or null if there was no previous version to restore)
     */
    public EntityDetail restorePreviousVersionOfEntity(String userId, String guid) {
        Keyword docRef = EntitySummaryMapping.getReference(guid);
        List<Map<Keyword, ?>> history = getObjectHistory(docRef);
        Map<Keyword, Object> currentVersionTxn  = null;
        Map<Keyword, Object> previousVersionTxn = null;
        if (history != null && history.size() > 1) {
            // There must be a minimum of two entries in the history for us to have a previous version to go to.
            currentVersionTxn  = new HashMap<>(history.get(0));
            previousVersionTxn = new HashMap<>(history.get(1));
        }
        if (currentVersionTxn != null) {
            Map<Keyword, Object> currentVersionCrux = getCruxObjectByReference(docRef, currentVersionTxn);
            EntityDetailMapping edmC = new EntityDetailMapping(this, currentVersionCrux);
            EntityDetail current = edmC.toEgeria();
            long currentVersion = current.getVersion();
            Map<Keyword, Object> previousVersionCrux = getCruxObjectByReference(docRef, previousVersionTxn);
            EntityDetailMapping edmP = new EntityDetailMapping(this, previousVersionCrux);
            EntityDetail restored = edmP.toEgeria();
            // Update the version of the restored instance to be one more than the latest (current) version, the update
            // time to reflect now (so we have an entirely new record in history that shows as the latest (current)),
            // and the last user to update it to the user that requested this restoration
            restored.setVersion(currentVersion + 1);
            restored.setUpdateTime(new Date());
            restored.setUpdatedBy(userId);
            // TODO: should we not add the calling user to the 'maintainedBy' list?
            // Then submit this version back into Crux as an update, and return the result
            return updateEntity(restored);
        }
        return null;
    }

    /**
     * Restore the previous version of the provided relationship, and return the restored version (or null if there was
     * no previous version and hence no restoration).
     * @param userId of the user requesting the restoration
     * @param guid of the relationship for which to restore the previous version
     * @return Relationship giving the restored version (or null if there was no previous version to restore)
     */
    public Relationship restorePreviousVersionOfRelationship(String userId, String guid) {
        Keyword docRef = RelationshipMapping.getReference(guid);
        ICruxDatasource db = cruxAPI.openDB();
        List<Map<Keyword, ?>> history = getObjectHistory(db, docRef);
        Map<Keyword, Object> currentVersionTxn  = null;
        Map<Keyword, Object> previousVersionTxn = null;
        if (history != null && history.size() > 1) {
            // There must be a minimum of two entries in the history for us to have a previous version to go to.
            currentVersionTxn  = new HashMap<>(history.get(0));
            previousVersionTxn = new HashMap<>(history.get(1));
        }
        Relationship restored = null;
        if (currentVersionTxn != null) {
            // Note that here we will not pass-through the opened DB as these methods will need to retrieve a different
            // point-in-time view anyway (so cannot use the opened DB resource)
            Map<Keyword, Object> currentVersionCrux = getCruxObjectByReference(docRef, currentVersionTxn);
            RelationshipMapping rmC = new RelationshipMapping(this, currentVersionCrux, db);
            Relationship current = rmC.toEgeria();
            long currentVersion = current.getVersion();
            Map<Keyword, Object> previousVersionCrux = getCruxObjectByReference(docRef, previousVersionTxn);
            RelationshipMapping rmP = new RelationshipMapping(this, previousVersionCrux, db);
            restored = rmP.toEgeria();
            // Ensure that we close the open DB resource now that we're finished with it
            closeDb(db);
            // Update the version of the restored instance to be one more than the latest (current) version, the update
            // time to reflect now (so we have an entirely new record in history that shows as the latest (current)),
            // and the last user to update it to the user that requested this restoration
            restored.setVersion(currentVersion + 1);
            restored.setUpdateTime(new Date());
            restored.setUpdatedBy(userId);
            // Then submit this version back into Crux as an update
            restored = updateRelationship(restored);
        } else {
            // Even if we did not find any history, ensure that we close the open DB resource
            closeDb(db);
        }
        return restored;
    }

    /**
     * Retrieve the transaction history for the provided Crux object, which will be of a form like the following:
     * <code>
     * {
     *     :crux.tx/tx-time #inst "2021-02-01T00:28:32.533-00:00",
     *     :crux.tx/tx-id 2,
     *     :crux.db/valid-time #inst "2021-02-01T00:28:32.531-00:00",
     *     :crux.db/content-hash #crux/id "80cdbac164c61913dee8e391db249db941fb053a"
     * }
     * </code>
     * @param reference indicating the primary key of the object for which to retrieve the transaction history.
     * @return {@code List<Map<Keyword, ?>>} of the transaction history
     */
    private List<Map<Keyword, ?>> getObjectHistory(Keyword reference) {
        HistoryOptions options = HistoryOptions.create(HistoryOptions.SortOrder.DESC);
        List<Map<Keyword, ?>> history = cruxAPI.db().entityHistory(reference, options);
        log.debug("Found history: {}", history);
        return history;
    }

    /**
     * Retrieve the transaction history for the provided Crux object, from an already-opened point-in-time view of the
     * repository, which will be of a form like the following:
     * <code>
     * {
     *     :crux.tx/tx-time #inst "2021-02-01T00:28:32.533-00:00",
     *     :crux.tx/tx-id 2,
     *     :crux.db/valid-time #inst "2021-02-01T00:28:32.531-00:00",
     *     :crux.db/content-hash #crux/id "80cdbac164c61913dee8e391db249db941fb053a"
     * }
     * </code>
     * @param db from which to retrieve the transaction history
     * @param reference indicating the primary key of the object for which to retrieve the transaction history.
     * @return {@code List<Map<Keyword, ?>>} of the transaction history
     */
    private List<Map<Keyword, ?>> getObjectHistory(ICruxDatasource db, Keyword reference) {
        HistoryOptions options = HistoryOptions.create(HistoryOptions.SortOrder.DESC);
        List<Map<Keyword, ?>> history = db.entityHistory(reference, options);
        log.debug("Found history: {}", history);
        return history;
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
     * Retrieve the current version of the requested reference's details from the Crux repository.
     * @param reference indicating the primary key of the Crux object to retrieve
     * @return {@code Map<Keyword, Object>} of the object's properties
     */
    public Map<Keyword, Object> getCruxObjectByReference(Keyword reference) {
        return getCruxObjectByReference(reference, (Date) null);
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
     * Retrieve the requested reference's details from the Crux repository at the precise version indicated by
     * the provided transaction details (as returned by an entity history call, must include the valid-time and
     * tx-time).
     * @param reference indicating the primary key of the Crux object to retrieve
     * @param txnDetails containing the valid-time and tx-time of the precise version of the document to retrieve
     * @return {@code Map<Keyword, Object>} of the object's properties
     */
    public Map<Keyword, Object> getCruxObjectByReference(Keyword reference, Map<Keyword, Object> txnDetails) {
        Date validTime = (Date) txnDetails.get(Constants.CRUX_VALID_TIME);
        Date txnTime   = (Date) txnDetails.get(Constants.CRUX_TX_TIME);
        return cruxAPI.db(validTime, txnTime).entity(reference);
    }

    /**
     * Search Crux based on the provided parameters (should work across both Entities and Relationships).
     * @param category to limit the search to either entities or relationships (required)
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
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    public Collection<List<?>> searchCrux(TypeDefCategory category,
                                          String typeGuid,
                                          List<String> subtypeGuids,
                                          SearchProperties matchProperties,
                                          int fromElement,
                                          List<InstanceStatus> limitResultsByStatus,
                                          SearchClassifications matchClassifications,
                                          Date asOfTime,
                                          String sequencingProperty,
                                          SequencingOrder sequencingOrder,
                                          int pageSize,
                                          String namespace) throws TypeErrorException {
        CruxQuery query = new CruxQuery();
        updateQuery(query,
                category,
                typeGuid,
                subtypeGuids,
                matchProperties,
                fromElement,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                namespace);
        log.debug("Querying with: {}", query.getQuery());
        return cruxAPI.db(asOfTime).query(query.getQuery());
    }

    /**
     * Search Crux based on the provided parameters, using an already-opened point-in-time view of the database (should
     * work across both Entities and Relationships).
     * @param db already opened point-in-time view of the database
     * @param category to limit the search to either entities or relationships (required)
     * @param typeGuid to limit the search by type (optional)
     * @param subtypeGuids to limit the search to a set of subtypes (optional)
     * @param matchProperties by which to limit the results (optional)
     * @param fromElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @param namespace by which to qualify the matchProperties
     * @return {@code Collection<List<?>>} list of the Crux document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    public Collection<List<?>> searchCrux(ICruxDatasource db,
                                          TypeDefCategory category,
                                          String typeGuid,
                                          List<String> subtypeGuids,
                                          SearchProperties matchProperties,
                                          int fromElement,
                                          List<InstanceStatus> limitResultsByStatus,
                                          SearchClassifications matchClassifications,
                                          String sequencingProperty,
                                          SequencingOrder sequencingOrder,
                                          int pageSize,
                                          String namespace) throws TypeErrorException {
        CruxQuery query = new CruxQuery();
        updateQuery(query,
                category,
                typeGuid,
                subtypeGuids,
                matchProperties,
                fromElement,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                namespace);
        log.debug("Querying with: {}", query.getQuery());
        return db.query(query.getQuery());
    }

    /**
     * Search all text properties in Crux based on the provided parameters (should work across both Entities and
     * Relationships).
     * @param category to limit the search to either entities or relationships (required)
     * @param typeGuid to limit the search by type (optional)
     * @param searchCriteria by which to limit the results (required, must be a Java regular expression)
     * @param fromElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param asOfTime for a historical search (optional, null will give current results)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @param namespace by which to qualify the matchProperties
     * @return {@code Collection<List<?>>} list of the Crux document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws FunctionNotSupportedException if the searchCriteria uses a complex regular expression that cannot efficiently be searched across all text fields
     */
    public Collection<List<?>> searchCruxLucene(TypeDefCategory category,
                                                String typeGuid,
                                                String searchCriteria,
                                                int fromElement,
                                                List<InstanceStatus> limitResultsByStatus,
                                                SearchClassifications matchClassifications,
                                                Date asOfTime,
                                                String sequencingProperty,
                                                SequencingOrder sequencingOrder,
                                                int pageSize,
                                                String namespace) throws TypeErrorException, FunctionNotSupportedException {
        CruxQuery query = new CruxQuery();
        updateTextQuery(query,
                category,
                typeGuid,
                searchCriteria,
                fromElement,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                namespace);
        log.debug("Querying with: {}", query.getQuery());
        return cruxAPI.db(asOfTime).query(query.getQuery());
    }

    /**
     * Search all text properties in Crux based on the provided parameters, using an already-opened point-in-time view
     * of the database (should work across both Entities and Relationships).
     * @param db already opened point-in-time view of the database
     * @param category to limit the search to either entities or relationships (required)
     * @param typeGuid to limit the search by type (optional)
     * @param searchCriteria by which to limit the results (required, must be a Java regular expression)
     * @param fromElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @param namespace by which to qualify the matchProperties
     * @return {@code Collection<List<?>>} list of the Crux document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws FunctionNotSupportedException if the searchCriteria uses a complex regular expression that cannot efficiently be searched across all text fields
     */
    public Collection<List<?>> searchCruxLucene(ICruxDatasource db,
                                                TypeDefCategory category,
                                                String typeGuid,
                                                String searchCriteria,
                                                int fromElement,
                                                List<InstanceStatus> limitResultsByStatus,
                                                SearchClassifications matchClassifications,
                                                String sequencingProperty,
                                                SequencingOrder sequencingOrder,
                                                int pageSize,
                                                String namespace) throws TypeErrorException, FunctionNotSupportedException {
        CruxQuery query = new CruxQuery();
        updateTextQuery(query,
                category,
                typeGuid,
                searchCriteria,
                fromElement,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                namespace);
        log.debug("Querying with: {}", query.getQuery());
        return db.query(query.getQuery());
    }

    /**
     * Find the relationships that match the provided parameters.
     * @param db already opened point-in-time view of the database
     * @param entityGUID of the entity for which to find relationships
     * @param relationshipTypeGUID to limit the relationship types to retrieve (optional)
     * @param fromRelationshipElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @return {@code Collection<List<?>>} list of the Crux document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    public Collection<List<?>> findEntityRelationships(ICruxDatasource db,
                                                       String entityGUID,
                                                       String relationshipTypeGUID,
                                                       int fromRelationshipElement,
                                                       List<InstanceStatus> limitResultsByStatus,
                                                       String sequencingProperty,
                                                       SequencingOrder sequencingOrder,
                                                       int pageSize) throws TypeErrorException {
        CruxQuery query = new CruxQuery();
        query.addRelationshipEndpointConditions(EntitySummaryMapping.getReference(entityGUID));
        updateQuery(query,
                TypeDefCategory.RELATIONSHIP_DEF,
                relationshipTypeGUID,
                null,
                null,
                fromRelationshipElement,
                limitResultsByStatus,
                null,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                null);
        log.debug("Querying with: {}", query.getQuery());
        return db.query(query.getQuery());
    }

    /**
     * Update the provided query with the specified parameters.
     * @param query into which to add conditions
     * @param category to limit the search to either entities or relationships (required)
     * @param typeGuid to limit the search by type (optional)
     * @param subtypeGuids to limit the search to a set of subtypes (optional)
     * @param matchProperties by which to limit the results (optional)
     * @param fromElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @param namespace by which to qualify the matchProperties
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    private void updateQuery(CruxQuery query,
                             TypeDefCategory category,
                             String typeGuid,
                             List<String> subtypeGuids,
                             SearchProperties matchProperties,
                             int fromElement,
                             List<InstanceStatus> limitResultsByStatus,
                             SearchClassifications matchClassifications,
                             String sequencingProperty,
                             SequencingOrder sequencingOrder,
                             int pageSize,
                             String namespace) throws TypeErrorException {
        query.addTypeCondition(typeGuid, subtypeGuids);
        query.addTypeDefCategoryCondition(category);
        // Note that we need to pass-through the complete set of type names to include in the search so that we can
        // further qualify the property names (the same property name could be different in different types).
        // We will also send through the repositoryHelper to reverse-lookup property names to the types that they
        // could exist within.
        Set<String> completeTypeSet = getCompleteSetOfTypeNamesForSearch(typeGuid, subtypeGuids, namespace);
        query.addPropertyConditions(matchProperties, namespace, completeTypeSet, repositoryHelper, repositoryName);
        query.addClassificationConditions(matchClassifications, completeTypeSet, repositoryHelper, repositoryName);
        query.addSequencing(sequencingOrder, sequencingProperty, namespace, completeTypeSet, repositoryHelper, repositoryName);
        query.addPaging(fromElement, pageSize);
        query.addStatusLimiters(limitResultsByStatus);
    }

    /**
     * Update the provided query with the specified parameters for a free-form text search across all text fields.
     * @param query into which to add conditions
     * @param category to limit the search to either entities or relationships (required)
     * @param typeGuid to limit the search by type (optional)
     * @param searchCriteria defining the textual regular expression to use to match against all text fields
     * @param fromElement starting element for paging
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param pageSize maximum number of results per page
     * @param namespace by which to qualify the sequencing property (if any)
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws FunctionNotSupportedException if the searchCriteria uses a complex regular expression that cannot efficiently be searched across all text fields
     */
    private void updateTextQuery(CruxQuery query,
                                 TypeDefCategory category,
                                 String typeGuid,
                                 String searchCriteria,
                                 int fromElement,
                                 List<InstanceStatus> limitResultsByStatus,
                                 SearchClassifications matchClassifications,
                                 String sequencingProperty,
                                 SequencingOrder sequencingOrder,
                                 int pageSize,
                                 String namespace) throws TypeErrorException, FunctionNotSupportedException {
        query.addTypeCondition(typeGuid, null);
        query.addTypeDefCategoryCondition(category);
        Set<String> completeTypeSet = getCompleteSetOfTypeNamesForSearch(typeGuid, null, namespace);
        query.addClassificationConditions(matchClassifications, completeTypeSet, repositoryHelper, repositoryName);
        query.addWildcardTextCondition(searchCriteria, repositoryHelper, repositoryName);
        query.addSequencing(sequencingOrder, sequencingProperty, namespace, completeTypeSet, repositoryHelper, repositoryName);
        query.addPaging(fromElement, pageSize);
        query.addStatusLimiters(limitResultsByStatus);
    }

    /**
     * Retrieve the complete list of type names that have been requested by the search.
     * @param typeGuid provided to the search, to limit by type
     * @param subtypeGuids provided to the search, to limit to a set of subtypes
     * @param namespace by which properties will be qualified (allowing us to see whether the types should be for entities or relationships)
     * @return {@code Set<String>} of the names of all types and subtypes to include in the search
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    private Set<String> getCompleteSetOfTypeNamesForSearch(String typeGuid,
                                                           List<String> subtypeGuids,
                                                           String namespace) throws TypeErrorException {
        final String methodName = "getCompleteListOfTypeNamesForSearch";
        Set<String> complete = new HashSet<>();
        if (namespace != null) {
            if (subtypeGuids != null && !subtypeGuids.isEmpty()) {
                // If subtypes were specified, we can short-circuit to only considering those (and logic is the same
                // across entity types and relationship types)
                for (String subtypeGuid : subtypeGuids) {
                    String typeDefName = repositoryHelper.getTypeDef(repositoryName, "subtypeGuids", subtypeGuid, methodName).getName();
                    addAllSubtypesToSet(complete, typeDefName);
                }
            } else if (typeGuid != null) {
                // Otherwise we need to consider all sub-types of the provided typeGuid
                String typeDefName = repositoryHelper.getTypeDef(repositoryName, "typeGuid", typeGuid, methodName).getName();
                addAllSubtypesToSet(complete, typeDefName);
            } else {
                // Otherwise we need to consider all types of the provided kind
                if (RelationshipMapping.RELATIONSHIP_PROPERTIES_NS.equals(namespace)) {
                    // We need all relationship types
                    try {
                        List<TypeDef> typeDefs = metadataCollection.findTypeDefsByCategory(null, TypeDefCategory.RELATIONSHIP_DEF);
                        if (typeDefs != null) {
                            for (TypeDef typeDef : typeDefs) {
                                String typeDefName = typeDef.getName();
                                addAllSubtypesToSet(complete, typeDefName);
                            }
                        }
                    } catch (InvalidParameterException | RepositoryErrorException | UserNotAuthorizedException e) {
                        log.error("Unable to retrieve all relationship typedefs.", e);
                    }
                } else {
                    // Otherwise we need all entity types
                    String typeDefName = "OpenMetadataRoot";
                    addAllSubtypesToSet(complete, typeDefName);
                }
            }
        }
        return complete;
    }

    /**
     * Add the names of all of the subtypes of the provided typeDefName to the provided set of subtypes.
     * @param subtypes to update with names of subtypes
     * @param typeDefName for which to retrieve all subtypes
     */
    private void addAllSubtypesToSet(Set<String> subtypes, String typeDefName) {
        subtypes.add(typeDefName);  // add the typedef itself, and then its subtypes
        List<String> subtypesList = repositoryHelper.getSubTypesOf(repositoryName, typeDefName);
        if (subtypesList != null) {
            subtypes.addAll(subtypesList);
        }
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

    private void closeDb(ICruxDatasource db) {
        try {
            db.close();
        } catch (IOException e) {
            log.error("Unable to close the open DB resource.", e);
        }
    }

}
