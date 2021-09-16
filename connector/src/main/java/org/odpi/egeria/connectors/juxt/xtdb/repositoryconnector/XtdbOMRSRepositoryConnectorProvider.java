/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector;

import org.odpi.openmetadata.frameworks.connectors.properties.beans.ConnectorType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnectorProviderBase;

import java.util.ArrayList;
import java.util.List;

/**
 * In the Open Connector Framework (OCF), a ConnectorProvider is a factory for a specific type of connector.
 * The XtdbOMRSRepositoryConnectorProvider is the connector provider for the XtdbOMRSRepositoryConnector.
 * It extends OMRSRepositoryConnectorProviderBase which in turn extends the OCF ConnectorProviderBase.
 * ConnectorProviderBase supports the creation of connector instances.
 * <p>
 * The XtdbOMRSRepositoryConnectorProvider must initialize ConnectorProviderBase with the Java class
 * name of the OMRS Connector implementation (by calling super.setConnectorClassName(className)).
 * Then the connector provider will work.
 * <br><br>
 * The configurationProperties can contain the following keys:
 * <ul>
 *     <li><code>xtdbConfig</code>: a XTDB configuration document that follows the JSON structure documented on
 *         XTDB's site: https://xtdb.com/reference/1.19.0-beta1/configuration.html</li>
 *     <li><code>syncIndex</code>: a boolean indicating whether writes should be done synchronously (true, default) to
 *         ensure the index is up-to-date before returning, or asynchronously (false) to ensure that the transaction is
 *         recorded but that the index can be eventually consistent (faster writes)</li>
 *     <li><code>luceneRegexes</code>: a boolean indicating whether any unquoted regexes (those not surrounded by
 *         <code>\Q</code> and <code>\E</code>) should be treated as Lucene regexes (true) or not (false). Technically
 *         the search interfaces are meant to take Java regexes; however, if usage of the connector is only expected to
 *         pass fairly simple regexes that are also supported by Lucene, enabling this should significantly improve the
 *         performance of queries against text data that involves regexes that are unquoted. (Regexes that are quoted
 *         will be handled appropriately irrespective of this setting.)  Note that this will have no impact if Lucene
 *         itself is not configured.</li>
 * </ul><br>
 * For example:
 * <code>
 * {
 *   "class": "Connection",
 *   "connectorType": {
 *     "class": "ConnectorType",
 *     "connectorProviderClassName": "org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnectorProvider"
 *   },
 *   "configurationProperties": {
 *     "xtdbConfig": {
 *       "xtdb/index-store": {
 *         "kv-store": {
 *           "xtdb/module": "xtdb.rocksdb/-&gt;kv-store",
 *           "db-dir": "data/servers/xtdb/rdb-index"
 *         }
 *       },
 *       "xtdb/document-store": {
 *         "kv-store": {
 *           "xtdb/module": "xtdb.rocksdb/-&gt;kv-store",
 *           "db-dir": "data/servers/xtdb/rdb-docs"
 *         }
 *       },
 *       "xtdb/tx-log": {
 *         "xtdb/module": "xtdb.kafka/-&gt;tx-log",
 *         "kafka-config": {
 *           "bootstrap-servers": "localhost:9092"
 *         },
 *         "tx-topic-opts": {
 *           "topic-name": "xtdb-tx-log"
 *         },
 *         "poll-wait-duration": "PT1S"
 *       }
 *     },
 *     "syncIndex": false,
 *     "luceneRegexes": true
 *   }
 * }
 * </code>
 */
public class XtdbOMRSRepositoryConnectorProvider extends OMRSRepositoryConnectorProviderBase {

    static final String CONNECTOR_TYPE_GUID = "ba99618d-fd21-475b-8eba-87051aea026e";
    static final String CONNECTOR_TYPE_NAME = "OMRS XTDB Repository Connector";
    static final String CONNECTOR_TYPE_DESC = "OMRS XTDB Repository Connector that uses XTDB as a back-end historical repository for metadata.";

    public static final String XTDB_CONFIG = "xtdbConfig";
    public static final String XTDB_CONFIG_EDN = "xtdbConfigEDN";
    public static final String SYNCHRONOUS_INDEX = "syncIndex";
    public static final String LUCENE_REGEXES = "luceneRegexes";

    /**
     * Constructor used to initialize the ConnectorProviderBase with the Java class name of the specific
     * OMRS Connector implementation.
     */
    public XtdbOMRSRepositoryConnectorProvider() {

        Class<?> connectorClass = XtdbOMRSRepositoryConnector.class;
        super.setConnectorClassName(connectorClass.getName());

        ConnectorType connectorType = new ConnectorType();
        connectorType.setType(ConnectorType.getConnectorTypeType());
        connectorType.setGUID(CONNECTOR_TYPE_GUID);
        connectorType.setQualifiedName(CONNECTOR_TYPE_NAME);
        connectorType.setDisplayName(CONNECTOR_TYPE_NAME);
        connectorType.setDescription(CONNECTOR_TYPE_DESC);
        connectorType.setConnectorProviderClassName(this.getClass().getName());

        List<String> configProperties = new ArrayList<>();
        configProperties.add(XTDB_CONFIG);
        configProperties.add(XTDB_CONFIG_EDN);
        configProperties.add(SYNCHRONOUS_INDEX);
        configProperties.add(LUCENE_REGEXES);
        connectorType.setRecognizedConfigurationProperties(configProperties);

        super.connectorTypeBean = connectorType;

    }

}
