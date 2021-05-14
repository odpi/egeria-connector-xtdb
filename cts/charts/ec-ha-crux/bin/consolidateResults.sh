#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

echo "Performance Test Suite is complete -- retrieving all results..."

curl -f -k --silent --basic admin:admin -X GET --max-time 60 ${CRUX_BOOTSTRAP_ENDPOINT}/servers/pts/open-metadata/conformance-suite/users/${EGERIA_USER}/report/summary > /tmp/openmetadata_cts_summary.json
TEST_CASES=$(curl -f -k --silent --basic admin:admin -X GET --max-time 60 ${CRUX_BOOTSTRAP_ENDPOINT}/servers/pts/open-metadata/conformance-suite/users/${EGERIA_USER}/report/test-cases  | jq -r '.testCaseIds[]')
PROFILES=$(curl -f -k --silent --basic admin:admin -X GET --max-time 60 ${CRUX_BOOTSTRAP_ENDPOINT}/servers/pts/open-metadata/conformance-suite/users/${EGERIA_USER}/report/profiles | jq -r '.profileNames[]')

mkdir -p /tmp/profile-details
while read -r line; do
  urlencoded=$(echo ${line} | sed -e 's/ /%20/g')
  filename=$(echo ${line} | sed -e 's/ /_/g')
  echo "Retrieving profile details for: ${line}"
  curl -f -k --silent --basic admin:admin -X GET --max-time 60 ${CRUX_BOOTSTRAP_ENDPOINT}/servers/pts/open-metadata/conformance-suite/users/${EGERIA_USER}/report/profiles/${urlencoded} > /tmp/profile-details/${filename}.json
done < <(echo "${PROFILES}")

mkdir -p /tmp/test-case-details
while read -r line; do
  echo "Retrieving test case details for: ${line}"
  urlencoded=$(echo ${line} | sed -e 's/</%3C/g')
  urlencoded=$(echo ${urlencoded} | sed -e 's/>/%3E/g')
  filename=$(echo ${line} | sed -e 's/[<>]/_/g')
  curl -f -k --silent --basic admin:admin -X GET --max-time 60 ${CRUX_BOOTSTRAP_ENDPOINT}/servers/pts/open-metadata/conformance-suite/users/${EGERIA_USER}/report/test-cases/${urlencoded} > /tmp/test-case-details/${filename}.json
done < <(echo "${TEST_CASES}")

echo "Consolidating all results into a single tarball archive..."

cd /tmp
tar cvf pd.tar profile-details/*.json; gzip pd.tar
tar cvf tcd.tar test-case-details/*.json; gzip tcd.tar
tar cvf ${PTS_REPORT_NAME}.tar *.config cohort.* openmetadata_cts_summary.json pd.tar.gz tcd.tar.gz; gzip ${PTS_REPORT_NAME}.tar
echo "Complete -- PTS results available to download from /tmp/${PTS_REPORT_NAME}.tar.gz"
