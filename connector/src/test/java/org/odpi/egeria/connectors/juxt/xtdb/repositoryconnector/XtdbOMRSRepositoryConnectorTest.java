/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector;

import clojure.lang.IPersistentVector;
import org.odpi.egeria.connectors.juxt.xtdb.txnfn.AddEntityProxy;
import org.odpi.egeria.connectors.juxt.xtdb.txnfn.PurgeEntity;
import org.odpi.egeria.connectors.juxt.xtdb.txnfn.PurgeRelationship;
import xtdb.api.tx.Transaction;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.Constants;
import org.odpi.egeria.connectors.juxt.xtdb.mocks.MockConnection;
import org.odpi.openmetadata.adapters.repositoryservices.ConnectorConfigurationFactory;
import org.odpi.openmetadata.frameworks.connectors.Connector;
import org.odpi.openmetadata.frameworks.connectors.ConnectorBroker;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectionCheckedException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.Connection;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.ConnectorType;
import org.odpi.openmetadata.repositoryservices.auditlog.OMRSAuditLog;
import org.odpi.openmetadata.repositoryservices.auditlog.OMRSAuditLogDestination;
import org.odpi.openmetadata.repositoryservices.connectors.stores.auditlogstore.OMRSAuditLogStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.HistorySequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityConflictException;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentHelper;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentManager;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentValidator;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.testng.Assert.*;

/**
 * Tests the repository connector itself.
 */
public class XtdbOMRSRepositoryConnectorTest {

    private static final XtdbOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    @Test
    void testStart() {

        Map<String, String> luceneConfig = new HashMap<>();
        try {
            Path dir = Files.createTempDirectory("lucene");
            luceneConfig.put("db-dir", dir.toString());
        } catch (IOException e) {
            e.printStackTrace();
            assertNull(e, "Unable to create temporary directory for connector config.");
        }

        Map<String, Object> xtdbConfig = new HashMap<>();
        xtdbConfig.put(Constants.XTDB_LUCENE, luceneConfig);

        Map<String, Object> config = new HashMap<>();
        config.put(XtdbOMRSRepositoryConnectorProvider.XTDB_CONFIG, xtdbConfig);
        config.put(XtdbOMRSRepositoryConnectorProvider.LUCENE_REGEXES, true);
        config.put(XtdbOMRSRepositoryConnectorProvider.SYNCHRONOUS_INDEX, true);

        Connection tmpConnection = new Connection();
        tmpConnection.setDisplayName("Mock XTDB Connection");
        tmpConnection.setDescription("A pretend XTDB connection.");
        ConnectorType connectorType = new ConnectorType();
        connectorType.setConnectorProviderClassName("org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnectorProvider");
        tmpConnection.setConnectorType(connectorType);
        tmpConnection.setConfigurationProperties(config);

        ConnectorConfigurationFactory connectorConfigurationFactory = new ConnectorConfigurationFactory();
        ConnectorBroker broker = new ConnectorBroker();

        Connector auditLogConnector = null;
        try {
            auditLogConnector = broker.getConnector(connectorConfigurationFactory.getDefaultAuditLogConnection());
            auditLogConnector.start();
        } catch (ConnectionCheckedException | ConnectorCheckedException e) {
            e.printStackTrace();
            assertNull(e, "Unable to get or start audit log via the broker.");
        }

        List<OMRSAuditLogStore> auditLogDestinations = new ArrayList<>();
        auditLogDestinations.add((OMRSAuditLogStore)auditLogConnector);
        OMRSAuditLogDestination destination = new OMRSAuditLogDestination("ConnectorTest", "XTDB", "ODPi", auditLogDestinations);
        OMRSAuditLog auditLog = new OMRSAuditLog(destination, -1, "ConnectorTest", "Testing of the connector", null);
        OMRSRepositoryContentManager contentManager = new OMRSRepositoryContentManager(MockConnection.USERNAME, auditLog);

        try {
            Object candidate = broker.getConnector(tmpConnection);
            assertTrue(candidate instanceof XtdbOMRSRepositoryConnector);
            XtdbOMRSRepositoryConnector tmp = (XtdbOMRSRepositoryConnector) candidate;
            tmp.setAuditLog(auditLog);
            tmp.setRepositoryHelper(new OMRSRepositoryContentHelper(contentManager));
            tmp.setRepositoryValidator(new OMRSRepositoryContentValidator(contentManager));
            tmp.setMetadataCollectionId(MockConnection.METADATA_COLLECTION_ID);
            tmp.setMetadataCollectionName(MockConnection.METADATA_COLLECTION_NAME);
            tmp.start();
            tmp.disconnect();
        } catch (ConnectionCheckedException | ConnectorCheckedException e) {
            e.printStackTrace();
            assertNull(e, "Unable to get connector via the broker.");
        }

    }

