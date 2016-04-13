#!/bin/sh
source $(dirname $0)/unix-func.sh
#findpids $1
PIDS=$1
for pid in $PIDS; do
   killtree $pid KILL;
done;

