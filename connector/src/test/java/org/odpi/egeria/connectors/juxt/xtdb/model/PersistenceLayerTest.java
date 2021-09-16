/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.model;

import org.odpi.egeria.connectors.juxt.xtdb.mocks.MockConnection;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the persistence layer model.
 */
public class PersistenceLayerTest {

    private static final XtdbOMRSRepositoryConnector connector = MockConnection.getMockConnector();

    @Test
    void testLatest() {
        try {

            assertEquals(PersistenceLayer.getVersion(connector.getXtdbAPI()), PersistenceLayer.LATEST_VERSION, "Version of the embedded XTDB node is always expected to match latest version.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

    @Test
    void testUpdate() {
        try {

            PersistenceLayer.setVersion(connector.getXtdbAPI(), 1L);
            assertEquals(PersistenceLayer.getVersion(connector.getXtdbAPI()), 1L, "Version of the XTDB node expected to match what was set.");
            PersistenceLayer.setVersion(connector.getXtdbAPI(), PersistenceLayer.LATEST_VERSION);
            assertTrue(PersistenceLayer.isLatestVersion(connector.getXtdbAPI()), "Version of the embedded XTDB node is always expected to match latest version.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

}
