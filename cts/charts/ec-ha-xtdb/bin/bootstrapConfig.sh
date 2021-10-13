#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

echo "Defining the common configuration via the PTS endpoint: ${PTS_ENDPOINT}..."

curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/server-url-root?url=${XTDB_PUBLIC_ENDPOINT}
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/server-type?typeName=XTDB
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/organization-name?name=ODPi
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/event-bus?topicURLRoot=egeria --data '{"producer": {"bootstrap.servers": "'"${KAFKA_ENDPOINT}"'"}, "consumer": {"bootstrap.servers": "'"${KAFKA_ENDPOINT}"'"} }'
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/local-repository/mode/plugin-repository/connection --data '{"class":"Connection","connectorType":{"class":"ConnectorType","connectorProviderClassName":"org.odpi.egeria.connectors.juxt.xtdb.repositoryconnector.XtdbOMRSRepositoryConnectorProvider"},"configurationProperties":{"xtdbConfigEDN":"{:xtdb/index-store {:kv-store {:xtdb/module xtdb.lmdb/->kv-store :db-dir \"data/servers/xtdb/lmdb-index\"}} :kafka-config {:xtdb/module xtdb.kafka/->kafka-config :bootstrap-servers \"'"${KAFKA_ENDPOINT}"'\"} :xtdb/tx-log {:xtdb/module xtdb.kafka/->tx-log :kafka-config :kafka-config :tx-topic-opts {:topic-name \"xtdb-tx-log\"} :poll-wait-duration \"PT0.050S\"} :xtdb.lucene/lucene-store {:db-dir \"data/servers/xtdb/lucene\" :indexer {:xtdb/module xtdb.lucene.egeria/->egeria-indexer} :analyzer {:xtdb/module xtdb.lucene.egeria/->ci-analyzer}} :xtdb/document-store {:xtdb/module xtdb.jdbc/->document-store :connection-pool {:dialect {:xtdb/module xtdb.jdbc.psql/->dialect} :pool-opts {:maximumPoolSize 10} :db-spec {:jdbcUrl \"'"${JDBC_URL}"'?user='"${username}"'&password='"${password}"'\"}}}}","syncIndex":true}}'
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/cohorts/${EGERIA_COHORT}

echo "Retrieving common configuration from PTS endpoint: ${PTS_ENDPOINT}..."

curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/configuration -o /tmp/configresponse.json
jq -c '.omagserverConfig' /tmp/configresponse.json > /tmp/serverconfig.json

echo "Deploying common configuration to all connector pods..."

REPLICA=0
while [ $REPLICA -lt $NUM_REPLICAS ]; do
  ENDPOINT="https://${XTDB_RELEASE_NAME}-xtdb-${REPLICA}.${INTERNAL_SVC_NAME}:9443"
  echo " ... deploying configuration to: ${ENDPOINT}"
  curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/configuration --data @/tmp/serverconfig.json
  echo " ... ${ENDPOINT} configuration is now as follows:"
  curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${XTDB_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/configuration
  REPLICA=$(($REPLICA+1))
done

echo "Starting the server within each pod..."

REPLICA=0
while [ $REPLICA -lt $NUM_REPLICAS ]; do
  ENDPOINT="https://${XTDB_RELEASE_NAME}-xtdb-${REPLICA}.${INTERNAL_SVC_NAME}:9443"
  echo " ... starting server within: ${ENDPOINT}"
  curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --max-time 900 ${ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${XTDB_SERVER}/instance
  REPLICA=$(($REPLICA+1))
done

echo ""