    @Test
    void testDates() {
        try {

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();
            EntityDetail original = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "GlossaryTerm");

            Date now = new Date();
            original.setUpdateTime(now);

            assertEquals(original.getUpdateTime(), now, "Expected retrieving the same property should give identical results.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testEntityLifecycle() {
        try {

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();
            EntityDetail original = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "GlossaryTerm");

            InstanceProperties ip = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-term",
                    this.getClass().getName());
            original.setProperties(ip);

            InstanceProperties classificationProperties = helper.addIntPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "level",
                    3,
                    this.getClass().getName());

            Classification classification = helper.getNewClassification(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "Confidentiality",
                    "GlossaryTerm",
                    ClassificationOrigin.ASSIGNED,
                    null,
                    classificationProperties);
            List<Classification> classifications = new ArrayList<>();
            classifications.add(classification);
            original.setClassifications(classifications);

            // Create
            EntityDetail result = connector.createEntity(original);
            assertEquals(result, original, "Expected resulting entity to be identical to sent entity.");

            // Read
            EntityDetail retrieved = connector.getEntity(original.getGUID(), null, false);
            assertEquals(retrieved, original, "Expected retrieved entity to be identical to the entity sent to be stored.");

            EntityProxy proxy = connector.getEntityProxy(original.getGUID());
            EntityProxy asProxy = helper.getNewEntityProxy(MockConnection.SOURCE_NAME, retrieved);
            assertEquals(proxy, asProxy, "Expected entity, when retrieved as a proxy, to be identical to the proxy representation of the original entity.");

            EntitySummary summary = connector.getEntitySummary(original.getGUID());
            EntitySummary asSummary = new EntitySummary(retrieved);
            assertEquals(summary, asSummary, "Expected entity, when retrieved as a summary, to be identical to the summary representation of the original entity.");

