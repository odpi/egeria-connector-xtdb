/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.crux.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnector;

/**
 * General utilities for the mapping of any instance data.
 */
public abstract class AbstractMapping {

    protected static final ObjectMapper mapper = new ObjectMapper();

    protected final CruxOMRSRepositoryConnector cruxConnector;

    /**
     * Default constructor.
     * @param cruxConnector connectivity to Crux
     */
    protected AbstractMapping(CruxOMRSRepositoryConnector cruxConnector) {
        this.cruxConnector = cruxConnector;
    }

}
