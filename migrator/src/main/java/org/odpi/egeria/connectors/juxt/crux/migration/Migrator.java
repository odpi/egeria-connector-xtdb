/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.migration;

import crux.api.Crux;
import crux.api.ICruxAPI;
import org.odpi.egeria.connectors.juxt.crux.migration.model.UpgradeInitialTo2;
import org.odpi.egeria.connectors.juxt.crux.model.PersistenceLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Handles migration of already-stored metadata from one release of the connector to another, when
 * necessary.
 */
public class Migrator {

    public static final int DEFAULT_BATCH_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(Migrator.class);

    public static void main(String[] args) {

        if (args.length == 0) {
            log.error("No configuration file path provided. The path to a JSON configuration file for the node must be provided.");
            System.exit(1);
        }

        String configFile = args[0];
        File config = new File(configFile);

        int batchSize = DEFAULT_BATCH_SIZE;
        if (args.length == 2) {
            batchSize = Integer.parseInt(args[1]);
        }

        log.info("Starting a Crux node using configuration: {}", configFile);
        try (ICruxAPI cruxAPI = Crux.startNode(config)) {

            log.info("... checking if migration is needed ({})", cruxAPI);

            if (PersistenceLayer.isLatestVersion(cruxAPI)) {
                log.info("This node is already at the latest version of the persistence layer ({}) -- no migration needed.", PersistenceLayer.LATEST_VERSION);
            } else {

                while (!PersistenceLayer.isLatestVersion(cruxAPI)) {

                    long currentVersion = PersistenceLayer.getVersion(cruxAPI);
                    log.info("The node is at version {}, while latest is {} -- migrating...", currentVersion, PersistenceLayer.LATEST_VERSION);

                    if (currentVersion == -1) {
                        UpgradeInitialTo2 upgradeInitialTo2 = new UpgradeInitialTo2(cruxAPI, batchSize);
                        upgradeInitialTo2.migrate();
                    }

                }

            }

        } catch (Exception e) {
            log.error("Fatal error!", e);
        }
        log.info("Work complete -- exiting.");

    }

}
