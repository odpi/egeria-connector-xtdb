#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

echo "Starting the Performance Test Suite..."

curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/server-url-root?url=${PTS_ENDPOINT}
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/server-type?typeName=PerformanceTestSuite
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/event-bus?topicURLRoot=egeria --data '{"producer": {"bootstrap.servers": "'"${KAFKA_ENDPOINT}"'"}, "consumer": {"bootstrap.servers": "'"${KAFKA_ENDPOINT}"'"} }'
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/cohorts/${EGERIA_COHORT}
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST --header "Content-Type: application/json" ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/conformance-suite-workbenches/repository-workbench/performance --data '{"class":"RepositoryPerformanceWorkbenchConfig","tutRepositoryServerName":"'"${XTDB_SERVER}"'","instancesPerType":5,"maxSearchResults":2,"waitBetweenScenarios":'"${WAIT_BETWEEN_SCENARIOS}"',"profilesToSkip":["Graph queries","Graph history queries"]}'
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X POST ${PTS_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/instance

echo ""
