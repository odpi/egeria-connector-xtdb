#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

echo "Starting the Performance Test Suite..."

curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${CRUX_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/server-url-root?url=${CRUX_BOOTSTRAP_ENDPOINT}
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${CRUX_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/server-type?typeName=PerformanceTestSuite
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${CRUX_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/event-bus?topicURLRoot=egeria --data '{"producer": {"bootstrap.servers": "'"${KAFKA_ENDPOINT}"'"}, "consumer": {"bootstrap.servers": "'"${KAFKA_ENDPOINT}"'"} }'
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${CRUX_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/cohorts/${EGERIA_COHORT}
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${CRUX_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/conformance-suite-workbenches/repository-workbench/performance --data '{"class":"RepositoryPerformanceWorkbenchConfig","tutRepositoryServerName":"'"${CRUX_SERVER}"'","instancesPerType":10,"maxSearchResults":5,"waitBetweenScenarios":60}'
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${CRUX_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/instance

echo ""