            // Update
            EntityDetail update = new EntityDetail(retrieved);
            InstanceProperties ip2 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "displayName",
                    "some-term",
                    this.getClass().getName());
            ip2 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    ip2,
                    "qualifiedName",
                    "some-term",
                    this.getClass().getName());
            update.setProperties(ip2);
            update.setVersion(2L);
            update.setUpdatedBy(MockConnection.USERNAME);
            Date updatedTime = new Date();
            update.setUpdateTime(updatedTime);

            EntityDetail updated = connector.updateEntity(update);
            assertEquals(updated, update, "Expected resulting entity from update to be identical to sent entity for update.");

            retrieved = connector.getEntity(original.getGUID(), null, false);
            assertEquals(retrieved, updated, "Expected retrieved updated entity to be identical to the updated entity sent to be stored.");

            List<EntityDetail> previousVersions = connector.getPreviousVersionsOfEntity(original.getGUID(), null, null, 0, 100, HistorySequencingOrder.BACKWARDS);
            assertNotNull(previousVersions, "Expected there to be some previous versions of the entity.");
            assertEquals(previousVersions.size(), 2, "Two previous versions of the entity were expected.");

            // Restore
            EntityDetail previous = connector.restorePreviousVersionOfEntity(MockConnection.USERNAME, original.getGUID());
            EntityDetail rolledBack = new EntityDetail(original);
            rolledBack.setVersion(3L);
            rolledBack.setUpdatedBy(MockConnection.USERNAME);
            rolledBack.setUpdateTime(previous.getUpdateTime());
            List<String> maintainers = new ArrayList<>();
            maintainers.add(MockConnection.USERNAME);
            rolledBack.setMaintainedBy(maintainers);
            assertEquals(previous, rolledBack, "Expected the previous version of the entity to be identical to the original version (except for versions and modification details).");

            retrieved = connector.getEntity(original.getGUID(), null, false);
            assertEquals(retrieved, previous, "Expected retrieved restored entity to be identical to the restored entity that was returned.");

            // Purge
            PurgeEntity.transactWithoutValidation(connector, original.getGUID());

            retrieved = connector.getEntity(original.getGUID(), null, false);
            assertNull(retrieved, "Expected the entity to no longer exist after it has been purged.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testRelationshipLifecycle() {
        try {

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();

            Relationship original = helper.getSkeletonRelationship(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "SemanticAssignment");

            InstanceProperties assetProperties = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-asset",
                    this.getClass().getName());

            EntityProxy asset = helper.getNewEntityProxy(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "Asset",
                    assetProperties,
                    null);

            AddEntityProxy.transact(connector, asset);

            InstanceProperties termProperties = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-term",
                    this.getClass().getName());

            EntityProxy term = helper.getNewEntityProxy(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "GlossaryTerm",
                    termProperties,
                    null);

            AddEntityProxy.transact(connector, term);

            original.setEntityOneProxy(asset);
            original.setEntityTwoProxy(term);

            InstanceProperties relationshipProperties = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "steward",
                    MockConnection.USERNAME,
                    this.getClass().getName());

            original.setProperties(relationshipProperties);

            // Create
            Relationship result = connector.createRelationship(original);
            assertEquals(result, original, "Expected resulting relationship to be identical to sent relationship.");

            // Read
            Relationship retrieved = connector.getRelationship(original.getGUID(), null);
            assertEquals(retrieved, original, "Expected retrieved relationship to be identical to the relationship sent to be stored.");

            // Update
            Relationship update = new Relationship(retrieved);
            InstanceProperties ip2 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "steward",
                    MockConnection.USERNAME,
                    this.getClass().getName());
            ip2 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    ip2,
                    "description",
                    "some-description",
                    this.getClass().getName());
            update.setProperties(ip2);
            update.setVersion(2L);
            update.setUpdatedBy(MockConnection.USERNAME);
            Date updatedTime = new Date();
            update.setUpdateTime(updatedTime);

            Relationship updated = connector.updateRelationship(update);
            assertEquals(updated, update, "Expected resulting relationship from update to be identical to sent relationship for update.");

            retrieved = connector.getRelationship(original.getGUID(), null);
            assertEquals(retrieved, updated, "Expected retrieved updated relationship to be identical to the updated relationship sent to be stored.");

            List<Relationship> previousVersions = connector.getPreviousVersionsOfRelationship(original.getGUID(), null, null, 0, 100, HistorySequencingOrder.BACKWARDS);
            assertNotNull(previousVersions, "Expected there to be some previous versions of the relationship.");
            assertEquals(previousVersions.size(), 2, "Two previous versions of the relationship were expected.");

            // Restore
            Relationship previous = connector.restorePreviousVersionOfRelationship(MockConnection.USERNAME, original.getGUID());
            Relationship rolledBack = new Relationship(original);
            rolledBack.setVersion(3L);
            rolledBack.setUpdatedBy(MockConnection.USERNAME);
            rolledBack.setUpdateTime(previous.getUpdateTime());
            List<String> maintainers = new ArrayList<>();
            maintainers.add(MockConnection.USERNAME);
            rolledBack.setMaintainedBy(maintainers);
            assertEquals(previous, rolledBack, "Expected the previous version of the relationship to be identical to the original version (except for versions and modification details).");

            retrieved = connector.getRelationship(original.getGUID(), null);
            assertEquals(retrieved, previous, "Expected retrieved restored relationship to be identical to the restored relationship that was returned.");

            // Purge
            PurgeRelationship.transactWithoutValidation(connector, original.getGUID());

            retrieved = connector.getRelationship(original.getGUID(), null);
            assertNull(retrieved, "Expected the relationship to no longer exist after it has been purged.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testEntityFind() {
        try {

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();

            EntityDetail profile1 = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "ActorProfile");

            InstanceProperties ip1 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-profile-1",
                    this.getClass().getName());
            profile1.setProperties(ip1);

            EntityDetail result = connector.createEntity(profile1);
            assertNotNull(result, "Expected an ActorProfile to be created.");

            EntityDetail profile2 = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "ActorProfile");

            InstanceProperties ip2 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-profile-2",
                    this.getClass().getName());
            profile2.setProperties(ip2);

            result = connector.createEntity(profile2);
            assertNotNull(result, "Expected an ActorProfile to be created.");

            List<EntityDetail> results = connector.findEntities("5a2f38dc-d69d-4a6f-ad26-ac86f118fa35",
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    100,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertEquals(results.size(), 2, "Expected precisely two search results.");

            results = connector.findEntities(null,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    100,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertTrue(results.size() >= 2, "Expected at least two search results.");

            results = connector.findEntitiesByText("5a2f38dc-d69d-4a6f-ad26-ac86f118fa35",
                    helper.getStartsWithRegex("some"),
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    100,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertEquals(results.size(), 2, "Expected precisely two search results.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testRelationshipFind() {
        try {

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();

            EntityDetail category1 = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "GlossaryCategory");

            InstanceProperties ip1 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-category-1",
                    this.getClass().getName());
            category1.setProperties(ip1);

            EntityDetail result = connector.createEntity(category1);
            assertNotNull(result, "Expected a category to be created.");

            EntityDetail category2 = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "GlossaryCategory");

            InstanceProperties ip2 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-category-2",
                    this.getClass().getName());
            category2.setProperties(ip2);

            result = connector.createEntity(category2);
            assertNotNull(result, "Expected a category to be created.");

            EntityDetail glossary = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "Glossary");

            InstanceProperties ip3 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-glossary",
                    this.getClass().getName());
            glossary.setProperties(ip3);

            result = connector.createEntity(glossary);
            assertNotNull(result, "Expected a glossary to be created.");

            Relationship one = helper.getSkeletonRelationship(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "CategoryAnchor");

            one.setEntityOneProxy(helper.getNewEntityProxy(MockConnection.SOURCE_NAME, glossary));
            one.setEntityTwoProxy(helper.getNewEntityProxy(MockConnection.SOURCE_NAME, category1));

            Relationship first = connector.createRelationship(one);
            assertNotNull(first, "Expected a CategoryAnchor relationship to be created.");

            Relationship two = helper.getSkeletonRelationship(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "CategoryHierarchyLink");

            two.setEntityOneProxy(helper.getNewEntityProxy(MockConnection.SOURCE_NAME, category1));
            two.setEntityTwoProxy(helper.getNewEntityProxy(MockConnection.SOURCE_NAME, category2));

            Relationship second = connector.createRelationship(two);
            assertNotNull(second, "Expected a CategoryHierarchyLink relationship to be created.");

            Relationship three = helper.getSkeletonRelationship(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "CategoryHierarchyLink");

            three.setEntityOneProxy(helper.getNewEntityProxy(MockConnection.SOURCE_NAME, category2));
            three.setEntityTwoProxy(helper.getNewEntityProxy(MockConnection.SOURCE_NAME, category1));
            three.setStatus(InstanceStatus.DELETED);

            Relationship third = connector.createRelationship(three);
            assertNotNull(third, "Expected a CategoryHierarchyLink relationship to be created.");
            Relationship retrieved = connector.getRelationship(three.getGUID(), null);
            assertNotNull(retrieved, "Expected to be able to retrieve the relationship back again.");

            List<Relationship> results = connector.findRelationshipsForEntity(category1.getGUID(),
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    100,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertEquals(results.size(), 2, "Expected precisely two search results.");

            results = connector.findHomedRelationshipsForEntity(category1, MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertEquals(results.size(), 2, "Expected precisely two search results.");

            results = connector.findActiveRelationshipsForEntity(category1, MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertEquals(results.size(), 2, "Expected precisely two search results.");

            Collection<List<?>> otherResults = connector.findEntityRelationships(connector.getXtdbAPI().db(),
                    category1.getGUID(),
                    MockConnection.USERNAME,
                    true);
            assertNotNull(otherResults, "Expected some search results.");
            assertEquals(otherResults.size(), 3, "Expected precisely three search results.");

            results = connector.findRelationshipsForEntity(category1.getGUID(),
                    "71e4b6fb-3412-4193-aff3-a16eccd87e8e",
                    0,
                    null,
                    null,
                    null,
                    null,
                    100,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertEquals(results.size(), 1, "Expected precisely one search result.");

            List<String> typeGuids = new ArrayList<>();
            typeGuids.add("71e4b6fb-3412-4193-aff3-a16eccd87e8e");
            typeGuids.add("c628938e-815e-47db-8d1c-59bb2e84e028");
            results = connector.findRelationships(null,
                    typeGuids,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    0,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertEquals(results.size(), 2, "Expected precisely two search results.");

            results = connector.findRelationships("c628938e-815e-47db-8d1c-59bb2e84e028",
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    0,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertEquals(results.size(), 1, "Expected precisely one search result.");

            results = connector.findRelationships(null,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    0,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected some search results.");
            assertTrue(results.size() >= 2, "Expected at least two search results.");

            // Note that this should not return any results, as there are no properties (let alone
            // any string properties) on the CategoryAnchor relationship type being searched.
            results = connector.findRelationshipsByText("c628938e-815e-47db-8d1c-59bb2e84e028",
                    helper.getStartsWithRegex("some"),
                    0,
                    null,
                    null,
                    null,
                    null,
                    100,
                    MockConnection.USERNAME);
            assertNotNull(results, "Expected non-null search results.");
            assertEquals(results.size(), 0, "Expected precisely zero search results.");

            InstanceGraph graph = connector.findNeighborhood(glossary.getGUID(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    5,
                    true);
            assertNotNull(graph, "Expected some graph query results.");
            assertEquals(graph.getRelationships().size(), 2, "Expected precisely two relationship results.");
            assertEquals(graph.getEntities().size(), 3, "Expected precisely three entity results.");

            graph = connector.findNeighborhood(glossary.getGUID(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    5,
                    false);
            assertNotNull(graph, "Expected some graph query results.");
            assertNull(graph.getRelationships(), "Expected no relationships in the results when explicitly excluded.");
            assertEquals(graph.getEntities().size(), 3, "Expected precisely three entity results.");

            graph = connector.getTraversalsBetweenEntities(glossary.getGUID(),
                    category2.getGUID(),
                    null,
                    null);
            assertNotNull(graph, "Expected some graph query results.");
            assertEquals(graph.getRelationships().size(), 2, "Expected precisely two relationship results.");
            assertEquals(graph.getEntities().size(), 3, "Expected precisely three entity results.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testReferenceCopies() {
        try {

            String externalMetadataCollectionId = "999";
            String externalMetadataCollectionName = "Imaginary external metadata collection";

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();

            EntityDetail entityRC = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    externalMetadataCollectionId,
                    externalMetadataCollectionName,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "ActorProfile");

            InstanceProperties ip1 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "external-profile-X",
                    this.getClass().getName());
            entityRC.setProperties(ip1);

            connector.saveReferenceCopy(entityRC);

            EntityDetail retrieved = connector.getEntity(entityRC.getGUID(), null, false);
            assertEquals(retrieved, entityRC, "Expected retrieved reference copy to be identical to saved reference copy.");

            EntityDetail entityRCUpdate = new EntityDetail(entityRC);
            entityRCUpdate.setVersion(2L);
            entityRCUpdate.setUpdateTime(new Date());
            entityRCUpdate.setUpdatedBy(MockConnection.USERNAME);

            connector.saveReferenceCopy(entityRCUpdate);

            retrieved = connector.getEntity(entityRC.getGUID(), null, false);
            assertEquals(retrieved, entityRCUpdate, "Expected retrieved reference copy to be identical to saved reference copy after update.");

            EntityDetail entityInvalid = new EntityDetail(entityRCUpdate);
            entityInvalid.setVersion(3L);
            entityInvalid.setMetadataCollectionId(MockConnection.METADATA_COLLECTION_ID);
            entityInvalid.setMetadataCollectionName(MockConnection.METADATA_COLLECTION_NAME);

            assertThrows(EntityConflictException.class, () -> connector.saveReferenceCopy(entityInvalid));

            EntityDetail category1 = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    externalMetadataCollectionId,
                    externalMetadataCollectionName,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "GlossaryCategory");

            InstanceProperties ip2 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-category-1",
                    this.getClass().getName());
            category1.setProperties(ip2);

            EntityDetail glossary = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    externalMetadataCollectionId,
                    externalMetadataCollectionName,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "Glossary");

            InstanceProperties ip3 = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "some-glossary",
                    this.getClass().getName());
            glossary.setProperties(ip3);

            Relationship relationshipRC = helper.getSkeletonRelationship(MockConnection.SOURCE_NAME,
                    externalMetadataCollectionId,
                    externalMetadataCollectionName,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "CategoryAnchor");

            relationshipRC.setEntityOneProxy(helper.getNewEntityProxy(MockConnection.SOURCE_NAME, glossary));
            relationshipRC.setEntityTwoProxy(helper.getNewEntityProxy(MockConnection.SOURCE_NAME, category1));

            Transaction.Builder tx = Transaction.builder();
            connector.addSaveReferenceCopyStatements(tx, relationshipRC);
            Transaction built = tx.build();
            IPersistentVector txVector = built.toVector();
            assertNotNull(txVector, "Expected there to be a vector containing the built transaction.");
            assertEquals(txVector.length(), 1, "Expected the vector to have precisely one item within it.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

}
