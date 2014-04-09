#!/bin/sh
source $(dirname $0)/unix-func.sh
findpids $1
[ "x$PIDS" != "x" ]
exit $?