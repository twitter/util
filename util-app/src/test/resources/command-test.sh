#!/usr/bin/env bash
#
# Simple script used for testing Command
#
REPS=$1
TIME=$2
EXITCODE=$3

for rep in $(seq 1 "$REPS"); do
  echo "Stdout # $rep"
  >&2 echo "Stderr # $rep"
  sleep "$TIME"
done

exit "$EXITCODE"
