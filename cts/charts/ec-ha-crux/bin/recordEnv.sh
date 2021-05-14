#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

echo "Recording environment details..."

curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${CRUX_BOOTSTRAP_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/pts/configuration > /tmp/omag.server.pts.config
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${CRUX_PUBLIC_ENDPOINT}/open-metadata/admin-services/users/${EGERIA_USER}/servers/${CRUX_SERVER}/configuration > /tmp/omag.server.${CRUX_SERVER}.config
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${CRUX_BOOTSTRAP_ENDPOINT}/servers/pts/open-metadata/repository-services/users/${EGERIA_USER}/metadata-highway/local-registration > /tmp/cohort.${EGERIA_COHORT}.pts.local
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${CRUX_BOOTSTRAP_ENDPOINT}/servers/pts/open-metadata/repository-services/users/${EGERIA_USER}/metadata-highway/cohorts/${EGERIA_COHORT}/remote-members > /tmp/cohort.${EGERIA_COHORT}.pts.remote
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${CRUX_PUBLIC_ENDPOINT}/servers/${CRUX_SERVER}/open-metadata/repository-services/users/${EGERIA_USER}/metadata-highway/local-registration > /tmp/cohort.${EGERIA_COHORT}.${CRUX_SERVER}.local
curl -f -k -w "\n  (%{http_code} - %{url_effective})\n" --silent -X GET ${CRUX_PUBLIC_ENDPOINT}/servers/${CRUX_SERVER}/open-metadata/repository-services/users/${EGERIA_USER}/metadata-highway/cohorts/${EGERIA_COHORT}/remote-members > /tmp/cohort.${EGERIA_COHORT}.${CRUX_SERVER}.remote

echo ""