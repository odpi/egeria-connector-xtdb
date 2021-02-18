/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.repositoryconnector;

import clojure.lang.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import crux.api.*;
import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.crux.auditlog.CruxOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.crux.mapping.*;
import org.odpi.egeria.connectors.juxt.crux.model.search.CruxGraphQuery;
import org.odpi.egeria.connectors.juxt.crux.model.search.CruxQuery;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides all connectivity and API-based interaction with a Crux back-end.
 */
public class CruxOMRSRepositoryConnector extends OMRSRepositoryConnector {

    private static final Logger log = LoggerFactory.getLogger(CruxOMRSRepositoryConnector.class);

    private static final String SYNC = "Synchronously";
    private static final String ASYNC = "Asynchronously";

    private ICruxAPI cruxAPI = null;
    private boolean luceneConfigured = false;
    private boolean synchronousIndex = true;

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

        // Retrieve the configuration from the configurationProperties, and serialise it directly into a .json file
        File configFile = null;
        Map<String, Object> configProperties = connectionProperties.getConfigurationProperties();
        if (configProperties != null && !configProperties.isEmpty()) {
            if (configProperties.containsKey(CruxOMRSRepositoryConnectorProvider.CRUX_CONFIG)) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    configFile = File.createTempFile("crux", ".json");
                    Object cruxCfg = configProperties.get(CruxOMRSRepositoryConnectorProvider.CRUX_CONFIG);
                    if (cruxCfg instanceof Map) {
                        Map<?, ?> cruxConfig = (Map<?, ?>) cruxCfg;
                        log.debug("Writing configuration to: {}", configFile.getCanonicalPath());
                        mapper.writeValue(configFile, cruxConfig);
                        // Dynamically set whether Lucene is configured or not based on the presence of its configuration in
                        // the configurationProperties
                        luceneConfigured = cruxConfig.containsKey(Constants.CRUX_LUCENE);
                    }
                } catch (IOException e) {
                    throw new ConnectorCheckedException(CruxOMRSErrorCode.CANNOT_READ_CONFIGURATION.getMessageDefinition(repositoryName),
                            this.getClass().getName(), methodName, e);
                }
            }
            if (configProperties.containsKey(CruxOMRSRepositoryConnectorProvider.SYNCHRONOUS_INDEX)) {
                Object syncIdx = configProperties.get(CruxOMRSRepositoryConnectorProvider.SYNCHRONOUS_INDEX);
                if (syncIdx instanceof Boolean) {
                    synchronousIndex = (Boolean) syncIdx;
                }
            }
        }

        try {

            if (configFile == null) {
                // If no configuration options were specified, we will start an in-memory node
                auditLog.logMessage(methodName, CruxOMRSAuditCode.REPOSITORY_SERVICE_STARTING_NO_CONFIG.getMessageDefinition());
                cruxAPI = Crux.startNode();
            } else {
                // Otherwise we will use the configuration options to start the server
                auditLog.logMessage(methodName, CruxOMRSAuditCode.REPOSITORY_SERVICE_STARTING_WITH_CONFIG.getMessageDefinition());
                cruxAPI = Crux.startNode(configFile);
            }
            Map<Keyword, ?> details = cruxAPI.status();
            log.debug(" ... node: {}", details);
            log.debug(" ... luceneConfigured? {}", luceneConfigured);
            log.debug(" ... synchronousIndex? {}", synchronousIndex);
            Object version = details.get(Constants.CRUX_VERSION);
            auditLog.logMessage(methodName, CruxOMRSAuditCode.REPOSITORY_SERVICE_STARTED.getMessageDefinition(getServerName(), version == null ? "<null>" : version.toString()));
        } catch (Exception e) {
            log.error("Unable to start the repository based on the provided configuration.", e);
            // Note: unfortunately the audit log swallows this exception's stack trace, and therefore is insufficient
            // for someone attempting to understand why their configuration did not work -- hence logging an error above
            throw new ConnectorCheckedException(CruxOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect() throws ConnectorCheckedException {

        final String methodName = "disconnect";
        super.disconnect();

        // Ready the embedded Crux node for GC
        try {
            this.cruxAPI.close();
        } catch (IOException e) {
            throw new ConnectorCheckedException(CruxOMRSErrorCode.FAILED_DISCONNECT.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }
        if (auditLog != null) {
            auditLog.logMessage(methodName, CruxOMRSAuditCode.REPOSITORY_SERVICE_SHUTDOWN.getMessageDefinition(getServerName()));
        } else {
            log.info("Crux repository connector has shutdown.");
        }

    }

    /**
     * Create the provided entity instance in the Crux repository.
     * @param entity to create
     */
    public void createEntityProxy(EntityProxy entity) {
        Map<Keyword, ?> results = runTx(getCreateEntityProxyStatements(entity));
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
        Map<Keyword, ?> results = runTx(getCreateEntityStatements(entity));
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
        runTx(getPurgeEntityStatements(guid));
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
     * Retrieve the requested entity from the Crux repository.
     * @param db already opened point-in-time view of the database
     * @param guid of the entity to retrieve
     * @return EntityDetail as it existed at the specified database's point-in-time view
     */
    public EntityDetail getEntity(ICruxDatasource db, String guid) {
        return getEntity(db, EntityDetailMapping.getReference(guid));
    }

    /**
     * Retrieve the requested entity from the Crux repository.
     * @param db already opened point-in-time view of the database
     * @param ref of the entity to retrieve
     * @return EntityDetail as it existed at the specified database's point-in-time view
     */
    private EntityDetail getEntity(ICruxDatasource db, Keyword ref) {
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(db, ref);
        if (cruxDoc == null) {
            return null;
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
                                                 int pageSize) throws TypeErrorException {
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

        try {

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

        } catch (Exception e) {
            // Ensure that even if there is an exception, we still close the open DB resource prior to propagating it
            closeDb(db);
            throw e;
        }

    }

    /**
     * Find the entities and relationships that radiate out from the supplied entity GUID.
     * The results are scoped by the provided type GUIDs, other limiters, and the level.
     * @param entityGUID the starting point for the radiation
     * @param entityTypeGUIDs list of entity types to include in the query results (null means include all)
     * @param relationshipTypeGUIDs list of relationship types to include in the query results (null means include all)
     * @param limitResultsByStatus list of statuses to restrict results (null means include all)
     * @param limitResultsByClassification list of classifications that must be present on all returned entities
     * @param asOfTime historical query of the radiated relationships and entities (null means current values)
     * @param level the number of relationships out from the starting entity that
     * @return InstanceGraph
     */
    public InstanceGraph findNeighborhood(String entityGUID,
                                          List<String> entityTypeGUIDs,
                                          List<String> relationshipTypeGUIDs,
                                          List<InstanceStatus> limitResultsByStatus,
                                          List<String> limitResultsByClassification,
                                          Date asOfTime,
                                          int level) {

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (and ensure we close it later).
        ICruxDatasource db;
        if (asOfTime != null) {
            db = cruxAPI.openDB(asOfTime);
        } else {
            db = cruxAPI.openDB();
        }

        try {

            InstanceGraph instanceGraph = new InstanceGraph();

            Set<List<?>> consolidated = new LinkedHashSet<>();

            Set<String> entityGUIDsRetrieved = new HashSet<>();
            Set<String> relationshipGUIDsRetrieved = new HashSet<>();
            Set<String> entityGUIDsVisited = new HashSet<>();
            Set<String> relationshipGUIDsVisited = new HashSet<>();
            List<String> nextEntityGUIDs = new ArrayList<>();
            nextEntityGUIDs.add(entityGUID);

            // Start the InstanceGraph off with the entity starting point that was requested
            // (not clear if this is the intended logic, but follows other repository implementations)
            List<EntityDetail> startingEntities = new ArrayList<>();
            EntityDetail startingEntity = this.getEntity(db, entityGUID);

            if (startingEntity != null) {
                startingEntities.add(startingEntity);
                instanceGraph.setEntities(startingEntities);
                entityGUIDsRetrieved.add(entityGUID);

                int levelTraversed = 0;
                if (level < 0) {
                    // If the level is negative, it means keep going until we run out of new traversals (or crash, presumably?)
                    // We will set a maximum...
                    level = Constants.MAX_TRAVERSAL_DEPTH;
                }
                if (level > 0) {

                    do {
                        Set<List<?>> nextGraph = getNextLevelNeighbors(db,
                                nextEntityGUIDs,
                                entityTypeGUIDs,
                                relationshipTypeGUIDs,
                                limitResultsByStatus,
                                limitResultsByClassification,
                                entityGUIDsVisited,
                                relationshipGUIDsVisited);
                        entityGUIDsVisited.addAll(nextEntityGUIDs);
                        levelTraversed++;
                        // Add this subset of results into the consolidated set of results
                        consolidated.addAll(nextGraph);
                        // Retrieve the next set of entity GUIDs to traverse, but remove any already-visited ones from
                        // the list prior to iterating again
                        nextEntityGUIDs = getEntityGUIDsFromGraphResults(nextGraph);
                        nextEntityGUIDs.removeAll(entityGUIDsVisited);
                        // Once we either run out of GUIDs to traverse, or we've reached the desired level, we stop iterating
                    } while (!nextEntityGUIDs.isEmpty() && levelTraversed < level);
                }

                // TODO: exclude proxies (somehow) from the entity detail list (?)
                InstanceGraph neighbors = resultsToGraph(db, consolidated, entityGUIDsRetrieved, relationshipGUIDsRetrieved);
                if (neighbors != null) {
                    instanceGraph = mergeGraphs(instanceGraph, neighbors);
                }

            }

            // Ensure that we close the open DB resource now that we're finished with it
            closeDb(db);

            return instanceGraph;

        } catch (Exception e) {
            // Ensure that even if there is an exception, we still close the open DB resource prior to propagating it
            closeDb(db);
            throw e;
        }

    }

    /**
     * Find the entities and relationships that radiate out directly from the supplied list of entity GUIDs.
     * The results are scoped by the provided type GUIDs, other limiters, and the level.
     * @param db already opened point-in-time view of the database
     * @param startingPoints list of entity GUIDs from which we should start radiating outwards
     * @param entityTypeGUIDs list of entity types to include in the query results (null means include all)
     * @param relationshipTypeGUIDs list of relationship types to include in the query results (null means include all)
     * @param limitResultsByStatus list of statuses to restrict results (null means include all)
     * @param limitResultsByClassification list of classifications that must be present on all returned entities
     * @return {@code Set<List<?>>} of the immediate neighbors of the specified starting point GUIDs, as graph tuples [[:entityRef :relationshipRef]]
     */
    private Set<List<?>> getNextLevelNeighbors(ICruxDatasource db,
                                               List<String> startingPoints,
                                               List<String> entityTypeGUIDs,
                                               List<String> relationshipTypeGUIDs,
                                               List<InstanceStatus> limitResultsByStatus,
                                               List<String> limitResultsByClassification,
                                               Set<String> entityGUIDsVisited,
                                               Set<String> relationshipGUIDsVisited) {

        Set<List<?>> consolidated = new LinkedHashSet<>();

        // Iterate through the provided starting entity starting points to retrieve the next level of neighbors
        for (String entityGUID : startingPoints) {
            // Results here will be a collection of tuples: [:entity/... :relationship/...]
            Collection<List<?>> nextDegree = findDirectNeighbors(db,
                    entityGUID,
                    entityTypeGUIDs,
                    relationshipTypeGUIDs,
                    limitResultsByStatus,
                    limitResultsByClassification);
            log.debug("Found neighborhood results: {}", nextDegree);
            for (List<?> candidateTuple : nextDegree) {
                Keyword candidateEntityRef = getEntityRefFromGraphTuple(candidateTuple);
                Keyword candidateRelationshipRef = getRelationshipRefFromGraphTuple(candidateTuple);
                String entityGuid = Constants.trimGuidFromReference(candidateEntityRef.toString());
                String relationshipGuid = Constants.trimGuidFromReference(candidateRelationshipRef.toString());
                if (!entityGUIDsVisited.contains(entityGuid) || !relationshipGUIDsVisited.contains(relationshipGuid)) {
                    // If either the entity or the relationship has not been seen, add the tuple
                    consolidated.add(candidateTuple);
                    entityGUIDsVisited.add(entityGUID);
                    relationshipGUIDsVisited.add(relationshipGuid);
                }
            }
        }

        return consolidated;

    }

    /**
     * Find all of the traversals that exist between the provided entities, based on the other provided criteria.
     * @param startEntityGUID from which to start traversing
     * @param endEntityGUID at which to stop traversing
     * @param limitResultsByStatus to limit the entities that are traversed based on their status
     * @param asOfTime to find the traversals for a particular point-in-time
     * @return InstanceGraph containing all of the relationships and entities between the start and end
     * @throws EntityNotKnownException if the requested starting point is not known to the repository
     */
    public InstanceGraph getTraversalsBetweenEntities(String startEntityGUID,
                                                      String endEntityGUID,
                                                      List<InstanceStatus> limitResultsByStatus,
                                                      Date asOfTime) throws EntityNotKnownException {

        final String methodName = "getTraversalsBetweenEntities";

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (and ensure we close it later).
        ICruxDatasource db;
        if (asOfTime != null) {
            db = cruxAPI.openDB(asOfTime);
        } else {
            db = cruxAPI.openDB();
        }

        try {

            InstanceGraph instanceGraph = new InstanceGraph();

            Set<String> entityGUIDsVisited = new HashSet<>();
            Set<String> relationshipGUIDsVisited = new HashSet<>();

            // Start the InstanceGraph off with the entity starting point that was requested
            // (not clear if this is the intended logic, but follows other repository implementations)
            List<EntityDetail> startingEntities = new ArrayList<>();
            EntityDetail startingEntity = this.getEntity(db, startEntityGUID);

            if (startingEntity == null) {
                throw new EntityNotKnownException(CruxOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                        startEntityGUID, repositoryName), this.getClass().getName(), methodName);
            }

            startingEntities.add(startingEntity);
            instanceGraph.setEntities(startingEntities);
            entityGUIDsVisited.add(startEntityGUID);

            Set<String> traversedGuids = new HashSet<>();
            traversedGuids.add(startEntityGUID);
            Set<List<?>> successfulTraversals = traverseToEnd(db,
                    startEntityGUID,
                    endEntityGUID,
                    limitResultsByStatus,
                    traversedGuids,
                    1);

            // TODO: exclude proxies (somehow) from the entity detail list (?)
            InstanceGraph furtherTraversals = resultsToGraph(db, successfulTraversals, entityGUIDsVisited, relationshipGUIDsVisited);
            if (furtherTraversals == null || furtherTraversals.getEntities().isEmpty()) {
                // If there are no entities, return an empty graph
                instanceGraph = null;
            } else {
                instanceGraph = mergeGraphs(instanceGraph, furtherTraversals);
            }

            // Ensure that we close the open DB resource now that we're finished with it
            closeDb(db);

            return instanceGraph;

        } catch (Exception e) {
            // Ensure that even if there is an exception, we still close the open DB resource prior to propagating it
            closeDb(db);
            throw e;
        }

    }

    /**
     * Recursively traverses from the starting entity to the end entity, up to a maximum depth (to avoid potential
     * stack overflow).
     * @param db already opened view of the database at a point-in-time
     * @param startEntityGUID from which to start traversing
     * @param endEntityGUID at which to stop traversing
     * @param limitResultsByStatus to limit which entities are traversed based on their status
     * @param entityGUIDsVisited to avoid traversing the same entity multiple times (for efficiency)
     * @param currentDepth tracks the current depth (once we reach maximum, bail out)
     * @return {@code Set<List<?>>} of the unique combinations of entities and relationships that successfully link between the start and end
     * @see Constants#MAX_TRAVERSAL_DEPTH
     */
    private Set<List<?>> traverseToEnd(ICruxDatasource db,
                                       String startEntityGUID,
                                       String endEntityGUID,
                                       List<InstanceStatus> limitResultsByStatus,
                                       Set<String> entityGUIDsVisited,
                                       int currentDepth) {

        Set<List<?>> consolidated = new LinkedHashSet<>();

        // As long as we have not reached the maximum depth, keep traversing...
        if (currentDepth < Constants.MAX_TRAVERSAL_DEPTH) {
            Collection<List<?>> nextLevel = findDirectNeighbors(db,
                    startEntityGUID,
                    null,
                    null,
                    limitResultsByStatus,
                    null);
            log.debug("Found traversal results: {}", nextLevel);
            Keyword startRef = EntitySummaryMapping.getReference(startEntityGUID);
            Keyword endRef = EntitySummaryMapping.getReference(endEntityGUID);
            if (nextLevel != null && !nextLevel.isEmpty()) {
                // As long as there is something to check in the next level, do so...
                for (List<?> candidateTuple : nextLevel) {
                    Keyword candidateEntityRef = getEntityRefFromGraphTuple(candidateTuple);
                    if (endRef.equals(candidateEntityRef)) {
                        // If we found the endEntityGUID in the results, add it to the set of successful traversals
                        consolidated.add(candidateTuple);
                    } else if (!startRef.equals(candidateEntityRef)) {
                        // Otherwise, so long as we have not circled back to the starting point, continue traversing
                        String nextStartGuid = Constants.trimGuidFromReference(candidateEntityRef.toString());
                        if (!entityGUIDsVisited.contains(nextStartGuid)) {
                            // If we have not already traversed this GUID, continue traversing...
                            entityGUIDsVisited.add(nextStartGuid);
                            Set<List<?>> nextTraversal = traverseToEnd(db,
                                    nextStartGuid,
                                    endEntityGUID,
                                    limitResultsByStatus,
                                    entityGUIDsVisited,
                                    currentDepth + 1);
                            // If the traversal returns a non-empty result, it was successful
                            if (!nextTraversal.isEmpty()) {
                                // So add the traversal up to now (since it led to a successful outcome)
                                consolidated.add(candidateTuple);
                                // And the successful sub-traversal(s) that were returned
                                consolidated.addAll(nextTraversal);
                            }
                        }
                    }
                }
            }
        }

        // return the consolidated set of successful traversals, or null if none were found
        return consolidated;

    }

    /**
     * Merge the provided instance graphs into a single consolidated graph, without any duplicates.
     * @param one first graph to merge
     * @param two second graph to merge
     * @return InstanceGraph containing all elements from both graphs, only once
     */
    private InstanceGraph mergeGraphs(InstanceGraph one, InstanceGraph two) {
        InstanceGraph consolidated = new InstanceGraph();
        List<EntityDetail> oneEntities = one.getEntities();
        List<EntityDetail> twoEntities = two.getEntities();
        if (oneEntities != null) {
            if (twoEntities != null) {
                oneEntities.removeAll(twoEntities);
                oneEntities.addAll(twoEntities);
            }
        } else {
            oneEntities = twoEntities;
        }
        consolidated.setEntities(oneEntities);
        if (oneEntities != null) {
            log.debug("Merged entities: {}", oneEntities.stream().map(EntityDetail::getGUID).collect(Collectors.toList()));
        }
        List<Relationship> oneRelationships = one.getRelationships();
        List<Relationship> twoRelationships = two.getRelationships();
        if (oneRelationships != null) {
            if (twoRelationships != null) {
                oneRelationships.removeAll(twoRelationships);
                oneRelationships.addAll(twoRelationships);
            }
        } else {
            oneRelationships = twoRelationships;
        }
        consolidated.setRelationships(oneRelationships);
        if (oneRelationships != null) {
            log.debug("Merged relationships: {}", oneRelationships.stream().map(Relationship::getGUID).collect(Collectors.toList()));
        }
        return consolidated;
    }

    /**
     * Translate the collection of Crux tuple results (from a graph query) into an Egeria InstanceGraph.
     * @param db already opened point-in-time view of the database
     * @param cruxResults list of result tuples, eg. from a neighborhood or other graph search
     * @return InstanceGraph
     * @see #findNeighborhood(String, List, List, List, List, Date, int)
     */
    private InstanceGraph resultsToGraph(ICruxDatasource db,
                                         Collection<List<?>> cruxResults,
                                         Set<String> entityGUIDsVisited,
                                         Set<String> relationshipGUIDsVisited) {
        InstanceGraph results = null;
        if (cruxResults != null) {
            List<Relationship> relationships = new ArrayList<>();
            List<EntityDetail> entities = new ArrayList<>();
            for (List<?> cruxResult : cruxResults) {
                Keyword entityRef = getEntityRefFromGraphTuple(cruxResult);
                String entityGuid = Constants.trimGuidFromReference(entityRef.toString());
                if (!entityGUIDsVisited.contains(entityGuid)) {
                    EntityDetail entity = getEntityByRef(db, entityRef);
                    if (entity == null) {
                        log.warn("Unable to resolve search result into entity: {}", cruxResult);
                    } else {
                        entities.add(entity);
                    }
                }
                Keyword relationshipRef = getRelationshipRefFromGraphTuple(cruxResult);
                String relationshipGuid = Constants.trimGuidFromReference(relationshipRef.toString());
                if (!relationshipGUIDsVisited.contains(relationshipGuid)) {
                    Relationship relationship = getRelationshipByRef(db, relationshipRef);
                    if (relationship == null) {
                        log.warn("Unable to resolve search result into relationship: {}", cruxResult);
                    } else {
                        relationships.add(relationship);
                    }
                }
            }
            results = new InstanceGraph();
            results.setEntities(entities);
            results.setRelationships(relationships);
        }
        return results;
    }

    /**
     * Retrieve the unique set of entity GUIDs from the provided graph query results.
     * @param cruxResults graph query results
     * @return {@code List<String>}
     */
    private List<String> getEntityGUIDsFromGraphResults(Collection<List<?>> cruxResults) {
        List<String> list = new ArrayList<>();
        for (List<?> result : cruxResults) {
            Keyword entityRef = getEntityRefFromGraphTuple(result);
            if (entityRef != null) {
                String guid = Constants.trimGuidFromReference(entityRef.toString());
                if (!list.contains(guid)) {
                    list.add(guid);
                }
            }
        }
        return list;
    }

    /**
     * Retrieve the entity reference from the provided graph query result.
     * @param tuple graph query result
     * @return Keyword reference for the entity
     */
    private Keyword getEntityRefFromGraphTuple(List<?> tuple) {
        return tuple == null ? null : (Keyword) tuple.get(0);
    }

    /**
     * Retrieve the relationship reference from the provided graph query result.
     * @param tuple graph query result
     * @return Keyword reference for the relationship
     */
    private Keyword getRelationshipRefFromGraphTuple(List<?> tuple) {
        return tuple == null ? null : (Keyword) tuple.get(1);
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

        try {

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

        } catch (Exception e) {
            // Ensure that even if there is an exception, we still close the open DB resource prior to propagating it
            closeDb(db);
            throw e;
        }

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
     * @see CruxOMRSMetadataCollection#findRelationshipsByPropertyValue(String, String, String, int, List, Date, String, SequencingOrder, int)
     */
    public List<Relationship> findRelationshipsByText(String relationshipTypeGUID,
                                                      String searchCriteria,
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

        try {

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

        } catch (Exception e) {
            // Ensure that even if there is an exception, we still close the open DB resource prior to propagating it
            closeDb(db);
            throw e;
        }

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
                Relationship relationship = getRelationshipByRef(db, docRef);
                if (relationship == null) {
                    log.warn("Unable to translate Crux result into Relationship: {}", cruxResult);
                } else {
                    results.add(relationship);
                }
            }
        }
        return results;
    }

    /**
     * Translate the provided Crux document reference into an Egeria relationship.
     * @param db already opened point-in-time view of the database
     * @param ref reference to the relationship document
     * @return Relationship
     */
    private Relationship getRelationshipByRef(ICruxDatasource db, Keyword ref) {
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(db, ref);
        if (cruxDoc != null) {
            RelationshipMapping rm = new RelationshipMapping(this, cruxDoc, db);
            return rm.toEgeria();
        }
        return null;
    }

    /**
     * Translate the provided Crux document reference into an Egeria relationship.
     * @param db already opened point-in-time view of the database
     * @param ref reference to the relationship document
     * @return Relationship
     */
    private EntityDetail getEntityByRef(ICruxDatasource db, Keyword ref) {
        Map<Keyword, Object> cruxDoc = getCruxObjectByReference(db, ref);
        if (cruxDoc != null) {
            EntityDetailMapping edm = new EntityDetailMapping(this, cruxDoc);
            return edm.toEgeria();
        }
        return null;
    }

    /**
     * Create the provided relationship instance in the Crux repository.
     * @param relationship to create
     * @return Relationship that was created
     */
    public Relationship createRelationship(Relationship relationship) {
        Map<Keyword, ?> results = runTx(getCreateRelationshipStatements(relationship));
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
        runTx(getPurgeRelationshipStatements(guid));
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

        try {

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

        } catch (Exception e) {
            // Ensure that even if there is an exception, we still close the open DB resource prior to propagating it
            closeDb(db);
            throw e;
        }

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
        List<Map<Keyword, Object>> history = getPreviousVersion(docRef);
        if (history != null && history.size() > 1) {
            // There must be a minimum of two entries in the history for us to have a previous version to go to.
            EntityDetailMapping edmC = new EntityDetailMapping(this, history.get(0));
            EntityDetail current = edmC.toEgeria();
            long currentVersion = current.getVersion();
            EntityDetailMapping edmP = new EntityDetailMapping(this, history.get(1));
            EntityDetail restored = edmP.toEgeria();
            // Update the version of the restored instance to be one more than the latest (current) version, the update
            // time to reflect now (so we have an entirely new record in history that shows as the latest (current)),
            // and the last user to update it to the user that requested this restoration
            restored.setVersion(currentVersion + 1);
            restored.setUpdateTime(new Date());
            restored.setUpdatedBy(userId);
            List<String> maintainedBy = restored.getMaintainedBy();
            if (maintainedBy != null && !maintainedBy.contains(userId)) {
                maintainedBy.add(userId);
            }
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

        try {
            List<Map<Keyword, Object>> history = getPreviousVersion(db, docRef);
            Relationship restored = null;
            if (history != null && history.size() > 1) {
                // There must be a minimum of two entries in the history for us to have a previous version to go to.
                RelationshipMapping rmC = new RelationshipMapping(this, history.get(0), db);
                Relationship current = rmC.toEgeria();
                long currentVersion = current.getVersion();
                RelationshipMapping rmP = new RelationshipMapping(this, history.get(1), db);
                restored = rmP.toEgeria();
                // Update the version of the restored instance to be one more than the latest (current) version, the update
                // time to reflect now (so we have an entirely new record in history that shows as the latest (current)),
                // and the last user to update it to the user that requested this restoration
                restored.setVersion(currentVersion + 1);
                restored.setUpdateTime(new Date());
                restored.setUpdatedBy(userId);
                // Then submit this version back into Crux as an update
                restored = updateRelationship(restored);
            }
            // Ensure that we close the open DB resource now that we're finished with it (whether we found something or not)
            closeDb(db);
            return restored;
        } catch (Exception e) {
            // Ensure that even if there is an exception, we still close the open DB resource prior to propagating it
            closeDb(db);
            throw e;
        }

    }

    /**
     * Retrieve only the current and previous versions for the provided Crux object, lazily instantiated so that we do
     * not need to pull back the entire history for an object that has changed many times.
     * @param reference indicating the primary key of the object for which to retrieve the previous version
     * @return {@code List<Map<Keyword, Object>>} with the current version as the first element, and the the previous version as the second element (or null if there is no previous version)
     */
    private List<Map<Keyword, Object>> getPreviousVersion(Keyword reference) {

        HistoryOptions options = HistoryOptions.create(HistoryOptions.SortOrder.DESC);
        List<Map<Keyword, Object>> results = null;

        // try-with to ensure that the ICursor resource is closed, even if any exception is thrown
        try (ICursor<Map<Keyword, ?>> lazyCursor = cruxAPI.db().openEntityHistory(reference, options)) {
            results = getPreviousVersionFromCursor(lazyCursor, reference);
        } catch (Exception e) {
            log.error("Unable to lazily query history for: {}", reference, e);
        }

        return results;

    }

    /**
     * Retrieve only the current and previous versions for the provided Crux object, from an already-opened
     * point-in-time view of the repository, lazily instantiated so that we do not need to pull back the entire history
     * for an object that has changed many times.
     * @param db from which to retrieve the previous version
     * @param reference indicating the primary key of the object for which to retrieve the previous version
     * @return {@code List<Map<Keyword, Object>>} with the current version as the first element, and the previous version as the second element (or null if there is no previous version)
     */
    private List<Map<Keyword, Object>> getPreviousVersion(ICruxDatasource db, Keyword reference) {

        HistoryOptions options = HistoryOptions.create(HistoryOptions.SortOrder.DESC);
        List<Map<Keyword, Object>> results = null;

        // try-with to ensure that the ICursor resource is closed, even if any exception is thrown
        try (ICursor<Map<Keyword, ?>> lazyCursor = db.openEntityHistory(reference, options)) {
            // Note that here we will not pass-through the opened DB as this method will need to retrieve a different
            // point-in-time view of the details of each entity anyway (based on the transaction dates from the cursor,
            // rather than the already-opened DB resource)
            results = getPreviousVersionFromCursor(lazyCursor, reference);
        } catch (Exception e) {
            log.error("Unable to lazily query history for: {}", reference, e);
        }

        return results;

    }

    /**
     * Retrieve only the current and previous versions of the provided Crux reference, from an already-opened lazily-
     * evaluated cursor.
     * @param cursor from which to lazily-evaluate the current and previous versions
     * @param reference indicating the primary key of the object for which to retrieve the current and previous version
     * @return {@code List<Map<Keyword, Object>>} with the current version as the first element, and the previous version as the second element (or null if there is no previous version)
     */
    private List<Map<Keyword, Object>> getPreviousVersionFromCursor(ICursor<Map<Keyword, ?>> cursor, Keyword reference) {
        List<Map<Keyword, Object>> results = new ArrayList<>();
        // History entries themselves will just be transaction details like the following:
        // { :crux.tx/tx-time #inst "2021-02-01T00:28:32.533-00:00",
        //   :crux.tx/tx-id 2,
        //   :crux.db/valid-time #inst "2021-02-01T00:28:32.531-00:00",
        //   :crux.db/content-hash #crux/id "..." }
        if (cursor != null) {
            if (cursor.hasNext()) {
                Map<Keyword, ?> currentVersionTxn = cursor.next();
                if (cursor.hasNext()) {
                    // ... so use these transaction details to retrieve the actual objects, and return those
                    Map<Keyword, ?> previousVersionTxn = cursor.next();
                    Map<Keyword, Object> current = getCruxObjectByReference(reference, currentVersionTxn);
                    if (current != null) {
                        results.add(current);
                    }
                    Map<Keyword, Object> previous = getCruxObjectByReference(reference, previousVersionTxn);
                    if (previous != null) {
                        results.add(previous);
                    }
                }
            }
        }
        return results;
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
    public Map<Keyword, Object> getCruxObjectByReference(Keyword reference, Map<Keyword, ?> txnDetails) {
        Object oValid = txnDetails.get(Constants.CRUX_VALID_TIME);
        Object oTxn   = txnDetails.get(Constants.CRUX_TX_TIME);
        if (oValid instanceof Date && oTxn instanceof Date) {
            return cruxAPI.db((Date) oValid, (Date) oTxn).entity(reference);
        }
        return null;
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
        CruxQuery query = new CruxQuery(getMaxPageSize());
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
        CruxQuery query = new CruxQuery(getMaxPageSize());
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
                                                String namespace) throws TypeErrorException {
        CruxQuery query = new CruxQuery(getMaxPageSize());
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
                                                String namespace) throws TypeErrorException {
        CruxQuery query = new CruxQuery(getMaxPageSize());
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
        CruxQuery query = new CruxQuery(getMaxPageSize());
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
     * Find the immediate neighbors (1-degree separated entities and the relationships between) using the provided criteria.
     * @param db already opened point-in-view of the database
     * @param entityGUID of the entity for which to find immediate relationships
     * @param entityTypeGUIDs of the entity type definitions by which to restrict entities in the results
     * @param relationshipTypeGUIDs of the relationship type definitions by which to restrict relationships in the results
     * @param limitResultsByStatus by which to limit relationships
     * @param limitResultsByClassification by which to limit the entities in the results
     * @return {@code Collection<List<?>>} of tuples of relationships and entities found in the results
     */
    public Collection<List<?>> findDirectNeighbors(ICruxDatasource db,
                                                   String entityGUID,
                                                   List<String> entityTypeGUIDs,
                                                   List<String> relationshipTypeGUIDs,
                                                   List<InstanceStatus> limitResultsByStatus,
                                                   List<String> limitResultsByClassification) {
        CruxGraphQuery query = new CruxGraphQuery(getMaxPageSize());
        query.addEntityAnchorCondition(entityGUID);
        query.addRelationshipLimiters(relationshipTypeGUIDs, limitResultsByStatus);
        query.addEntityLimiters(entityTypeGUIDs, limitResultsByClassification);
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
                                 String namespace) throws TypeErrorException {
        query.addTypeCondition(typeGuid, null);
        query.addTypeDefCategoryCondition(category);
        Set<String> completeTypeSet = getCompleteSetOfTypeNamesForSearch(typeGuid, null, namespace);
        query.addClassificationConditions(matchClassifications, completeTypeSet, repositoryHelper, repositoryName);
        if (luceneConfigured) {
            query.addWildcardLuceneCondition(searchCriteria, repositoryHelper, repositoryName, completeTypeSet, namespace);
        } else {
            query.addWildcardTextCondition(searchCriteria, repositoryHelper, repositoryName, completeTypeSet, namespace);
        }
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
                        List<TypeDef> typeDefinitions = metadataCollection.findTypeDefsByCategory(null, TypeDefCategory.RELATIONSHIP_DEF);
                        if (typeDefinitions != null) {
                            for (TypeDef typeDef : typeDefinitions) {
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
    public Map<Keyword, ?> runTx(List<List<?>> statements) {
        log.debug("{} transacting with: {}", synchronousIndex ? SYNC : ASYNC, statements);
        Map<Keyword, ?> tx = cruxAPI.submitTx(statements);
        // Null for the timeout here means use the default (which is therefore configurable directly by the Crux
        // configurationProperties of the connector)
        if (synchronousIndex) {
            return cruxAPI.awaitTx(tx, null);
        } else {
            return tx;
        }
    }

    /**
     * Retrieve the statements needed to permanently remove a document (and its history) from the repository.
     * @param docRef giving the primary key of the document to permanently remove
     * @return {@code List<List<?>>} of statements
     */
    private List<List<?>> getEvictDocStatements(Keyword docRef) {
        List<List<?>> statements = new ArrayList<>();
        statements.add(Constants.evict(docRef));
        return statements;
    }

    /**
     * Close the point-in-time view of the database to allow its resources to be released.
     * @param db to close
     */
    private void closeDb(ICruxDatasource db) {
        try {
            db.close();
        } catch (IOException e) {
            log.error("Unable to close the open DB resource.", e);
        }
    }

}
