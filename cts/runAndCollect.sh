#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

scale=$1
chart_name=$2

echo "Kicking off the $chart_name chart ($scale)..."
helm install -f ${scale}.yaml "$scale" $chart_name

echo "Capturing the names of the pods of interest..."
pod_report=$(kubectl get pods | grep "${scale}"-init-and-report | awk '{ print $1 }')
echo " ... report pod: $pod_report"
pod_actual=$(kubectl get pods | grep "${scale}"-platform | awk '{ print $1 }')
echo " ... actual pod: $pod_actual"

echo "Monitoring the reporting pod until we see completion..."
report_status="None"
while [[ "$report_status" != "-- End of conformance test suite results collection, download from: /tmp/$scale.tar.gz" && "$report_status" != "-- End of performance test suite results collection, download from: /tmp/$scale.tar.gz" ]]; do
  echo " ... found status $report_status -- waiting ..."
  sleep 120
  report_status=$(kubectl logs --tail=1 "${pod_report}")
done

echo "Detailing runtime and reports into ./$scale..."
mkdir -p ./"$scale"
kubectl cp "$pod_report":/tmp/"$scale".tar.gz ./"$scale"/"$scale".tar.gz
kubectl describe pod "$pod_actual" > ./"$scale"/deployment
kubectl get configmap "$scale"-env -o yaml > ./"$scale"/configmap.yaml

echo "Deleting completed helm chart..."
helm delete "$scale"
exit
