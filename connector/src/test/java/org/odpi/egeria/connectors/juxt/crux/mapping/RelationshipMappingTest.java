/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import crux.api.ICruxDatasource;
import org.odpi.egeria.connectors.juxt.crux.mocks.MockConnection;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the mappings of relationship objects.
 */
public class RelationshipMappingTest {

    private static final CruxOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    @Test
    void testValid() {
        try {

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();
            Relationship relationship = helper.getSkeletonRelationship(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "SemanticAssignment");

            InstanceProperties properties = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "description",
                    "some description",
                    this.getClass().getName());
            relationship.setProperties(properties);

            EntityProxy one = getEntityProxy(helper, "Referenceable", "some-referenceable");
            EntityProxy two = getEntityProxy(helper, "GlossaryTerm", "some-glossary-term");

            connector.createEntityProxy(one);
            connector.createEntityProxy(two);

            relationship.setEntityOneProxy(one);
            relationship.setEntityTwoProxy(two);

            ICruxDatasource db = connector.getCruxAPI().db();

            RelationshipMapping egeria = new RelationshipMapping(connector, relationship);
            CruxDocument doc = egeria.toCrux();
            assertNotNull(doc);
            assertEquals(doc.getId(), RelationshipMapping.getReference(relationship.getGUID()), "Crux document ID is expected to be identical to a prefixed GUID of the Egeria object.");

            RelationshipMapping crux = new RelationshipMapping(connector, doc, db);
            Relationship retrieved = crux.toEgeria();
            assertNotNull(retrieved);
            assertEquals(retrieved, relationship, "Relationship is expected to be identical after being retrieved back from conversion.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    private EntityProxy getEntityProxy(OMRSRepositoryHelper helper,
                                       String type,
                                       String name) throws Exception {

        EntityDetail detail = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                MockConnection.METADATA_COLLECTION_ID,
                MockConnection.METADATA_COLLECTION_NAME,
                InstanceProvenanceType.LOCAL_COHORT,
                MockConnection.USERNAME,
                type);

        InstanceProperties ip = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                null,
                "qualifiedName",
                name,
                this.getClass().getName());
        detail.setProperties(ip);

        return helper.getNewEntityProxy(MockConnection.SOURCE_NAME,
                detail);

    }

    @Test
    void testNull() {

        ICruxDatasource db = connector.getCruxAPI().db();

        RelationshipMapping egeria = new RelationshipMapping(connector, null);
        CruxDocument doc = egeria.toCrux();
        assertNull(doc);

        RelationshipMapping crux = new RelationshipMapping(connector, null, db);
        Relationship relationship = crux.toEgeria();
        assertNull(relationship);

    }

}
