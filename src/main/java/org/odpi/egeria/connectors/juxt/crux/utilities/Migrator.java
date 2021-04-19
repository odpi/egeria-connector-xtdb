/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.utilities;

import crux.api.Crux;
import crux.api.ICruxAPI;
import org.odpi.egeria.connectors.juxt.crux.model.PersistenceLayer;
import org.odpi.egeria.connectors.juxt.crux.model.migration.UpgradeInitialTo2;

import java.io.File;

/**
 * Handles migration of already-stored metadata from one release of the connector to another, when
 * necessary.
 */
public class Migrator {

    public static final int DEFAULT_BATCH_SIZE = 500;

    public static void main(String[] args) {

        if (args.length == 0) {
            System.err.println("No configuration file path provided. The path to a JSON configuration file for the node must be provided.");
            System.exit(1);
        }

        String configFile = args[0];
        File config = new File(configFile);

        int batchSize = DEFAULT_BATCH_SIZE;
        if (args.length == 2) {
            batchSize = Integer.parseInt(args[1]);
        }

        System.out.println("Starting a Crux node using configuration: " + configFile);
        try (ICruxAPI cruxAPI = Crux.startNode(config)) {

            System.out.println("... checking if migration is needed (" + cruxAPI + ")");

            if (PersistenceLayer.isLatestVersion(cruxAPI)) {
                System.out.println("This node is already at the latest version of the persistence layer -- no migration needed.");
            } else {

                while (!PersistenceLayer.isLatestVersion(cruxAPI)) {

                    long currentVersion = PersistenceLayer.getVersion(cruxAPI);
                    System.out.println("The node is at version " + currentVersion + ", while latest is " + PersistenceLayer.LATEST_VERSION + " -- migrating...");

                    if (currentVersion == -1) {
                        UpgradeInitialTo2 upgradeInitialTo2 = new UpgradeInitialTo2(cruxAPI, batchSize);
                        upgradeInitialTo2.migrate();
                    }

                }

            }

        } catch (Exception ex) {
            System.err.println("Fatal error!");
            ex.printStackTrace();
        }

    }

}
