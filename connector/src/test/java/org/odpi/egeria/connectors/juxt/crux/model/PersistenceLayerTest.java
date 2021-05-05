/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.model;

import org.odpi.egeria.connectors.juxt.crux.mocks.MockConnection;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the persistence layer model.
 */
public class PersistenceLayerTest {

    private static final CruxOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    @Test
    void testLatest() {
        try {

            assertEquals(PersistenceLayer.getVersion(connector.getCruxAPI()), PersistenceLayer.LATEST_VERSION, "Version of the embedded Crux node is always expected to match latest version.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testUpdate() {
        try {

            PersistenceLayer.setVersion(connector.getCruxAPI(), 1L);
            assertEquals(PersistenceLayer.getVersion(connector.getCruxAPI()), 1L, "Version of the Crux node expected to match what was set.");
            PersistenceLayer.setVersion(connector.getCruxAPI(), PersistenceLayer.LATEST_VERSION);
            assertTrue(PersistenceLayer.isLatestVersion(connector.getCruxAPI()), "Version of the embedded Crux node is always expected to match latest version.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

}
