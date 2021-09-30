/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector;

import clojure.lang.*;
import org.odpi.egeria.connectors.juxt.xtdb.cache.ErrorMessageCache;
import org.odpi.egeria.connectors.juxt.xtdb.txnfn.*;
import xtdb.api.*;
import xtdb.api.tx.Transaction;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.*;
import org.odpi.egeria.connectors.juxt.xtdb.model.PersistenceLayer;
import org.odpi.egeria.connectors.juxt.xtdb.model.search.XtdbGraphQuery;
import org.odpi.egeria.connectors.juxt.xtdb.model.search.XtdbQuery;
import org.odpi.egeria.connectors.juxt.xtdb.model.search.TextConditionBuilder;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.HistorySequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.MatchCriteria;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.PropertyComparisonOperator;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.PropertyCondition;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchClassifications;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.PrimitiveDefCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.ffdc.OMRSErrorCode;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Provides all connectivity and API-based interaction with a XTDB back-end.
 */
public class XtdbOMRSRepositoryConnector extends OMRSRepositoryConnector implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(XtdbOMRSRepositoryConnector.class);

    private static final long serialVersionUID = 1L;

    private static final String SYNC = "Synchronously";
    private static final String ASYNC = "Asynchronously";

    private IXtdb xtdbAPI = null;
    private boolean luceneConfigured = false;
    private boolean synchronousIndex = true;
    private boolean luceneRegexes = true;

    /**
     * Default constructor used by the OCF Connector Provider.
     */
    public XtdbOMRSRepositoryConnector() {
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
                metadataCollection = new XtdbOMRSMetadataCollection(this,
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
    @SuppressWarnings("unchecked")
    public synchronized void start() throws ConnectorCheckedException {

        super.start();
        final String methodName = "start";

        auditLog.logMessage(methodName, XtdbOMRSAuditCode.REPOSITORY_NODE_STARTING.getMessageDefinition());

        // Retrieve the configuration from the configurationProperties, and serialise it directly into a .json file
        File configFile = null;
        Map<?, ?> configMap = null;
        Map<String, Object> configProperties = connectionProperties.getConfigurationProperties();
        if (configProperties != null && !configProperties.isEmpty()) {
            if (configProperties.containsKey(XtdbOMRSRepositoryConnectorProvider.XTDB_CONFIG)) {
                // JSON-style configuration
                Object xtdbCfg = configProperties.get(XtdbOMRSRepositoryConnectorProvider.XTDB_CONFIG);
                if (xtdbCfg instanceof Map) {
                    Map<String, Object> xtdbConfig = (Map<String, Object>) xtdbCfg;
                    // Dynamically set whether Lucene is configured or not based on the presence of its configuration in
                    // the configurationProperties
                    luceneConfigured = xtdbConfig.containsKey(Constants.XTDB_LUCENE);
                    // If Lucene is configured, inject the custom analyzers and indexers required by Egeria
                    if (luceneConfigured) {
                        Object luceneCfg = xtdbConfig.get(Constants.XTDB_LUCENE);
                        if (luceneCfg instanceof Map) {
                            Map<String, Object> luceneConfig = (Map<String, Object>) luceneCfg;
                            Map<String, String> indexer = new HashMap<>();
                            indexer.put("xtdb/module", "xtdb.lucene.egeria/->egeria-indexer");
                            luceneConfig.put("indexer", indexer);
                            Map<String, String> analyzer = new HashMap<>();
                            analyzer.put("xtdb/module", "xtdb.lucene.egeria/->ci-analyzer");
                            luceneConfig.put("analyzer", analyzer);
                            // Override the Lucene configuration with these injected customizations
                            xtdbConfig.put(Constants.XTDB_LUCENE, luceneConfig);
                        }
                    }
                    configMap = xtdbConfig;
                }
            }
            if (configProperties.containsKey(XtdbOMRSRepositoryConnectorProvider.XTDB_CONFIG_EDN)) {
                // EDN-style configuration
                try {
                    configFile = File.createTempFile("xtdb", ".edn", new File("./"));
                    String xtdbCfg = (String) configProperties.get(XtdbOMRSRepositoryConnectorProvider.XTDB_CONFIG_EDN);
                    luceneConfigured = xtdbCfg.contains(Constants.XTDB_LUCENE);
                    BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
                    writer.write(xtdbCfg);
                    writer.close();
                } catch (IOException e) {
                    auditLog.logException(methodName, XtdbOMRSAuditCode.CANNOT_READ_CONFIGURATION.getMessageDefinition(e.getClass().getName()), e);
                    throw new ConnectorCheckedException(XtdbOMRSErrorCode.CANNOT_READ_CONFIGURATION.getMessageDefinition(repositoryName),
                            this.getClass().getName(), methodName, e);
                }
            }
            if (configProperties.containsKey(XtdbOMRSRepositoryConnectorProvider.SYNCHRONOUS_INDEX)) {
                Object syncIdx = configProperties.get(XtdbOMRSRepositoryConnectorProvider.SYNCHRONOUS_INDEX);
                if (syncIdx instanceof Boolean) {
                    synchronousIndex = (Boolean) syncIdx;
                }
            }
            if (configProperties.containsKey(XtdbOMRSRepositoryConnectorProvider.LUCENE_REGEXES)) {
                Object luceneReg = configProperties.get(XtdbOMRSRepositoryConnectorProvider.LUCENE_REGEXES);
                if (luceneReg instanceof Boolean) {
                    luceneRegexes = (Boolean) luceneReg;
                }
            }
        }

        try {

            if (configMap == null && configFile == null) {
                // If no configuration options were specified, we will start an in-memory node
                auditLog.logMessage(methodName, XtdbOMRSAuditCode.REPOSITORY_NODE_STARTING_NO_CONFIG.getMessageDefinition());
                xtdbAPI = IXtdb.startNode();
            } else if (configMap != null) {
                // If the map (JSON-style) is populated, use that to start the server
                auditLog.logMessage(methodName, XtdbOMRSAuditCode.REPOSITORY_NODE_STARTING_WITH_CONFIG.getMessageDefinition());
                log.debug("Starting XTDB with configuration: {}", configMap);
                xtdbAPI = IXtdb.startNode(configMap);
            } else {
                // Otherwise, fall-back to configuring based on the EDN-style config
                auditLog.logMessage(methodName, XtdbOMRSAuditCode.REPOSITORY_NODE_STARTING_WITH_CONFIG.getMessageDefinition());
                log.debug("Starting XTDB with configuration: {}", configFile);
                xtdbAPI = IXtdb.startNode(configFile);
                Files.delete(Paths.get(configFile.getCanonicalPath()));
            }
            Map<Keyword, ?> details = xtdbAPI.status();
            log.info("xtdb config details: {}", details);
            Object version = details.get(Constants.XTDB_VERSION);
            long persistenceVersion = PersistenceLayer.getVersion(xtdbAPI);
            boolean emptyDataStore = isDataStoreEmpty();
            if (persistenceVersion == -1 && emptyDataStore) {
                // If there is no persistence layer defined, and there is no metadata stored yet, mark the
                // version per this connector
                PersistenceLayer.setVersion(xtdbAPI, PersistenceLayer.LATEST_VERSION);
            } else if (persistenceVersion != PersistenceLayer.LATEST_VERSION) {
                // Otherwise, there is something in the data store already (the persistence layer details, and / or
                // pre-existing metadata), so if the versions do not match we must exit to ensure integrity of the data
                xtdbAPI.close();
                throw new ConnectorCheckedException(XtdbOMRSErrorCode.PERSISTENCE_LAYER_MISMATCH.getMessageDefinition("" + persistenceVersion, "" + PersistenceLayer.LATEST_VERSION),
                        this.getClass().getName(), methodName);
            }
            List<String> opts = new ArrayList<>();
            opts.add(synchronousIndex ? "synchronous indexing" : "asynchronous indexing");
            if (luceneConfigured) {
                opts.add("Lucene text index");
                if (luceneRegexes)
                    opts.add("Lucene regexes");
            }
            auditLog.logMessage(methodName,
                    XtdbOMRSAuditCode.REPOSITORY_SERVICE_STARTED.getMessageDefinition(
                            version == null ? "<null>" : version.toString(),
                            String.join(", ", opts)));
        } catch (Exception e) {
            auditLog.logException(methodName, XtdbOMRSAuditCode.FAILED_REPOSITORY_STARTUP.getMessageDefinition(e.getClass().getName()), e);
            throw new ConnectorCheckedException(XtdbOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }

        // Ensure the latest version of the transaction functions is committed to the repository at
        // every startup (these should all be idempotent operations if the functions already exist)
        Transaction.Builder tx = Transaction.builder();
        AddEntityProxy.create(tx);
        UpdateEntityStatus.create(tx);
        UpdateEntityProperties.create(tx);
        UndoEntityUpdate.create(tx);
        RestoreEntity.create(tx);
        ClassifyEntity.create(tx);
        DeclassifyEntity.create(tx);
        UpdateEntityClassification.create(tx);
        AddRelationship.create(tx);
        UpdateRelationshipStatus.create(tx);
        UpdateRelationshipProperties.create(tx);
        UndoRelationshipUpdate.create(tx);
        RestoreRelationship.create(tx);
        DeleteRelationship.create(tx);
        PurgeRelationship.create(tx);
        DeleteEntity.create(tx);
        PurgeEntity.create(tx);
        ReLinkRelationship.create(tx);
        ReIdentifyEntity.create(tx);
        ReIdentifyRelationship.create(tx);
        ReTypeEntity.create(tx);
        ReTypeRelationship.create(tx);
        ReHomeEntity.create(tx);
        ReHomeRelationship.create(tx);
        SaveEntityReferenceCopy.create(tx);
        SaveClassificationReferenceCopy.create(tx);
        SaveRelationshipReferenceCopy.create(tx);
        PurgeClassificationReferenceCopy.create(tx);
        // Null for the timeout here means use the default (which is therefore configurable directly by
        // the XTDB configurationProperties of the connector)
        Transaction txn = tx.build();
        log.info("Adding transaction functions: {}", txn.toVector());
        TransactionInstant instant = xtdbAPI.submitTx(txn);
        xtdbAPI.awaitTx(instant, null);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void disconnect() throws ConnectorCheckedException {

        final String methodName = "disconnect";
        super.disconnect();

        // Ready the embedded XTDB node for GC
        try {
            this.xtdbAPI.close();
        } catch (IOException e) {
            if (auditLog != null)
                auditLog.logException(methodName, XtdbOMRSAuditCode.FAILED_REPOSITORY_SHUTDOWN.getMessageDefinition(e.getClass().getName()), e);
            throw new ConnectorCheckedException(XtdbOMRSErrorCode.FAILED_DISCONNECT.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }
        if (auditLog != null)
            auditLog.logMessage(methodName, XtdbOMRSAuditCode.REPOSITORY_SERVICE_SHUTDOWN.getMessageDefinition(getServerName()));

    }

    /**
     * Log a problem with the connector, preferring the audit log so long as it is available and only falling
     * back to debug-level logging if it is not.
     * @param className where the problem occurred
     * @param methodName where the problem occurred
     * @param code describing the problem
     * @param cause the exception that triggered the problem (if any)
     * @param params providing additional details about the problem
     */
    public void logProblem(String className, String methodName, XtdbOMRSAuditCode code, Throwable cause, String... params) {
        String location = className + "::" + methodName;
        if (auditLog != null) {
            if (cause != null) {
                auditLog.logException(location, code.getMessageDefinition(params), cause);
            } else {
                auditLog.logMessage(location, code.getMessageDefinition(params));
            }
        } else {
            log.error("No audit log available -- problem during {}: {}", location, code.getMessageDefinition(params), cause);
        }
    }

    /**
     * Checks whether the data store is currently empty.
     * @return true of the data store is empty (has no metadata stored), otherwise false
     */
    public boolean isDataStoreEmpty() {
        XtdbQuery query = new XtdbQuery();
        List<IPersistentCollection> conditions = new ArrayList<>();
        conditions.add(PersistentVector.create(XtdbQuery.DOC_ID, Keyword.intern(InstanceAuditHeaderMapping.METADATA_COLLECTION_ID), Symbol.intern("_")));
        query.addConditions(conditions);
        IPersistentMap q = query.getQuery();
        q = q.assoc(Keyword.intern("limit"), 1);
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = xtdbAPI.db().query(q);
        return results == null || results.isEmpty();
    }

    /**
     * Validate that the commit was persisted, or throw an exception if it failed.
     * @param instant giving the commit point
     * @param methodName that made the commit
     * @throws Exception on any error
     */
    public void validateCommit(TransactionInstant instant, String methodName) throws Exception {
        if (synchronousIndex) {
            if (!xtdbAPI.hasTxCommitted(instant)) {
                Exception e = ErrorMessageCache.get(instant.getId());
                if (e != null) {
                    throw e;
                } else {
                    throw new RepositoryErrorException(XtdbOMRSErrorCode.UNKNOWN_RUNTIME_ERROR.getMessageDefinition(),
                            this.getClass().getName(),
                            methodName);
                }
            }
        }
    }

    /**
     * Validates that the commit was persisted (if synchronous), throwing an exception if it failed, and
     * also retrieves and returns the detailed entity that resulted from the transaction. Note that if the
     * operation is configured to be asynchronous, this will ALWAYS return null for the entity details.
     * @param docId of the entity within XTDB itself (i.e. prefixed)
     * @param instant giving the commit point of the transaction
     * @param methodName that made the commit
     * @return EntityDetail result of the committed transaction (synchronous) or null (asynchronous)
     * @throws Exception on any error
     */
    public EntityDetail getResultingEntity(String docId,
                                           TransactionInstant instant,
                                           String methodName) throws Exception {
        validateCommit(instant, methodName);
        if (synchronousIndex) {
            XtdbDocument result = getXtdbObjectByReference(docId);
            EntityDetailMapping edm = new EntityDetailMapping(this, result);
            return edm.toEgeria();
        } else {
            // For async write we will ALWAYS return null, as there cannot be any consistent idea
            // of what the object looks like before the write itself has completed
            return null;
        }
    }

    /**
     * Validates that the commit was persisted (if synchornous), throwing an exception if it failed, and
     * also retrieves and returns the detailed relationship that resulted from the transaction. Note that if
     * the operation is configured to be asynchronous, this will ALWAYS return null for the relationship detials.
     * @param docId of the relationship within XTDB itself (i.e. prefixed)
     * @param instant giving the commit point of the transaction
     * @param methodName that made the commit
     * @return Relationship result of the committed transaction (synchronous) or null (asynchronous)
     * @throws Exception on any error
     */
    public Relationship getResultingRelationship(String docId,
                                                 TransactionInstant instant,
                                                 String methodName) throws Exception {
        validateCommit(instant, methodName);
        if (synchronousIndex) {
            try (IXtdbDatasource db = xtdbAPI.openDB(instant)) {
                XtdbDocument result = getXtdbObjectByReference(db, docId);
                RelationshipMapping rm = new RelationshipMapping(this, result, db);
                return rm.toEgeria();
            } catch (IOException e) {
                throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                        this.getClass().getName(), methodName, e);
            }
        } else {
            // For async write we will ALWAYS return null, as there cannot be any consistent idea
            // of what the object looks like before the write itself has completed
            return null;
        }
    }

    /**
     * Create the provided entity instance in the XTDB repository.
     * @param entity to create
     * @return EntityDetail that was created
     */
    public EntityDetail createEntity(EntityDetail entity) {
        Transaction.Builder tx = Transaction.builder();
        addCreateEntityStatements(tx, entity);
        TransactionInstant results = runTx(tx.build());
        log.debug(Constants.WRITE_RESULTS, results);
        return entity;
    }

    /**
     * Retrieve the statements that need to be executed against XTDB to create (persist) the entity provided.
     * @param tx the transaction through which to create the entity
     * @param entity to be created
     */
    public void addCreateEntityStatements(Transaction.Builder tx, EntityDetail entity) {
        EntityDetailMapping edm = new EntityDetailMapping(this, entity);
        put(tx, edm.toXTDB());
    }

    /**
     * Retrieve the requested entity, as a proxy, from the XTDB repository.
     * @param guid of the entity to retrieve
     * @return EntityProxy of the current version of the entity
     */
    public EntityProxy getEntityProxy(String guid) {
        XtdbDocument xtdbDoc = getXtdbObjectByReference(EntityProxyMapping.getReference(guid));
        return EntityProxyMapping.getFromDoc(this, xtdbDoc);
    }

    /**
     * Retrieve the requested entity from the XTDB repository.
     * @param guid of the entity to retrieve
     * @return EntitySummary of the current version of the entity
     */
    public EntitySummary getEntitySummary(String guid) {
        XtdbDocument xtdbDoc = getXtdbObjectByReference(EntitySummaryMapping.getReference(guid));
        if (log.isDebugEnabled())
            log.debug(Constants.FOUND_RESULTS, xtdbDoc == null ? null : xtdbDoc.toMap());
        EntitySummaryMapping esm = new EntitySummaryMapping(this, xtdbDoc);
        return esm.toEgeria();
    }

    /**
     * Retrieve the requested entity from the XTDB repository.
     * @param guid of the entity to retrieve
     * @param asOfTime view of the entity at this particular point in time
     * @param acceptProxies if true, allow proxies to be returned as EntityDetails
     * @return EntityDetail as it existed at the specified point in time
     * @throws EntityProxyOnlyException if the entity requested is only an EntityProxy (and acceptProxies is false)
     */
    public EntityDetail getEntity(String guid, Date asOfTime, boolean acceptProxies) throws EntityProxyOnlyException {
        final String methodName = "getEntity";
        XtdbDocument xtdbDoc = getXtdbObjectByReference(EntityDetailMapping.getReference(guid), asOfTime);
        if (log.isDebugEnabled())
            log.debug(Constants.FOUND_RESULTS, xtdbDoc == null ? null : xtdbDoc.toMap());
        if (xtdbDoc == null) {
            return null;
        }
        if (!acceptProxies && EntityProxyMapping.isOnlyAProxy(xtdbDoc)) {
            throw new EntityProxyOnlyException(
                    XtdbOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                            guid, repositoryName
                    ),
                    this.getClass().getName(),
                    methodName
            );
        }
        EntityDetailMapping edm = new EntityDetailMapping(this, xtdbDoc);
        return edm.toEgeria();
    }

    /**
     * Retrieve the requested entity from the XTDB repository.
     * @param db already opened point-in-time view of the database
     * @param guid of the entity to retrieve
     * @return EntityDetail as it existed at the specified database's point-in-time view
     */
    public EntityDetail getEntityByGuid(IXtdbDatasource db, String guid) {
        return getEntityByRef(db, EntityDetailMapping.getReference(guid));
    }

    /**
     * Retrieve the requested entity from the XTDB repository.
     * @param db already opened point-in-time view of the database
     * @param ref of the entity to retrieve
     * @return EntityDetail as it existed at the specified database's point-in-time view
     */
    private EntityDetail getEntityByRef(IXtdbDatasource db, String ref) {
        XtdbDocument xtdbDoc = getXtdbObjectByReference(db, ref);
        if (xtdbDoc == null) {
            return null;
        }
        EntityDetailMapping edm = new EntityDetailMapping(this, xtdbDoc);
        return edm.toEgeria();
    }

    /**
     * Search based on the provided parameters.
     * @param entityTypeGUID see XtdbOMRSMetadataCollection#findEntities
     * @param entitySubtypeGUIDs see XtdbOMRSMetadataCollection#findEntities
     * @param matchProperties see XtdbOMRSMetadataCollection#findEntities
     * @param fromEntityElement see XtdbOMRSMetadataCollection#findEntities
     * @param limitResultsByStatus see XtdbOMRSMetadataCollection#findEntities
     * @param matchClassifications see XtdbOMRSMetadataCollection#findEntities
     * @param asOfTime see XtdbOMRSMetadataCollection#findEntities
     * @param sequencingProperty see XtdbOMRSMetadataCollection#findEntities
     * @param sequencingOrder see XtdbOMRSMetadataCollection#findEntities
     * @param pageSize see XtdbOMRSMetadataCollection#findEntities
     * @param userId of the user running the query
     * @return {@code List<EntityDetail>}
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws RepositoryTimeoutException if the query runs longer than the defined threshold (default: 30s)
     * @see XtdbOMRSMetadataCollection#findEntities(String, String, List, SearchProperties, int, List, SearchClassifications, Date, String, SequencingOrder, int)
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
                                           int pageSize,
                                           String userId) throws TypeErrorException, RepositoryTimeoutException {
        final String methodName = "findEntities";
        try {
            Collection<List<?>> xtdbResults = searchXtdb(
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
                    EntityDetailMapping.ENTITY_PROPERTIES_NS,
                    userId
            );
            log.debug(Constants.FOUND_RESULTS, xtdbResults);
            return translateEntityResults(xtdbResults, asOfTime);
        } catch (TimeoutException e) {
            throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                    this.getClass().getName(), methodName, e);
        }
    }

    /**
     * Search based on the provided parameters.
     * @param entityTypeGUID see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param searchCriteria see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param fromEntityElement see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param limitResultsByStatus see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param matchClassifications see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param asOfTime see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param sequencingProperty see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param sequencingOrder see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param pageSize see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue
     * @param userId of the user running the query
     * @return {@code List<EntityDetail>}
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws RepositoryTimeoutException if the query runs longer than the defined threshold (default: 30s)
     * @see XtdbOMRSMetadataCollection#findEntitiesByPropertyValue(String, String, String, int, List, List, Date, String, SequencingOrder, int)
     */
    public List<EntityDetail> findEntitiesByText(String entityTypeGUID,
                                                 String searchCriteria,
                                                 int fromEntityElement,
                                                 List<InstanceStatus> limitResultsByStatus,
                                                 SearchClassifications matchClassifications,
                                                 Date asOfTime,
                                                 String sequencingProperty,
                                                 SequencingOrder sequencingOrder,
                                                 int pageSize,
                                                 String userId) throws TypeErrorException, RepositoryTimeoutException {
        final String methodName = "findEntitiesByText";
        try {
            Collection<List<?>> xtdbResults = searchXtdbText(
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
                    EntityDetailMapping.ENTITY_PROPERTIES_NS,
                    userId
            );
            log.debug(Constants.FOUND_RESULTS, xtdbResults);
            return translateEntityResults(xtdbResults, asOfTime);
        } catch (TimeoutException e) {
            throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                    this.getClass().getName(), methodName, e);
        }
    }

    /**
     * Translate the provided list of XTDB document references into a list of full EntityDetail results.
     * @param xtdbResults list of XTDB document references (ie. from a search)
     * @param asOfTime point in time view of the data (or null for current)
     * @return {@code List<EntityDetail>} list of Egeria representation of the results
     */
    private List<EntityDetail> translateEntityResults(Collection<List<?>> xtdbResults, Date asOfTime) {
        final String methodName = "translateEntityResults";
        List<EntityDetail> results = null;
        if (xtdbResults != null) {
            results = new ArrayList<>();
            for (List<?> xtdbResult : xtdbResults) {
                String docRef = (String) xtdbResult.get(0);
                XtdbDocument xtdbDoc = getXtdbObjectByReference(docRef, asOfTime);
                if (xtdbDoc == null) {
                    logProblem(this.getClass().getName(),
                            methodName,
                            XtdbOMRSAuditCode.MAPPING_FAILURE,
                            null,
                            "entity",
                            docRef,
                            "cannot be retrieved from XTDB");
                } else {
                    EntityDetailMapping edm = new EntityDetailMapping(this, xtdbDoc);
                    EntityDetail ed = edm.toEgeria();
                    if (ed != null) {
                        results.add(ed);
                    } else {
                        logProblem(this.getClass().getName(),
                                methodName,
                                XtdbOMRSAuditCode.MAPPING_FAILURE,
                                null,
                                "entity",
                                docRef,
                                "cannot be mapped to EntityDetail");
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
     * @param userId of the user running the query
     * @return {@code List<Relationship>} list of the matching relationships
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws RepositoryErrorException if any issue closing open XTDB resources, or if the query runs longer than the defined threshold (default: 30s)
     */
    public List<Relationship> findRelationshipsForEntity(String entityGUID,
                                                         String relationshipTypeGUID,
                                                         int fromRelationshipElement,
                                                         List<InstanceStatus> limitResultsByStatus,
                                                         Date asOfTime,
                                                         String sequencingProperty,
                                                         SequencingOrder sequencingOrder,
                                                         int pageSize,
                                                         String userId) throws TypeErrorException, RepositoryErrorException {

        final String methodName = "findRelationshipsForEntity";
        List<Relationship> results;

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (try-with to ensure it is closed after).
        try (IXtdbDatasource db = asOfTime == null ? xtdbAPI.openDB() : xtdbAPI.openDB(asOfTime)) {

            Collection<List<?>> xtdbResults = findEntityRelationships(db,
                    entityGUID,
                    relationshipTypeGUID,
                    fromRelationshipElement,
                    limitResultsByStatus,
                    sequencingProperty,
                    sequencingOrder,
                    pageSize,
                    userId);

            log.debug(Constants.FOUND_RESULTS, xtdbResults);
            results = resultsToList(db, xtdbResults);

        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        } catch (TimeoutException e) {
            throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                    this.getClass().getName(), methodName, e);
        }

        return results;

    }

    /**
     * Find all active (non-deleted) relationships in this repository for the provided entity.
     * @param entity for which to find relationships
     * @param userId of the user running the query
     * @return {@code List<Relationship>} list of the homed relationships
     * @throws RepositoryErrorException  if any issue closing open XTDB resources, or if the query runs longer than the defined threshold (default: 30s)
     */
    public List<Relationship> findActiveRelationshipsForEntity(EntityDetail entity,
                                                               String userId) throws RepositoryErrorException {

        final String methodName = "findRelationshipsForEntity";
        List<Relationship> results;

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (try-with to ensure it is closed after).
        try (IXtdbDatasource db = xtdbAPI.openDB()) {

            Collection<List<?>> xtdbResults = findEntityRelationships(db,
                    entity.getGUID(),
                    userId,
                    false);

            log.debug(Constants.FOUND_RESULTS, xtdbResults);
            results = resultsToList(db, xtdbResults);

        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        } catch (TimeoutException e) {
            throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                    this.getClass().getName(), methodName, e);
        }

        return results;

    }

    /**
     * Find all relationships homed in this repository for the provided entity.
     * @param entity for which to find relationships
     * @param userId of the user running the query
     * @return {@code List<Relationship>} list of the homed relationships
     * @throws RepositoryErrorException  if any issue closing open XTDB resources, or if the query runs longer than the defined threshold (default: 30s)
     */
    public List<Relationship> findHomedRelationshipsForEntity(EntityDetail entity,
                                                              String userId) throws RepositoryErrorException {

        final String methodName = "findHomedRelationshipsForEntity";
        List<Relationship> results;

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (try-with to ensure it is closed after).
        try (IXtdbDatasource db = xtdbAPI.openDB()) {

            Collection<List<?>> xtdbResults = findHomedEntityRelationships(db,
                    entity,
                    userId);

            log.debug(Constants.FOUND_RESULTS, xtdbResults);
            results = resultsToList(db, xtdbResults);

        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        } catch (TimeoutException e) {
            throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                    this.getClass().getName(), methodName, e);
        }

        return results;

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
     * @param includeRelationships whether to include relationships in the resulting graph (true) or not (false)
     * @return InstanceGraph of the neighborhood
     * @throws RepositoryErrorException  if any issue closing open XTDB resources, or if the query runs longer than the defined threshold (default: 30s)
     */
    public InstanceGraph findNeighborhood(String entityGUID,
                                          List<String> entityTypeGUIDs,
                                          List<String> relationshipTypeGUIDs,
                                          List<InstanceStatus> limitResultsByStatus,
                                          List<String> limitResultsByClassification,
                                          Date asOfTime,
                                          int level,
                                          boolean includeRelationships) throws RepositoryErrorException {

        final String methodName = "findNeighborhood";
        InstanceGraph instanceGraph;

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (try-with to ensure it is closed after).
        try (IXtdbDatasource db = asOfTime == null ? xtdbAPI.openDB() : xtdbAPI.openDB(asOfTime)) {

            instanceGraph = null;

            Set<List<?>> consolidated = new LinkedHashSet<>();

            Set<String> entityGUIDsRetrieved = new HashSet<>();
            Set<String> relationshipGUIDsRetrieved = new HashSet<>();
            Set<String> entityGUIDsVisited = new HashSet<>();
            Set<String> relationshipGUIDsVisited = new HashSet<>();
            List<String> nextEntityGUIDs = new ArrayList<>();
            nextEntityGUIDs.add(entityGUID);

            EntityDetail startingEntity = this.getEntityByGuid(db, entityGUID);

            if (startingEntity != null) {
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
                instanceGraph = resultsToGraph(startingEntity, db, consolidated, entityGUIDsRetrieved, relationshipGUIDsRetrieved, includeRelationships);

            }

        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }

        return instanceGraph;

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
     * @throws RepositoryTimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    private Set<List<?>> getNextLevelNeighbors(IXtdbDatasource db,
                                               List<String> startingPoints,
                                               List<String> entityTypeGUIDs,
                                               List<String> relationshipTypeGUIDs,
                                               List<InstanceStatus> limitResultsByStatus,
                                               List<String> limitResultsByClassification,
                                               Set<String> entityGUIDsVisited,
                                               Set<String> relationshipGUIDsVisited) throws RepositoryTimeoutException {

        final String methodName = "getNextLevelNeighbors";
        Set<List<?>> consolidated = new LinkedHashSet<>();

        try {
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
                    String candidateEntityRef = getEntityRefFromGraphTuple(candidateTuple);
                    String candidateRelationshipRef = getRelationshipRefFromGraphTuple(candidateTuple);
                    String entityGuid = InstanceHeaderMapping.trimGuidFromReference(candidateEntityRef);
                    String relationshipGuid = InstanceHeaderMapping.trimGuidFromReference(candidateRelationshipRef);
                    if (!entityGUIDsVisited.contains(entityGuid) || !relationshipGUIDsVisited.contains(relationshipGuid)) {
                        // If either the entity or the relationship has not been seen, add the tuple
                        consolidated.add(candidateTuple);
                        entityGUIDsVisited.add(entityGUID);
                        relationshipGUIDsVisited.add(relationshipGuid);
                    }
                }
            }
        } catch (TimeoutException e) {
            throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                    this.getClass().getName(), methodName, e);
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
     * @throws RepositoryErrorException if any issue closing an open XTDB resource
     */
    public InstanceGraph getTraversalsBetweenEntities(String startEntityGUID,
                                                      String endEntityGUID,
                                                      List<InstanceStatus> limitResultsByStatus,
                                                      Date asOfTime) throws EntityNotKnownException, RepositoryErrorException {

        final String methodName = "getTraversalsBetweenEntities";
        InstanceGraph instanceGraph;

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (try-with to ensure it is closed after).
        try (IXtdbDatasource db = asOfTime == null ? xtdbAPI.openDB() : xtdbAPI.openDB(asOfTime)) {

            Set<String> entityGUIDsVisited = new HashSet<>();
            Set<String> relationshipGUIDsVisited = new HashSet<>();

            // Start the InstanceGraph off with the entity starting point that was requested
            // (not clear if this is the intended logic, but follows other repository implementations)
            EntityDetail startingEntity = this.getEntityByGuid(db, startEntityGUID);

            if (startingEntity == null) {
                throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_PROXY_ONLY.getMessageDefinition(
                        startEntityGUID, repositoryName), this.getClass().getName(), methodName);
            }

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
            instanceGraph = resultsToGraph(startingEntity, db, successfulTraversals, entityGUIDsVisited, relationshipGUIDsVisited, true);
            if (instanceGraph != null && instanceGraph.getEntities() != null && instanceGraph.getEntities().size() == 1) {
                // If there were no entities other than the starting entity, return an empty graph
                instanceGraph = null;
            }

        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }

        return instanceGraph;

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
     * @throws RepositoryTimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    private Set<List<?>> traverseToEnd(IXtdbDatasource db,
                                       String startEntityGUID,
                                       String endEntityGUID,
                                       List<InstanceStatus> limitResultsByStatus,
                                       Set<String> entityGUIDsVisited,
                                       int currentDepth) throws RepositoryTimeoutException {

        final String methodName = "traverseToEnd";
        Set<List<?>> consolidated = new LinkedHashSet<>();

        // As long as we have not reached the maximum depth, keep traversing...
        if (currentDepth < Constants.MAX_TRAVERSAL_DEPTH) {
            try {
                Collection<List<?>> nextLevel = findDirectNeighbors(db,
                        startEntityGUID,
                        null,
                        null,
                        limitResultsByStatus,
                        null);
                log.debug("Found traversal results: {}", nextLevel);
                String startRef = EntitySummaryMapping.getReference(startEntityGUID);
                String endRef = EntitySummaryMapping.getReference(endEntityGUID);
                if (nextLevel != null && !nextLevel.isEmpty()) {
                    // As long as there is something to check in the next level, do so...
                    for (List<?> candidateTuple : nextLevel) {
                        String candidateEntityRef = getEntityRefFromGraphTuple(candidateTuple);
                        if (endRef.equals(candidateEntityRef)) {
                            // If we found the endEntityGUID in the results, add it to the set of successful traversals
                            consolidated.add(candidateTuple);
                        } else if (!startRef.equals(candidateEntityRef)) {
                            // Otherwise, so long as we have not circled back to the starting point, continue traversing
                            String nextStartGuid = InstanceHeaderMapping.trimGuidFromReference(candidateEntityRef);
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
            } catch (TimeoutException e) {
                throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                        this.getClass().getName(), methodName, e);
            }
        }

        // return the consolidated set of successful traversals, or null if none were found
        return consolidated;

    }

    /**
     * Translate the collection of XTDB tuple results (from a graph query) into an Egeria InstanceGraph.
     * @param startingEntity the EntityDetail from which the graph query was anchored
     * @param db already opened point-in-time view of the database
     * @param xtdbResults list of result tuples, eg. from a neighborhood or other graph search
     * @param entityGUIDsVisited the list of entity GUIDs that have already been retrieved
     * @param relationshipGUIDsVisited the list of relationship GUIDs that have already been retrieved
     * @param includeRelationships whether to include relationships in the resulting graph (true) or not (false)
     * @return InstanceGraph
     * @see #findNeighborhood(String, List, List, List, List, Date, int, boolean)
     */
    private InstanceGraph resultsToGraph(EntityDetail startingEntity,
                                         IXtdbDatasource db,
                                         Collection<List<?>> xtdbResults,
                                         Set<String> entityGUIDsVisited,
                                         Set<String> relationshipGUIDsVisited,
                                         boolean includeRelationships) {

        final String methodName = "resultsToGraph";
        InstanceGraph results = null;

        if (startingEntity != null) {

            // Start the InstanceGraph off with the entity starting point that was requested
            // (not clear if this is the intended logic, but follows other repository implementations)
            results = new InstanceGraph();
            List<EntityDetail> entities = new ArrayList<>();
            List<Relationship> relationships = new ArrayList<>();
            entities.add(startingEntity);
            entityGUIDsVisited.add(startingEntity.getGUID());

            if (xtdbResults != null) {

                for (List<?> xtdbResult : xtdbResults) {
                    String entityRef = getEntityRefFromGraphTuple(xtdbResult);
                    String entityGuid = InstanceHeaderMapping.trimGuidFromReference(entityRef);
                    if (!entityGUIDsVisited.contains(entityGuid)) {
                        EntityDetail entity = getEntityByRef(db, entityRef);
                        entityGUIDsVisited.add(entityGuid);
                        if (entity == null) {
                            logProblem(this.getClass().getName(),
                                    methodName,
                                    XtdbOMRSAuditCode.MAPPING_FAILURE,
                                    null,
                                    "entity",
                                    entityRef,
                                    "cannot be translated to EntityDetail");
                        } else {
                            entities.add(entity);
                        }
                    }
                    if (includeRelationships) {
                        String relationshipRef = getRelationshipRefFromGraphTuple(xtdbResult);
                        String relationshipGuid = InstanceHeaderMapping.trimGuidFromReference(relationshipRef);
                        if (!relationshipGUIDsVisited.contains(relationshipGuid)) {
                            Relationship relationship = getRelationshipByRef(db, relationshipRef);
                            relationshipGUIDsVisited.add(relationshipGuid);
                            if (relationship == null) {
                                logProblem(this.getClass().getName(),
                                        methodName,
                                        XtdbOMRSAuditCode.MAPPING_FAILURE,
                                        null,
                                        "relationship",
                                        relationshipRef,
                                        "cannot be translated to Relationship");
                            } else {
                                relationships.add(relationship);
                            }
                        }
                    }
                }

            }

            results.setEntities(entities);
            results.setRelationships(relationships);

        }

        return results;

    }

    /**
     * Retrieve the unique set of entity GUIDs from the provided graph query results.
     * @param xtdbResults graph query results
     * @return {@code List<String>}
     */
    private List<String> getEntityGUIDsFromGraphResults(Collection<List<?>> xtdbResults) {
        List<String> list = new ArrayList<>();
        for (List<?> result : xtdbResults) {
            String entityRef = getEntityRefFromGraphTuple(result);
            if (entityRef != null) {
                String guid = InstanceHeaderMapping.trimGuidFromReference(entityRef);
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
     * @return String reference for the entity
     */
    private String getEntityRefFromGraphTuple(List<?> tuple) {
        return tuple == null ? null : (String) tuple.get(0);
    }

    /**
     * Retrieve the relationship reference from the provided graph query result.
     * @param tuple graph query result
     * @return String reference for the relationship
     */
    private String getRelationshipRefFromGraphTuple(List<?> tuple) {
        return tuple == null ? null : (String) tuple.get(1);
    }

    /**
     * Search based on the provided parameters.
     * @param relationshipTypeGUID see XtdbOMRSMetadataCollection#findRelationships
     * @param relationshipSubtypeGUIDs see XtdbOMRSMetadataCollection#findRelationships
     * @param matchProperties see XtdbOMRSMetadataCollection#findRelationships
     * @param fromRelationshipElement see XtdbOMRSMetadataCollection#findRelationships
     * @param limitResultsByStatus see XtdbOMRSMetadataCollection#findRelationships
     * @param asOfTime see XtdbOMRSMetadataCollection#findRelationships
     * @param sequencingProperty see XtdbOMRSMetadataCollection#findRelationships
     * @param sequencingOrder see XtdbOMRSMetadataCollection#findRelationships
     * @param pageSize see XtdbOMRSMetadataCollection#findRelationships
     * @param userId of the user running the query
     * @return {@code List<Relationship>}
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws RepositoryErrorException  if any issue closing open Xtdb resources, or if the query runs longer than the defined threshold (default: 30s)
     * @see XtdbOMRSMetadataCollection#findRelationships(String, String, List, SearchProperties, int, List, Date, String, SequencingOrder, int)
     */
    public List<Relationship> findRelationships(String relationshipTypeGUID,
                                                List<String> relationshipSubtypeGUIDs,
                                                SearchProperties matchProperties,
                                                int fromRelationshipElement,
                                                List<InstanceStatus> limitResultsByStatus,
                                                Date asOfTime,
                                                String sequencingProperty,
                                                SequencingOrder sequencingOrder,
                                                int pageSize,
                                                String userId) throws TypeErrorException, RepositoryErrorException {

        final String methodName = "findRelationships";
        List<Relationship> results;

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (try-with to ensure it is closed after).
        try (IXtdbDatasource db = asOfTime == null ? xtdbAPI.openDB() : xtdbAPI.openDB(asOfTime)) {

            Collection<List<?>> xtdbResults = searchXtdb(db,
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
                    RelationshipMapping.RELATIONSHIP_PROPERTIES_NS,
                    userId
            );

            log.debug(Constants.FOUND_RESULTS, xtdbResults);
            results = resultsToList(db, xtdbResults);

        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        } catch (TimeoutException e) {
            throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                    this.getClass().getName(), methodName, e);
        }

        return results;

    }

    /**
     * Search based on the provided parameters.
     * @param relationshipTypeGUID see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param searchCriteria see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param fromRelationshipElement see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param limitResultsByStatus see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param asOfTime see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param sequencingProperty see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param sequencingOrder see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param pageSize see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue
     * @param userId of the user running the query
     * @return {@code List<Relationship>}
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws RepositoryErrorException  if any issue closing open XTDB resources, or if the query runs longer than the defined threshold (default: 30s)
     * @see XtdbOMRSMetadataCollection#findRelationshipsByPropertyValue(String, String, String, int, List, Date, String, SequencingOrder, int)
     */
    public List<Relationship> findRelationshipsByText(String relationshipTypeGUID,
                                                      String searchCriteria,
                                                      int fromRelationshipElement,
                                                      List<InstanceStatus> limitResultsByStatus,
                                                      Date asOfTime,
                                                      String sequencingProperty,
                                                      SequencingOrder sequencingOrder,
                                                      int pageSize,
                                                      String userId) throws TypeErrorException, RepositoryErrorException {

        final String methodName = "findRelationshipsByText";
        List<Relationship> results;

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (try-with to ensure it is closed after).
        try (IXtdbDatasource db = asOfTime == null ? xtdbAPI.openDB() : xtdbAPI.openDB(asOfTime)) {

            Collection<List<?>> xtdbResults = searchXtdbText(db,
                    TypeDefCategory.RELATIONSHIP_DEF,
                    relationshipTypeGUID,
                    searchCriteria,
                    fromRelationshipElement,
                    limitResultsByStatus,
                    null,
                    sequencingProperty,
                    sequencingOrder,
                    pageSize,
                    RelationshipMapping.RELATIONSHIP_PROPERTIES_NS,
                    userId
            );

            log.debug(Constants.FOUND_RESULTS, xtdbResults);
            results = resultsToList(db, xtdbResults);

        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        } catch (TimeoutException e) {
            throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(repositoryName),
                    this.getClass().getName(), methodName, e);
        }

        return results;

    }

    /**
     * Translate the set of XTDB document IDs into a list of Egeria Relationships.
     * @param db already opened point-in-time view of the database
     * @param xtdbResults list of document IDs, eg. from a search
     * @return {@code List<Relationship>}
     * @see #searchXtdb(IXtdbDatasource, TypeDefCategory, String, List, SearchProperties, int, List, SearchClassifications, String, SequencingOrder, int, String, String)
     * @see #findEntityRelationships(IXtdbDatasource, String, String, int, List, String, SequencingOrder, int, String)
     */
    private List<Relationship> resultsToList(IXtdbDatasource db, Collection<List<?>> xtdbResults) {
        final String methodName = "resultsToList";
        List<Relationship> results = null;
        if (xtdbResults != null) {
            results = new ArrayList<>();
            for (List<?> xtdbResult : xtdbResults) {
                String docRef = (String) xtdbResult.get(0);
                Relationship relationship = getRelationshipByRef(db, docRef);
                if (relationship == null) {
                    logProblem(this.getClass().getName(),
                            methodName,
                            XtdbOMRSAuditCode.MAPPING_FAILURE,
                            null,
                            "relationship",
                            docRef,
                            "cannot be translated to Relationship");
                } else {
                    results.add(relationship);
                }
            }
        }
        return results;
    }

    /**
     * Translate the provided XTDB document reference into an Egeria relationship.
     * @param db already opened point-in-time view of the database
     * @param ref reference to the relationship document
     * @return Relationship
     */
    private Relationship getRelationshipByRef(IXtdbDatasource db, String ref) {
        XtdbDocument xtdbDoc = getXtdbObjectByReference(db, ref);
        if (xtdbDoc != null) {
            RelationshipMapping rm = new RelationshipMapping(this, xtdbDoc, db);
            return rm.toEgeria();
        }
        return null;
    }

    /**
     * Retrieve the requested relationship from the XTDB repository.
     * @param guid of the relationship to retrieve
     * @param asOfTime view of the relationship at this particular point in time
     * @return Relationship as it existed at the specified point in time
     * @throws RepositoryErrorException if any issue closing an open XTDB resource
     */
    public Relationship getRelationship(String guid, Date asOfTime) throws RepositoryErrorException {

        final String methodName = "getRelationship";
        Relationship result;

        // Since a relationship involves not only the relationship object, but also some details from each proxy,
        // we will open a database up-front to re-use for multiple queries (try-with to ensure it is closed after).
        try (IXtdbDatasource db = asOfTime == null ? xtdbAPI.openDB() : xtdbAPI.openDB(asOfTime)) {

            XtdbDocument xtdbDoc = getXtdbObjectByReference(db, RelationshipMapping.getReference(guid));
            if (log.isDebugEnabled())
                log.debug(Constants.FOUND_RESULTS, xtdbDoc == null ? null : xtdbDoc.toMap());
            RelationshipMapping rm = new RelationshipMapping(this, xtdbDoc, db);
            result = rm.toEgeria();

        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }

        return result;

    }

    /**
     * Retrieve previous versions of the entity between the provided dates, ordered as requested.
     * @param guid of the entity for which to retrieve previous versions
     * @param from earliest date and time for which the versions to include were valid (inclusive)
     * @param to latest date and time for which the versions to include were valid (exclusive)
     * @param offset element from which to start (paging)
     * @param pageSize maximum number of results to include (paging)
     * @param order whether to order the results in reverse-chronological order (backwards) or chronologically (forwards)
     * @return {@code List<EntityDetail>} giving all versions of the entity within the range requested
     * @throws EntityNotKnownException if the requested entity was not known to the repository during the specified time range
     * @throws RepositoryErrorException if any issue closing the lazy-evaluating cursor
     */
    public List<EntityDetail> getPreviousVersionsOfEntity(String guid,
                                                          Date from,
                                                          Date to,
                                                          int offset,
                                                          int pageSize,
                                                          HistorySequencingOrder order) throws EntityNotKnownException, RepositoryErrorException {

        final String methodName = "getPreviousVersionsOfEntity";
        List<EntityDetail> results = new ArrayList<>();
        String docRef = EntitySummaryMapping.getReference(guid);

        boolean noResults;

        // Open the database view at the latest point against which we are interested
        try (IXtdbDatasource db = to == null ? xtdbAPI.openDB() : xtdbAPI.openDB(to)) {
            List<XtdbDocument> history = getPreviousVersions(db, docRef, from, order);
            noResults = history.isEmpty();

            // Default to the maximum allowable page size if none was specified
            if (pageSize == 0) {
                pageSize = getMaxPageSize();
            }
            int maxResult = offset + pageSize;

            int currentIndex = 0;
            // Iterate through every doc received back and retrieve the details of the associated
            // EntityDetail -- adhering to requested paging parameters...
            for (XtdbDocument version : history) {
                if (currentIndex >= maxResult) {
                    break; // break out if we're beyond the page
                } else if (currentIndex >= offset) {
                    EntityDetailMapping edm = new EntityDetailMapping(this, version);
                    EntityDetail detail = edm.toEgeria();
                    if (detail != null) {
                        results.add(detail);
                    }
                }
                currentIndex++;
            }
        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }

        if (noResults)
            throw new EntityNotKnownException(XtdbOMRSErrorCode.ENTITY_NOT_KNOWN.getMessageDefinition(guid),
                    this.getClass().getName(), methodName);

        return results;

    }

    /**
     * Retrieve previous versions of the relationship between the provided dates, ordered as requested.
     * @param guid of the relationship for which to retrieve previous versions
     * @param from earliest date and time for which the versions to include were valid (inclusive)
     * @param to latest date and time for which the versions to include were valid (exclusive)
     * @param offset element from which to start (paging)
     * @param pageSize maximum number of results to include (paging)
     * @param order whether to order the results in reverse-chronological order (backwards) or chronologically (forwards)
     * @return {@code List<Relationship>} giving all versions of the relationship within the range requested
     * @throws RelationshipNotKnownException if the requested relationship was not known to the repository during the specified time range
     * @throws RepositoryErrorException if any issue closing the lazy-evaluating cursor
     */
    public List<Relationship> getPreviousVersionsOfRelationship(String guid,
                                                                Date from,
                                                                Date to,
                                                                int offset,
                                                                int pageSize,
                                                                HistorySequencingOrder order) throws RelationshipNotKnownException, RepositoryErrorException {

        final String methodName = "getPreviousVersionsOfRelationship";
        List<Relationship> results = new ArrayList<>();
        String docRef = RelationshipMapping.getReference(guid);

        boolean noResults;

        // Open the database view at the latest point against which we are interested
        try (IXtdbDatasource db = to == null ? xtdbAPI.openDB() : xtdbAPI.openDB(to)) {
            List<XtdbDocument> history = getPreviousVersions(db, docRef, from, order);
            noResults = history.isEmpty();

            // Default to the maximum allowable page size if none was specified
            if (pageSize == 0) {
                pageSize = getMaxPageSize();
            }
            int maxResult = offset + pageSize;

            int currentIndex = 0;
            // Iterate through every doc received back and retrieve the details of the associated
            // EntityDetail -- adhering to requested paging parameters...
            for (XtdbDocument version : history) {
                if (currentIndex >= maxResult) {
                    break; // break out if we're beyond the page
                } else if (currentIndex >= offset) {
                    // TODO: is it sufficient to just send the current opened view of the database, or
                    //  do we actually need to send a newly opened view as of this particular version's
                    //  validity time?
                    RelationshipMapping rm = new RelationshipMapping(this, version, db);
                    Relationship relationship = rm.toEgeria();
                    if (relationship != null) {
                        results.add(relationship);
                    }
                }
                currentIndex++;
            }
        } catch (IOException e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }

        if (noResults)
            throw new RelationshipNotKnownException(XtdbOMRSErrorCode.RELATIONSHIP_NOT_KNOWN.getMessageDefinition(guid),
                    this.getClass().getName(), methodName);

        return results;

    }

    /**
     * Retrieve the previous versions of the provided XTDB object, from an already-opened point-in-time view of the
     * repository back to the earliest point in time defined by the 'earliest' parameter.
     * @param db from which to retrieve the previous version
     * @param reference indicating the primary key of the object for which to retrieve the previous version
     * @param earliest the earliest version to retrieve
     * @param order indicating either chronological (forward) or reverse-chronological (backward) ordering of results
     * @return {@code List<XtdbDocument>} with all versions of the XTDB object back to the earliest point specified, ordered as requested
     * @throws RepositoryErrorException if any issue closing the lazy-evaluating cursor
     */
    private List<XtdbDocument> getPreviousVersions(IXtdbDatasource db, String reference, Date earliest, HistorySequencingOrder order) throws RepositoryErrorException {

        final String methodName = "getPreviousVersions";

        // Note: we will always retrieve the versions in reverse-order, since they are lazily-evaluated. This will
        // avoid the need to retrieve history that goes before the 'earliest' date and compare it, but does mean that
        // the results of our looping may need to reverse-order the resulting array if the sort order requested is
        // 'forward' (chronological)
        HistoryOptions options = HistoryOptions.create(HistoryOptions.SortOrder.DESC);
        List<XtdbDocument> results;

        // try-with to ensure that the ICursor resource is closed, even if any exception is thrown
        try (ICursor<Map<Keyword, ?>> lazyCursor = db.openEntityHistory(reference, options)) {
            // Note that here we will not pass-through the opened DB as this method will need to retrieve a different
            // point-in-time view of the details of each entity anyway (based on the transaction dates from the cursor,
            // rather than the already-opened DB resource)
            results = getPreviousVersionsFromCursor(lazyCursor, reference, earliest, order);
        } catch (Exception e) {
            throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                    this.getClass().getName(), methodName, e);
        }

        return results;

    }

    /**
     * Retrieve the previous versions of the provided XTDB reference up to the earliest point requested, from an
     * already-opened lazily-evaluated cursor.
     * @param cursor from which to lazily-evaluate the current and previous versions
     * @param reference indicating the primary key of the object for which to retrieve the current and previous version
     * @param earliest the earliest version to retrieve
     * @param order indicating either chronological (forward) or reverse-chronological (backward) ordering of results
     * @return {@code List<XtdbDocument>} with all versions of the XTDB object back to the earliest point specified
     */
    private List<XtdbDocument> getPreviousVersionsFromCursor(ICursor<Map<Keyword, ?>> cursor, String reference, Date earliest, HistorySequencingOrder order) {
        List<XtdbDocument> results = new ArrayList<>();
        // History entries themselves will just be transaction details like the following:
        // { :xtdb.tx/tx-time #inst "2021-02-01T00:28:32.533-00:00",
        //   :xtdb.tx/tx-id 2,
        //   :xtdb.db/valid-time #inst "2021-02-01T00:28:32.531-00:00",
        //   :xtdb.db/content-hash #xtdb/id "..." }
        if (cursor != null) {
            while (cursor.hasNext()) {
                Map<Keyword, ?> version = cursor.next();
                Date versionValidFrom = (Date) version.get(Constants.XTDB_VALID_TIME);
                // Recall that the ordering requested from XTDB is always in reverse, so we will always be building up
                // the results array in reverse-order to best leverage its lazy evaluation
                // (Also, given that the earliest date could be null to indicate 'all history', we should force our
                // comparator to always continue the loop if the earliest date is null
                int comparator = earliest == null ? 1 : versionValidFrom.compareTo(earliest);
                XtdbDocument docVersion = getXtdbObjectByReference(reference, version);
                if (docVersion != null) {
                    results.add(docVersion);
                }
                if (comparator <= 0) {
                    // If the version we are examining is either the first one we see before our earliest date cut-off,
                    // or precisely on that earliest date cut-off, we have the final version we should include so
                    // we can now break out of the loop
                    break;
                }
                // (Otherwise, we are still within the versions to include, so simply continue looping)
            }
        }

        // Note: our results are reverse-chronological by default due to lazy evaluation, but if we were requested to
        // return them in forward chronological order we should reverse the array
        if (order.equals(HistorySequencingOrder.FORWARDS)) {
            Collections.reverse(results);
        }

        return results;

    }

    /**
     * Retrieve the current version of the requested reference's details from the XTDB repository.
     * @param reference indicating the primary key of the XTDB object to retrieve
     * @return XtdbDocument of the object's properties
     */
    public XtdbDocument getXtdbObjectByReference(String reference) {
        return getXtdbObjectByReference(reference, (Date) null);
    }

    /**
     * Retrieve the requested reference's details from the XTDB repository.
     * @param reference indicating the primary key of the XTDB object to retrieve
     * @param asOfTime view of the object at this particular point in time (or null for current)
     * @return XtdbDocument of the object's properties
     */
    public XtdbDocument getXtdbObjectByReference(String reference, Date asOfTime) {
        if (asOfTime != null) {
            return xtdbAPI.db(asOfTime).entity(reference);
        } else {
            return xtdbAPI.db().entity(reference);
        }
    }

    /**
     * Retrieve the requested reference's details from an already-open XTDB repository.
     * @param db from which to retrieve the details
     * @param reference indicating the primary key of the XTDB object to retrieve
     * @return XtdbDocument of the object's properties
     */
    public XtdbDocument getXtdbObjectByReference(IXtdbDatasource db, String reference) {
        return db.entity(reference);
    }

    /**
     * Retrieve the requested reference's details from the XTDB repository at the precise version indicated by
     * the provided transaction details (as returned by an entity history call, must include the valid-time and
     * tx-time).
     * @param reference indicating the primary key of the XTDB object to retrieve
     * @param txnDetails containing the valid-time and tx-time of the precise version of the document to retrieve
     * @return XtdbDocument of the object's properties
     */
    public XtdbDocument getXtdbObjectByReference(String reference, Map<Keyword, ?> txnDetails) {
        Object oValid = txnDetails.get(Constants.XTDB_VALID_TIME);
        Object oTxn   = txnDetails.get(Constants.XTDB_TX_TIME);
        if (oValid instanceof Date && oTxn instanceof Date) {
            return xtdbAPI.db((Date) oValid, (Date) oTxn).entity(reference);
        }
        return null;
    }

    /**
     * Search XTDB based on the provided parameters (should work across both Entities and Relationships).
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
     * @param userId of the user running the query
     * @return {@code Collection<List<?>>} list of the XTDB document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws TimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    public Collection<List<?>> searchXtdb(TypeDefCategory category,
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
                                          String namespace,
                                          String userId) throws TypeErrorException, TimeoutException {
        XtdbQuery query = new XtdbQuery();
        updateQuery(query,
                category,
                typeGuid,
                subtypeGuids,
                matchProperties,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                namespace,
                userId);
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = xtdbAPI.db(asOfTime).query(q);
        // Note: we de-duplicate and apply paging here, against the full set of results from XTDB
        return deduplicateAndPage(results, fromElement, pageSize);
    }

    /**
     * Search XTDB based on the provided parameters, using an already-opened point-in-time view of the database (should
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
     * @param userId of the user running the query
     * @return {@code Collection<List<?>>} list of the XTDB document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws TimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    public Collection<List<?>> searchXtdb(IXtdbDatasource db,
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
                                          String namespace,
                                          String userId) throws TypeErrorException, TimeoutException {
        XtdbQuery query = new XtdbQuery();
        updateQuery(query,
                category,
                typeGuid,
                subtypeGuids,
                matchProperties,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                namespace,
                userId);
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = db.query(q);
        // Note: we de-duplicate and apply paging here, against the full set of results from XTDB
        return deduplicateAndPage(results, fromElement, pageSize);
    }

    /**
     * Search all text properties in XTDB based on the provided parameters (should work across both Entities and
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
     * @param userId of the user running the query
     * @return {@code Collection<List<?>>} list of the Xtdb document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws TimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    public Collection<List<?>> searchXtdbText(TypeDefCategory category,
                                              String typeGuid,
                                              String searchCriteria,
                                              int fromElement,
                                              List<InstanceStatus> limitResultsByStatus,
                                              SearchClassifications matchClassifications,
                                              Date asOfTime,
                                              String sequencingProperty,
                                              SequencingOrder sequencingOrder,
                                              int pageSize,
                                              String namespace,
                                              String userId) throws TypeErrorException, TimeoutException {
        XtdbQuery query = new XtdbQuery();
        updateTextQuery(query,
                category,
                typeGuid,
                searchCriteria,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                namespace,
                userId);
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = xtdbAPI.db(asOfTime).query(q);
        // Note: we de-duplicate and apply paging here, against the full set of results from XTDB
        return deduplicateAndPage(results, fromElement, pageSize);
    }

    /**
     * Search all text properties in XTDB based on the provided parameters, using an already-opened point-in-time view
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
     * @param userId of the user running the query
     * @return {@code Collection<List<?>>} list of the XTDB document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws TimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    public Collection<List<?>> searchXtdbText(IXtdbDatasource db,
                                              TypeDefCategory category,
                                              String typeGuid,
                                              String searchCriteria,
                                              int fromElement,
                                              List<InstanceStatus> limitResultsByStatus,
                                              SearchClassifications matchClassifications,
                                              String sequencingProperty,
                                              SequencingOrder sequencingOrder,
                                              int pageSize,
                                              String namespace,
                                              String userId) throws TypeErrorException, TimeoutException {
        XtdbQuery query = new XtdbQuery();
        updateTextQuery(query,
                category,
                typeGuid,
                searchCriteria,
                limitResultsByStatus,
                matchClassifications,
                sequencingProperty,
                sequencingOrder,
                namespace,
                userId);
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = db.query(q);
        // Note: we de-duplicate and apply paging here, against the full set of results from XTDB
        return deduplicateAndPage(results, fromElement, pageSize);
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
     * @param userId of the user running the query
     * @return {@code Collection<List<?>>} list of the XTDB document references that match
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws TimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    public Collection<List<?>> findEntityRelationships(IXtdbDatasource db,
                                                       String entityGUID,
                                                       String relationshipTypeGUID,
                                                       int fromRelationshipElement,
                                                       List<InstanceStatus> limitResultsByStatus,
                                                       String sequencingProperty,
                                                       SequencingOrder sequencingOrder,
                                                       int pageSize,
                                                       String userId) throws TypeErrorException, TimeoutException {
        XtdbQuery query = new XtdbQuery();
        query.addRelationshipEndpointConditions(EntitySummaryMapping.getReference(entityGUID));
        updateQuery(query,
                TypeDefCategory.RELATIONSHIP_DEF,
                relationshipTypeGUID,
                null,
                null,
                limitResultsByStatus,
                null,
                sequencingProperty,
                sequencingOrder,
                null,
                userId);
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = db.query(q);
        // Note: we de-duplicate and apply paging here, against the full set of results from XTDB
        return deduplicateAndPage(results, fromRelationshipElement, pageSize);
    }

    /**
     * Find the relationships that match the provided parameters.
     * @param db already opened point-in-time view of the database
     * @param entityGUID for which to find relationships
     * @param userId of the user running the query
     * @param includeDeleted if true, include deleted relationships in the results (otherwise exclude them)
     * @return {@code Collection<List<?>>} list of the XTDB document references that match
     * @throws TimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    public Collection<List<?>> findEntityRelationships(IXtdbDatasource db,
                                                       String entityGUID,
                                                       String userId,
                                                       boolean includeDeleted) throws TimeoutException {
        final String methodName = "findEntityRelationships";
        XtdbQuery query = new XtdbQuery();
        query.addRelationshipEndpointConditions(EntitySummaryMapping.getReference(entityGUID));
        try {
            if (includeDeleted) {
                query.addTypeCondition(TypeDefCategory.RELATIONSHIP_DEF, null, null);
            } else {
                updateQuery(query,
                        TypeDefCategory.RELATIONSHIP_DEF,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        userId);
            }
        } catch (TypeErrorException e) {
            logProblem(this.getClass().getName(),
                    methodName,
                    XtdbOMRSAuditCode.UNEXPECTED_RUNTIME_ERROR,
                    e,
                    "unexpected type error when no types are explicitly used",
                    e.getClass().getName());
        }
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = db.query(q);
        // Note: we de-duplicate here, against the full set of results from XTDB
        return deduplicate(results);
    }

    /**
     * Find the relationships that match the provided parameters.
     * @param db already opened point-in-time view of the database
     * @param entity for which to find relationships
     * @param userId of the user running the query
     * @return {@code Collection<List<?>>} list of the XTDB document references that match
     * @throws TimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    public Collection<List<?>> findHomedEntityRelationships(IXtdbDatasource db,
                                                            EntityDetail entity,
                                                            String userId) throws TimeoutException {
        final String methodName = "findHomedEntityRelationships";
        XtdbQuery query = new XtdbQuery();
        query.addRelationshipEndpointConditions(EntitySummaryMapping.getReference(entity.getGUID()));
        SearchProperties matchProperties = new SearchProperties();
        List<PropertyCondition> conditions = new ArrayList<>();
        PrimitivePropertyValue metadataCollectionId = new PrimitivePropertyValue();
        metadataCollectionId.setPrimitiveDefCategory(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_STRING);
        metadataCollectionId.setPrimitiveValue(entity.getMetadataCollectionId());
        PropertyCondition byMetadataCollectionId = new PropertyCondition();
        byMetadataCollectionId.setProperty("metadataCollectionId");
        byMetadataCollectionId.setOperator(PropertyComparisonOperator.EQ);
        byMetadataCollectionId.setValue(metadataCollectionId);
        conditions.add(byMetadataCollectionId);
        matchProperties.setConditions(conditions);
        matchProperties.setMatchCriteria(MatchCriteria.ALL);
        try {
            updateQuery(query,
                    TypeDefCategory.RELATIONSHIP_DEF,
                    null,
                    null,
                    matchProperties,
                    null,
                    null,
                    null,
                    null,
                    null,
                    userId);
        } catch (TypeErrorException e) {
            logProblem(this.getClass().getName(),
                    methodName,
                    XtdbOMRSAuditCode.UNEXPECTED_RUNTIME_ERROR,
                    e,
                    "unexpected type error when no types are explicitly used",
                    e.getClass().getName());
        }
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        Collection<List<?>> results = db.query(q);
        // Note: we de-duplicate here, against the full set of results from XTDB
        return deduplicate(results);
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
     * @throws TimeoutException if the query runs longer than the defined threshold (default: 30s)
     */
    public Collection<List<?>> findDirectNeighbors(IXtdbDatasource db,
                                                   String entityGUID,
                                                   List<String> entityTypeGUIDs,
                                                   List<String> relationshipTypeGUIDs,
                                                   List<InstanceStatus> limitResultsByStatus,
                                                   List<String> limitResultsByClassification) throws TimeoutException {
        XtdbGraphQuery query = new XtdbGraphQuery();
        query.addRelationshipLimiters(entityGUID, relationshipTypeGUIDs, limitResultsByStatus);
        query.addEntityLimiters(entityTypeGUIDs, limitResultsByClassification, limitResultsByStatus);
        IPersistentMap q = query.getQuery();
        log.debug(Constants.QUERY_WITH, q);
        return db.query(q);
    }

    /**
     * Update the provided query with the specified parameters.
     * @param query into which to add conditions
     * @param category to limit the search to either entities or relationships (required)
     * @param typeGuid to limit the search by type (optional)
     * @param subtypeGuids to limit the search to a set of subtypes (optional)
     * @param matchProperties by which to limit the results (optional)
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param namespace by which to qualify the matchProperties
     * @param userId of the user running the query
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    void updateQuery(XtdbQuery query,
                     TypeDefCategory category,
                     String typeGuid,
                     List<String> subtypeGuids,
                     SearchProperties matchProperties,
                     List<InstanceStatus> limitResultsByStatus,
                     SearchClassifications matchClassifications,
                     String sequencingProperty,
                     SequencingOrder sequencingOrder,
                     String namespace,
                     String userId) throws TypeErrorException {
        // Note that we will put the property search criteria first to optimise the search, which can more than double
        // the speed for very broad scenarios (where no type limiter is specified, or only Referenceable)
        Set<String> completeTypeSet = getCompleteSetOfTypeNamesForSearch(userId, typeGuid, subtypeGuids, namespace);
        query.addPropertyConditions(matchProperties, namespace, completeTypeSet, this, luceneConfigured, luceneRegexes);
        query.addTypeCondition(category, typeGuid, subtypeGuids);
        query.addClassificationConditions(matchClassifications, completeTypeSet, this, luceneConfigured, luceneRegexes);
        query.addSequencing(sequencingOrder, sequencingProperty, namespace, completeTypeSet, this);
        // Note: we will always limit by 'e', even if the TypeDefCategory indicates this is a relationship as these
        // operations only ever return a single type of instance (entity or relationship), and 'e' is therefore used
        // generally to represent either
        query.addStatusLimiters(limitResultsByStatus, XtdbQuery.DOC_ID);
    }

    /**
     * Update the provided query with the specified parameters for a free-form text search across all text fields.
     * @param query into which to add conditions
     * @param category to limit the search to either entities or relationships (required)
     * @param typeGuid to limit the search by type (optional)
     * @param searchCriteria defining the textual regular expression to use to match against all text fields
     * @param limitResultsByStatus by which to limit results (optional)
     * @param matchClassifications by which to limit entity results (must be null for relationships) (optional)
     * @param sequencingProperty by which to order the results (required if sequencingOrder involves a property)
     * @param sequencingOrder by which to order results (optional, will default to GUID)
     * @param namespace by which to qualify the sequencing property (if any)
     * @param userId of the user running the query
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    private void updateTextQuery(XtdbQuery query,
                                 TypeDefCategory category,
                                 String typeGuid,
                                 String searchCriteria,
                                 List<InstanceStatus> limitResultsByStatus,
                                 SearchClassifications matchClassifications,
                                 String sequencingProperty,
                                 SequencingOrder sequencingOrder,
                                 String namespace,
                                 String userId) throws TypeErrorException {
        // Note that we will put the search string criteria first to optimise the search, which can more than double
        // the speed for very broad scenarios (where no type limiter is specified, or only Referenceable)
        Set<String> completeTypeSet = getCompleteSetOfTypeNamesForSearch(userId, typeGuid, null, namespace);
        if (luceneConfigured) {
            query.addConditions(TextConditionBuilder.buildWildcardLuceneCondition(searchCriteria, this, completeTypeSet, namespace, luceneRegexes));
        } else {
            query.addConditions(TextConditionBuilder.buildWildcardTextCondition(searchCriteria, this, completeTypeSet, namespace, false, luceneRegexes));
        }
        query.addTypeCondition(category, typeGuid, null);
        query.addClassificationConditions(matchClassifications, completeTypeSet, this, luceneConfigured, luceneRegexes);
        query.addSequencing(sequencingOrder, sequencingProperty, namespace, completeTypeSet, this);
        // Note: we will always limit by 'e', even if the TypeDefCategory indicates this is a relationship as these
        // operations only ever return a single type of instance (entity or relationship), and 'e' is therefore used
        // generally to represent either
        query.addStatusLimiters(limitResultsByStatus, XtdbQuery.DOC_ID);
    }

    /**
     * Retrieve the complete list of type names that have been requested by the search.
     * @param userId of the user running the query
     * @param typeGuid provided to the search, to limit by type
     * @param subtypeGuids provided to the search, to limit to a set of subtypes
     * @param namespace by which properties will be qualified (allowing us to see whether the types should be for entities or relationships)
     * @return {@code Set<String>} of the names of all types and subtypes to include in the search
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     */
    private Set<String> getCompleteSetOfTypeNamesForSearch(String userId,
                                                           String typeGuid,
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
                        List<TypeDef> typeDefinitions = metadataCollection.findTypeDefsByCategory(userId, TypeDefCategory.RELATIONSHIP_DEF);
                        if (typeDefinitions != null) {
                            for (TypeDef typeDef : typeDefinitions) {
                                String typeDefName = typeDef.getName();
                                addAllSubtypesToSet(complete, typeDefName);
                            }
                        }
                    } catch (InvalidParameterException | RepositoryErrorException | UserNotAuthorizedException e) {
                        logProblem(this.getClass().getName(),
                                methodName,
                                XtdbOMRSAuditCode.UNEXPECTED_RUNTIME_ERROR,
                                e,
                                "unable to retrieve relationship typedefs",
                                e.getClass().getName());
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
     * De-duplicate and return only the selected page of results from the provided collection of XTDB query results.
     * @param results from a XTDB query
     * @param fromElement starting point for the page
     * @param pageSize number of elements to include in the page
     * @return {@code Collection<List<?>>} of only the single page of results specified
     */
    private Collection<List<?>> deduplicateAndPage(Collection<List<?>> results, int fromElement, int pageSize) {
        if (results == null || results.isEmpty()) {
            return results;
        } else {
            List<List<?>> pageOfResults  = new ArrayList<>();
            Set<List<?>> skippedResults = new HashSet<>();
            int currentIndex = 0;
            // 0 as a pageSize means ALL pages -- so we should return every result that we found (up to the maximum
            // number of results allowed by the connector)
            pageSize = pageSize > 0 ? pageSize : getMaxPageSize();
            int lastResultIndex = (fromElement + pageSize);
            for (List<?> singleResult : results) {
                if (currentIndex >= lastResultIndex) {
                    // If we are at / beyond the last index, break out of the loop
                    break;
                } else if (currentIndex >= fromElement) {
                    if (!pageOfResults.contains(singleResult)) {
                        // Otherwise, only add this result if it is at or beyond the starting point (fromElement) and
                        // our list of results does not already contain this result (in which case, also increment our
                        // current index for the number of results we have captured)
                        pageOfResults.add(singleResult);
                        currentIndex++;
                    }
                } else if (!skippedResults.contains(singleResult)) {
                    // Otherwise, remember that we have are skipping this result and increment the current index
                    // accordingly just this once (necessary to skip only the correct number of results, when the
                    // fromElement is not 0)
                    skippedResults.add(singleResult);
                    currentIndex++;
                }
                // In any other scenario, it is a result that has already been included or already been skipped,
                // so we do not need to increment our index or do anything with the result -- just move on to the
                // next one
            }
            return pageOfResults;
        }
    }

    /**
     * De-duplicate and return the full set of results from the provided collection of XTDB query results.
     * @param results from a XTDB query
     * @return {@code Collection<List<?>>} of all unique results
     */
    Collection<List<?>> deduplicate(Collection<List<?>> results) {
        if (results == null || results.isEmpty()) {
            return results;
        } else {
            return new ArrayList<>(new HashSet<>(results));
        }
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
     * Run multiple statements through XTDB as a single transaction.
     * @param statements the transaction to submit
     * @return TransactionInstant transaction details
     */
    public TransactionInstant runTx(Transaction statements) {
        if (log.isDebugEnabled())
            log.debug("{} transacting with: {}", synchronousIndex ? SYNC : ASYNC, statements.toVector());
        TransactionInstant tx = xtdbAPI.submitTx(statements);
        // Null for the timeout here means use the default (which is therefore configurable directly by the XTDB
        // configurationProperties of the connector)
        if (synchronousIndex) {
            return xtdbAPI.awaitTx(tx, null);
        } else {
            return tx;
        }
    }

    /**
     * Retrieve the XTDB API directly.
     * NOTE: This should only be used in very exceptional circumstances where direct access to the API
     * is needed (e.g. for testing purposes). Use any other method where possible.
     * @return IXtdb
     */
    public IXtdb getXtdbAPI() {
        return xtdbAPI;
    }

    /**
     * Add a write operation to the transaction (applicable for any write operation, since it is append-only).
     * Note: this should be used rather than calling '.put' directly against the transaction builder, as this method
     * will set the historical date based on the information within the document itself (if any), allowing us to have
     * a historical view for things like reference copies.
     * @param tx the transaction through which to do the write operation
     * @param xtdbDoc the document to write to the repository
     */
    public static void put(Transaction.Builder tx, XtdbDocument xtdbDoc) {
        // Explicitly set the transaction time to match what is in the instance itself,
        // preferring update time if it is available and otherwise falling back to create time
        // (and if that is not available, falling back to allowing XTDB to manage it itself)
        Date txnTime = null;
        // Since classifications do not update the entity updateTime itself, we need to also
        // scan the entity's last classification change, to potentially apply a validity against
        // the entire entity document (including all of its classifications) based on these classification changes
        Object latestClassificationChange = xtdbDoc.get(ClassificationMapping.N_LAST_CLASSIFICATION_CHANGE);
        if (latestClassificationChange instanceof Date) {
            txnTime = ((Date) latestClassificationChange);
        }
        Object timeFromDoc = xtdbDoc.get(InstanceAuditHeaderMapping.UPDATE_TIME);
        if (timeFromDoc instanceof Date) {
            Date updateTime = ((Date) timeFromDoc);
            // If both the classification update and an entity property update exist, take the latest one for validity
            if (txnTime == null || txnTime.before(updateTime)) {
                txnTime = updateTime;
            }
        } else {
            timeFromDoc = xtdbDoc.get(InstanceAuditHeaderMapping.CREATE_TIME);
            if (timeFromDoc instanceof Date) {
                txnTime = ((Date) timeFromDoc);
            } else {
                txnTime = new Date();
            }
        }
        tx.put(xtdbDoc, txnTime);
    }

    /**
     * Default equality comparison.
     * @param o object to compare against
     * @return boolean
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        XtdbOMRSRepositoryConnector that = (XtdbOMRSRepositoryConnector) o;
        return luceneConfigured == that.luceneConfigured && synchronousIndex == that.synchronousIndex && luceneRegexes == that.luceneRegexes && Objects.equals(xtdbAPI, that.xtdbAPI);
    }

    /**
     * Default hash calculation.
     * @return int
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), xtdbAPI, luceneConfigured, synchronousIndex, luceneRegexes);
    }

}
