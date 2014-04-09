#!/bin/sh

findpids() {
   PIDS=""
   for pid in `ls /proc`; do
      grep /proc/$pid/environ -e "RADARGUN_TAG=$1" > /dev/null 2> /dev/null;
      if [ $? -eq 0 ]; then
         PIDS="$PIDS $pid";
      fi;
   done;
}

killtree() {
    local _pid=$1
    local _sig=$2
    kill -STOP ${_pid}
    for _child in $(ps -o pid --no-headers --ppid ${_pid}); do
        killtree ${_child} ${_sig}
    done
    kill -${_sig} ${_pid}
    kill -CONT ${_pid}
}