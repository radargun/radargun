#!/bin/sh
source $(dirname $0)/unix-func.sh
#findpids $1
PIDS=$1
echo "unix-stop: PIDS=$PIDS"
for pid in $PIDS; do
   killtree $pid TERM;
done;