#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

echo "Defining the common configuration via the PTS endpoint: ${PTS_ENDPOINT}..."

curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/server-url-root?url=${CRUX_PUBLIC_ENDPOINT}
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/server-type?typeName=Crux
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/organization-name?name=ODPi
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/event-bus?topicURLRoot=egeria --data '{"producer": {"bootstrap.servers": "'"${KAFKA_ENDPOINT}"'"}, "consumer": {"bootstrap.servers": "'"${KAFKA_ENDPOINT}"'"} }'
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/local-repository/mode/plugin-repository/connection --data '{"class":"Connection","connectorType":{"class":"ConnectorType","connectorProviderClassName":"org.odpi.egeria.connectors.juxt.crux.repositoryconnector.CruxOMRSRepositoryConnectorProvider"},"configurationProperties":{"cruxConfig":{"crux/index-store":{"kv-store":{"crux/module":"crux.rocksdb/->kv-store","db-dir":"data/servers/crux/rdb-index"}},"kafka-config":{"crux/module":"crux.kafka/->kafka-config","bootstrap-servers":"'"${KAFKA_ENDPOINT}"'"},"crux/document-store":{"crux/module":"crux.kafka/->document-store","kafka-config":"kafka-config","doc-topic-opts":{"topic-name":"crux-doc-store"},"poll-wait-duration":"PT0.050S"},"crux/tx-log":{"crux/module":"crux.kafka/->tx-log","kafka-config":"kafka-config","tx-topic-opts":{"topic-name":"crux-tx-log"},"poll-wait-duration":"PT0.050S"},"egeria.crux.lucene/lucene-store":{"db-dir":"data/servers/crux/lucene"}},"syncIndex":true}}'
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/cohorts/${EGERIA_COHORT}

echo "Retrieving common configuration from PTS endpoint: ${PTS_ENDPOINT}..."

curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/configuration -o /tmp/configresponse.json
jq -c '.omagserverConfig' /tmp/configresponse.json > /tmp/serverconfig.json

echo "Deploying common configuration to all connector pods..."

REPLICA=0
while [ $REPLICA -lt $NUM_REPLICAS ]; do
  ENDPOINT="https://${CRUX_RELEASE_NAME}-crux-${REPLICA}.${INTERNAL_SVC_NAME}:9443"
  echo " ... deploying configuration to: ${ENDPOINT}"
  curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/configuration --data @/tmp/serverconfig.json
  echo " ... ${ENDPOINT} configuration is now as follows:"
  curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${CRUX_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/configuration
  REPLICA=$(($REPLICA+1))
done

echo "Starting the server within each pod..."

REPLICA=0
while [ $REPLICA -lt $NUM_REPLICAS ]; do
  ENDPOINT="https://${CRUX_RELEASE_NAME}-crux-${REPLICA}.${INTERNAL_SVC_NAME}:9443"
  echo " ... starting server within: ${ENDPOINT}"
  curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --max-time 900 ${ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/instance
  REPLICA=$(($REPLICA+1))
done

echo ""