/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mocks;

import org.odpi.egeria.connectors.juxt.crux.mapping.Constants;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider;
import org.odpi.openmetadata.adapters.repositoryservices.ConnectorConfigurationFactory;
import org.odpi.openmetadata.frameworks.connectors.Connector;
import org.odpi.openmetadata.frameworks.connectors.ConnectorBroker;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectionCheckedException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.Connection;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.ConnectorType;
import org.odpi.openmetadata.opentypes.OpenMetadataTypesArchive;
import org.odpi.openmetadata.repositoryservices.auditlog.OMRSAuditLog;
import org.odpi.openmetadata.repositoryservices.auditlog.OMRSAuditLogDestination;
import org.odpi.openmetadata.frameworks.auditlog.ComponentDevelopmentStatus;
import org.odpi.openmetadata.repositoryservices.connectors.stores.archivestore.properties.OpenMetadataArchive;
import org.odpi.openmetadata.repositoryservices.connectors.stores.archivestore.properties.OpenMetadataArchiveTypeStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.auditlogstore.OMRSAuditLogStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.AttributeTypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentHelper;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentManager;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Mocked connection for the CruxOMRSRepositoryConnector.
 */
public class MockConnection extends Connection {

    public static final String SOURCE_NAME = "MockCruxConnection";
    public static final String METADATA_COLLECTION_ID = "123";
    public static final String METADATA_COLLECTION_NAME = "Crux repo for unit testing";
    public static final String USERNAME = "CruxUser";

    private static CruxOMRSRepositoryConnector connector = null;
    private static OMRSRepositoryContentManager contentManager = null;

    /**
     * Create a mocked connection that can be used for unit testing purposes, using a default
     * entirely in-memory embedded Crux configuration.
     */
    public MockConnection() {

        super();

        setDisplayName("Mock Crux Connection");
        setDescription("A pretend Crux connection.");

        ConnectorType connectorType = new ConnectorType();
        connectorType.setConnectorProviderClassName("org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider");
        setConnectorType(connectorType);

    }

    /**
     * Startup the connector.
     */
    private synchronized static void startConnector() {

        Map<String, String> luceneConfig = new HashMap<>();

        Map<String, Object> cruxConfig = new HashMap<>();
        cruxConfig.put(Constants.CRUX_LUCENE, luceneConfig);

        Map<String, Object> config = new HashMap<>();
        config.put(CruxOMRSRepositoryConnectorProvider.CRUX_CONFIG, cruxConfig);
        config.put(CruxOMRSRepositoryConnectorProvider.LUCENE_REGEXES, true);
        config.put(CruxOMRSRepositoryConnectorProvider.SYNCHRONOUS_INDEX, true);

        Connection mock = new MockConnection();
        mock.setConfigurationProperties(config);
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
        OMRSAuditLogDestination destination = new OMRSAuditLogDestination("MockCrux", "Crux", "ODPi", auditLogDestinations);
        OMRSAuditLog auditLog = new OMRSAuditLog(destination, -1, ComponentDevelopmentStatus.IN_DEVELOPMENT, "ConnectorTest", "Testing of the connector", null);
        contentManager = new OMRSRepositoryContentManager(USERNAME, auditLog);

        try {
            Object candidate = broker.getConnector(mock);
            assertTrue(candidate instanceof CruxOMRSRepositoryConnector);
            connector = (CruxOMRSRepositoryConnector) candidate;
            connector.setAuditLog(auditLog);
            connector.setRepositoryHelper(new OMRSRepositoryContentHelper(contentManager));
            connector.setRepositoryValidator(new OMRSRepositoryContentValidator(contentManager));
            connector.setMetadataCollectionId(METADATA_COLLECTION_ID);
            connector.setMetadataCollectionName(METADATA_COLLECTION_NAME);
            connector.start();
        } catch (ConnectionCheckedException | ConnectorCheckedException e) {
            e.printStackTrace();
            assertNull(e, "Unable to get connector via the broker.");
        }

    }

    /**
     * Load all of the open types for the release we are building against.
     */
    private synchronized static void initAllOpenTypes() {

        try {
            OpenMetadataArchive archive = new OpenMetadataTypesArchive().getOpenMetadataArchive();
            OpenMetadataArchiveTypeStore typeStore = archive.getArchiveTypeStore();
            List<AttributeTypeDef> attributeTypeDefList = typeStore.getAttributeTypeDefs();
            List<TypeDef> typeDefList = typeStore.getNewTypeDefs();
            for (AttributeTypeDef attributeTypeDef : attributeTypeDefList) {
                connector.getMetadataCollection().addAttributeTypeDef(USERNAME, attributeTypeDef);
                contentManager.addAttributeTypeDef(connector.getRepositoryName(), attributeTypeDef);
            }
            for (TypeDef typeDef : typeDefList) {
                connector.getMetadataCollection().addTypeDef(USERNAME, typeDef);
                contentManager.addTypeDef(connector.getRepositoryName(), typeDef);
            }
            // TODO: if needed for some edge case, may want to eventually load and apply type def patches...
        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    /**
     * Retrieve a mock connection to a Crux repository.
     * @return CruxOMRSRepositoryConnector
     */
    public synchronized static CruxOMRSRepositoryConnector getMockConnector() {
        if (connector == null) {
            startConnector();
            initAllOpenTypes();
        }
        return connector;
    }

}
