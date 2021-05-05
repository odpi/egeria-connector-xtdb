/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import crux.api.CruxDocument;
import org.odpi.egeria.connectors.juxt.crux.mocks.MockConnection;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityDetail;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProvenanceType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the mappings of entity detail objects.
 */
public class EntityDetailMappingTest {

    private static final CruxOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    @Test
    void testValid() {
        try {

            OMRSRepositoryHelper helper = connector.getRepositoryHelper();
            EntityDetail detail = helper.getSkeletonEntity(MockConnection.SOURCE_NAME,
                    MockConnection.METADATA_COLLECTION_ID,
                    MockConnection.METADATA_COLLECTION_NAME,
                    InstanceProvenanceType.LOCAL_COHORT,
                    MockConnection.USERNAME,
                    "Referenceable");

            InstanceProperties ip = helper.addStringPropertyToInstance(MockConnection.SOURCE_NAME,
                    null,
                    "qualifiedName",
                    "a-qualified-name",
                    this.getClass().getName());
            detail.setProperties(ip);

            EntityDetailMapping egeria = new EntityDetailMapping(connector, detail);
            CruxDocument doc = egeria.toCrux();
            assertNotNull(doc);
            assertEquals(doc.getId(), EntityDetailMapping.getReference(detail.getGUID()), "Crux document ID is expected to be identical to a prefixed GUID of the Egeria object.");

            EntityDetailMapping crux = new EntityDetailMapping(connector, doc);
            EntityDetail retrieved = crux.toEgeria();
            assertNotNull(retrieved);
            assertEquals(retrieved, detail, "EntityDetail is expected to be identical after being retrieved back from conversion.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testNull() {

        EntityDetailMapping egeria = new EntityDetailMapping(connector, (EntityDetail) null);
        CruxDocument doc = egeria.toCrux();
        assertNull(doc);

        EntityDetailMapping crux = new EntityDetailMapping(connector, (CruxDocument) null);
        EntityDetail detail = crux.toEgeria();
        assertNull(detail);

    }

}
