#!/bin/sh

#
# PARAM 1 - The PID of the service
#

# This script requires pgrep and jps to be installed.
command -v pgrep >/dev/null 2>&1 || { echo >&2 "This script requires pgrep, but it's not installed.  Aborting."; exit 1; }
command -v jps >/dev/null 2>&1 || { echo >&2 "This script requires jps, but it's not installed.  Aborting."; exit 1; }

findjvms() {
   JVMS=""
   for pid in `jps -q`; do
      # If the service process is a Java process
      if [ "$pid" == "$1" ]; then
         JVMS="$JVMS $pid";
      else
         # If the service process is the parent of a Java process
         jvm=`pgrep -P $1` > /dev/null 2> /dev/null;
         if [ "$jvm" == "$pid" ]; then
            JVMS="$JVMS $pid";
         fi;
      fi;
   done;
}

killtree() {
   local _pid=$1
   local _sig=$2
   kill -STOP ${_pid} > /dev/null 2> /dev/null;
   for _child in $(pgrep -P ${_pid}); do
      killtree ${_child} ${_sig};
   done
   kill -${_sig} ${_pid} > /dev/null 2> /dev/null;
   kill -CONT ${_pid} > /dev/null 2> /dev/null;
}
