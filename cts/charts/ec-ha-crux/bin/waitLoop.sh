#!/bin/bash

echo ""
echo "Waiting 2 minutes for PTS to start..."
sleep 120

until [ $(curl -f -k --silent --basic admin:admin -X GET ${CRUX_BOOTSTRAP_ENDPOINT}/servers/pts/open-metadata/conformance-suite/users/${EGERIA_USER}/status/workbenches/performance-workbench | jq '.workbenchStatus.workbenchComplete') == "true" ]; do
  echo "... waiting for PTS to complete"
  sleep 20
done
