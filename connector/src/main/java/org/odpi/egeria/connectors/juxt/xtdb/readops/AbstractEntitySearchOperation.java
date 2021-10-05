/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.readops;

import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSAuditCode;
import org.odpi.egeria.connectors.juxt.xtdb.auditlog.XtdbOMRSErrorCode;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.Constants;
import org.odpi.egeria.connectors.juxt.xtdb.mapping.EntityDetailMapping;
import org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityDetail;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchClassifications;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdb.api.IXtdb;
import xtdb.api.IXtdbDatasource;
import xtdb.api.XtdbDocument;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Base class that all search operations that retrieve entities should implement.
 */
public abstract class AbstractEntitySearchOperation extends AbstractSearchOperation {

    private static final Logger log = LoggerFactory.getLogger(AbstractEntitySearchOperation.class);

    /**
     * {@inheritDoc}
     */
    protected AbstractEntitySearchOperation(XtdbOMRSRepositoryConnector xtdb,
                                            String entityTypeGUID,
                                            int fromEntityElement,
                                            List<InstanceStatus> limitResultsByStatus,
                                            SearchClassifications matchClassifications,
                                            Date asOfTime,
                                            String sequencingProperty,
                                            SequencingOrder sequencingOrder,
                                            int pageSize,
                                            String userId) {
        super(xtdb,
                entityTypeGUID,
                fromEntityElement,
                limitResultsByStatus,
                matchClassifications,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId);
    }

    /**
     * Public interface through which to execute the search operation, which should populate the 'xtdbResults'
     * protected member of this class.
     * @return {@code List<EntityDetail>} list of results in Egeria form
     * @throws TypeErrorException if a requested type for searching is not known to the repository
     * @throws RepositoryErrorException on any error with the read operation, either closing the datasource or timing out
     */
    public List<EntityDetail> getResults() throws TypeErrorException, RepositoryErrorException {
        if (existingDB != null) {
            try {
                Collection<List<?>> xtdbResults = runQuery(existingDB);
                log.debug(Constants.FOUND_RESULTS, xtdbResults);
                return translateResults(xtdbResults, existingDB);
            } catch (TimeoutException e) {
                throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(xtdb.getRepositoryName()),
                        this.getClass().getName(), this.getClass().getName(), e);
            }
        } else {
            List<EntityDetail> results;
            IXtdb xtdbAPI = xtdb.getXtdbAPI();
            try (IXtdbDatasource db = asOfTime == null ? xtdbAPI.openDB() : xtdbAPI.openDB(asOfTime)) {
                Collection<List<?>> xtdbResults = runQuery(db);
                log.debug(Constants.FOUND_RESULTS, xtdbResults);
                results = translateResults(xtdbResults, db);
            } catch (IOException e) {
                throw new RepositoryErrorException(XtdbOMRSErrorCode.CANNOT_CLOSE_RESOURCE.getMessageDefinition(),
                        this.getClass().getName(), this.getClass().getName(), e);
            } catch (TimeoutException e) {
                throw new RepositoryTimeoutException(XtdbOMRSErrorCode.QUERY_TIMEOUT.getMessageDefinition(""),
                        this.getClass().getName(), this.getClass().getName(), e);
            }
            return results;
        }
    }

    /**
     * Translate the results of the search operation into a list of full EntityDetail results.
     * @param xtdbResults collection of XT unique IDs to translate to full Egeria representations
     * @param db opened datasource from which to retrieve the full Egeria representations
     * @return {@code List<EntityDetail>} list of Egeria representation of the results
     */
    private List<EntityDetail> translateResults(Collection<List<?>> xtdbResults,
                                                IXtdbDatasource db) {
        final String methodName = "translateResults";
        List<EntityDetail> results = null;
        if (xtdbResults != null) {
            results = new ArrayList<>();
            for (List<?> xtdbResult : xtdbResults) {
                String docRef = (String) xtdbResult.get(0);
                XtdbDocument xtdbDoc = getXtdbObjectByReference(db, docRef);
                if (xtdbDoc == null) {
                    xtdb.logProblem(this.getClass().getName(),
                            methodName,
                            XtdbOMRSAuditCode.MAPPING_FAILURE,
                            null,
                            "entity",
                            docRef,
                            "cannot be retrieved from XTDB");
                } else {
                    EntityDetailMapping edm = new EntityDetailMapping(xtdb, xtdbDoc);
                    EntityDetail ed = edm.toEgeria();
                    if (ed != null) {
                        results.add(ed);
                    } else {
                        xtdb.logProblem(this.getClass().getName(),
                                methodName,
                                XtdbOMRSAuditCode.MAPPING_FAILURE,
                                null,
                                "entity",
                                docRef,
                                "cannot be mapped to EntityDetail");
                    }
                }
            }
        }
        return results;
    }

}
