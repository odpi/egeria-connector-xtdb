/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.mocks.MockConnection;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Tests the mappings of entity summary objects.
 */
public class EntitySummaryMappingTest {

    private static final CruxOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    @Test
    void testValid() {
        try {

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();
            EntitySummary summary = helper.getSkeletonEntitySummary(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "Referenceable");

            InstanceProperties properties = helper.addIntPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "level",
                    3,
                    this.getClass().getName());
            Classification classification = helper.getNewClassification(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "Confidentiality",
                    "Referenceable",
                    ClassificationOrigin.ASSIGNED,
                    null,
                    properties);
            List<Classification> classifications = new ArrayList<>();
            classifications.add(classification);
            summary.setClassifications(classifications);

            EntitySummaryMapping egeria = new EntitySummaryMapping(connector, summary);
            CruxDocument doc = egeria.toCrux();
            assertNotNull(doc);
            assertEquals(doc.getId(), EntitySummaryMapping.getReference(summary.getGUID()), "Crux document ID is expected to be identical to a prefixed GUID of the Egeria object.");

            EntitySummaryMapping crux = new EntitySummaryMapping(connector, doc);
            EntitySummary retrieved = crux.toEgeria();
            assertNotNull(retrieved);
            assertEquals(retrieved, summary, "EntitySummary is expected to be identical after being retrieved back from conversion.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testNull() {

        EntitySummaryMapping egeria = new EntitySummaryMapping(connector, (EntitySummary) null);
        CruxDocument doc = egeria.toCrux();
        assertNull(doc);

        EntitySummaryMapping crux = new EntitySummaryMapping(connector, (CruxDocument) null);
        EntitySummary summary = crux.toEgeria();
        assertNull(summary);

    }

}
