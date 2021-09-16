/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.migration;

import xtdb.api.IXtdb;
import org.odpi.egeria.connectors.juxt.xtdb.migration.model.UpgradeInitialTo2;
import org.odpi.egeria.connectors.juxt.xtdb.model.PersistenceLayer;
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
            try {
                batchSize = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                log.warn("Unable to parse batch size '{}' as an integer -- falling back to default batch size.", args[1]);
            }
        }

        log.info("Starting a XTDB node using configuration: {}", configFile);
        try (IXtdb xtdbAPI = IXtdb.startNode(config)) {

            log.info("... checking if migration is needed ({})", xtdbAPI);

            if (PersistenceLayer.isLatestVersion(xtdbAPI)) {
                log.info("This node is already at the latest version of the persistence layer ({}) -- no migration needed.", PersistenceLayer.LATEST_VERSION);
            } else {

                while (!PersistenceLayer.isLatestVersion(xtdbAPI)) {

                    long currentVersion = PersistenceLayer.getVersion(xtdbAPI);
                    log.info("The node is at version {}, while latest is {} -- migrating...", currentVersion, PersistenceLayer.LATEST_VERSION);

                    if (currentVersion == -1) {
                        UpgradeInitialTo2 upgradeInitialTo2 = new UpgradeInitialTo2(xtdbAPI, batchSize);
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
