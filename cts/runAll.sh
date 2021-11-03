#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0
# Copyright Contributors to the Egeria project.

scales=("p0005-02" "p0010-05" "p0020-10" "p0040-10" "p0080-10" "p0160-10" "p0320-10" "p0640-10" "p1280-10")
parallelism=2
throttle=120

index=0
running=0

echo "Starting CTS..."
screen -S "pts_cts" -d -m ./runAndCollect.sh cts5 egeria/egeria-cts
running=$((running+1))
echo " ... throttling (delaying) for $throttle seconds"
sleep $throttle

while [ $index -lt ${#scales[@]} ]; do
  next_scale=${scales[$index]}
  echo "Starting PTS ($index) for scale: $next_scale ..."
  screen -S "pts_$next_scale" -d -m ./runAndCollect.sh "$next_scale" egeria/egeria-pts
  index=$((index+1))
  running=$((running+1))
  echo " ... throttling (delaying) for $throttle seconds"
  sleep $throttle
  while [ $running -ge $parallelism ]; do
    echo " ... maximum parallelism ($parallelism) reached, continuing to throttle"
    sleep $throttle
    running=$(screen -ls | grep -c "pts_" || true)
  done
done
