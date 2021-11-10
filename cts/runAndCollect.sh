#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

scale=$1
chart_name=$2

echo "Kicking off the $chart_name chart ($scale)..."
helm install -f ${scale}.yaml "$scale" $chart_name

echo "Capturing the names of the pods of interest..."
pod_report=$(kubectl get pod -l job-name=${scale}-report --no-headers -o name)
echo " ... report pod: $pod_report"

echo "Monitoring the reporting pod until we see completion..."
report_status="Starting"
while [[ "$report_status" != "Running" ]]; do
  echo " ... found status $report_status -- waiting ..."
  sleep 120
  report_status=$(kubectl get pod -l job-name=${scale}-report --no-headers -o custom-columns=":status.phase")
done

echo "Detailing runtime and reports into ./$scale..."
mkdir -p ./"$scale"
cd ./"$scale"
kubectl exec "$pod_report" -- sh -c 'cat /export/pipe' | tar -xvf -
kubectl describe pod "$scale" > ./deployment
kubectl get configmap "$scale"-env -o yaml > ./configmap.yaml

echo "Deleting completed helm chart..."
helm delete "$scale"
exit
