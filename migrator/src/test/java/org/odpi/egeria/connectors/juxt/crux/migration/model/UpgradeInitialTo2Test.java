/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.migration.model;

import crux.api.Crux;
import crux.api.ICruxAPI;
import org.odpi.egeria.connectors.juxt.crux.model.PersistenceLayer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test the application of the upgrade.
 */
public class UpgradeInitialTo2Test {

    private static final ICruxAPI embedded = Crux.startNode();
    private static final UpgradeInitialTo2 upgrade = new UpgradeInitialTo2(embedded, 123);

    @BeforeClass
    void setup() {
        // Start off by setting up a back-level repository
        PersistenceLayer.setVersion(embedded, 1L);
    }

    @Test
    void testBasics() {
        assertEquals(upgrade.getFromVersion(), -1L, "Expected to upgrade from version -1.");
        assertEquals(upgrade.getToVersion(), 2L, "Expected to upgrade to version 2.");
        assertEquals(upgrade.getBatchSize(), 123, "Expected batch size to match what was set explicitly.");
    }

    @Test
    void testMigration() {
        try {

            upgrade.migrate();
            assertTrue(PersistenceLayer.isLatestVersion(embedded), "Expected persistence to be at the latest version after migration.");

        } catch (Exception e) {
            e.printStackTrace();
            assertNull(e);
        }
    }

}
