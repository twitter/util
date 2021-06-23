#!/usr/bin/env bash
#
# Simple script used for testing Command
#
REPS=$1
TIME=$2
EXITCODE=$3

for rep in $(seq 1 "$REPS"); do
  echo "Stdout # $rep $EXTRA_ENV"
  >&2 echo "Stderr # $rep $EXTRA_ENV"
  sleep "$TIME"
done

exit "$EXITCODE"
