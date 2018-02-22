#!/bin/sh
### Util functions for all of the shell scripts
#
# NOTE that this *needs* to be loaded via the following snippet:
#
#
#   if [ "x$RADARGUN_HOME" = "x" ]; then RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
#   . ${RADARGUN_HOME}/bin/includes.sh
#
# as a "side effect", the snippet also sets the RADARGUN_HOME environment variable.

command -v pgrep >/dev/null 2>&1 || { echo >&2 "This script requires pgrep, but it's not installed.  Aborting."; exit 1; }

welcome() {
  SCRIPTNAME=`basename ${0}`
  echo "=== Radargun: ${SCRIPTNAME} ==="
  wrappedecho "${1}"
  echo ""
}

wrappedecho() {
  echo "${1}" | fold -s -w 80
}

check_plugin_exists() {
  PLUGIN=${1}
  if ! [ -d ${RADARGUN_HOME}/plugins/$PLUGIN ] ; then
    echo "FATAL: unknown plugin ${PLUGIN}! Directory doesn't exist in ${RADARGUN_HOME}/plugins!"
    exit 2
  fi
}

add_to_classpath() {
  if [ -z $CP ] ; then
    CP=${1}
  else
    CP=${CP}:${1}
  fi
}

add_plugin_to_classpath() {
  PLUGIN=${1}
  check_plugin_exists $PLUGIN
  for i in ${RADARGUN_HOME}/plugins/${PLUGIN}/lib/*.jar ; do
    add_to_classpath $i
  done
  add_to_classpath ${RADARGUN_HOME}/plugins/${PLUGIN}/conf
}

add_fwk_to_classpath() {
  for i in ${RADARGUN_HOME}/lib/*.jar ; do
    add_to_classpath $i
  done
  add_to_classpath ${RADARGUN_HOME}/conf
}

set_env() {
  . `dirname $0`/environment.sh

  # If the user specified a JAVA_HOME, use that
  if [ -n "$JAVA_HOME" ]
  then
    JAVA=$JAVA_HOME/bin/java
  else
    JAVA=`which java`
  fi

  # If on cygwin, java is a regular Windows program.
  # So we have to convert the classpath to Windows paths.
  if [ "$OSTYPE" == "cygwin" ]
  then
    CP=`cygpath -wp "$CP"`
  fi
}

wait_for_process() {
  PID=$1
  echo "Waiting for process $PID to complete"
  while ! [ "`ps ax | grep $PID | grep -v grep`" = "" ] ; do 
    echo "Waiting for process completion."
    sleep 5
  done  
}

get_port() {
  HOST_PORT=$1
  if [ "x`echo $HOST_PORT | grep :`" = "x" ]
  then
    PORT=""
  else
    PORT=`echo $HOST_PORT | sed -e 's/^.*://g'`
  fi
}

get_host() {
  HOST_PORT=$1
  HOST=`echo $HOST_PORT | sed -e 's/:.*$//g' -e 's/^\s*//g'`
}

tail_log() {
   #
   # Bash log tailer
   # Param 1 - log file
   # Param 2 - String in the log file. When it is found, the tail process is killed
   # Param 3 - The string in 'ps -ef' to search for. If it doesn't exist, then exit.
   #

   tail -f ${1} | while true
   do
      # Read from stdin with a 10 min timeout
      IFS= read -r -u 0 -t 200
      if (($? == 0))
      then
         echo "${REPLY}"
         if [[ "${REPLY}" =~ ${2} ]]
         then
             kill -9 `pgrep -P $$ tail`
             break
         fi
      else
         # Kill tail if no java process spawned from the shell script is found
         if ! (ps -ef | grep "${3}" | grep -v "grep" | grep -v "tail" &>/dev/null)
         then
            kill -9 `pgrep -P $$ tail`
            break
         fi
      fi
   done
   return 0
}

wait_pid() {
    PID=$1
    if [ ! "$PID" == "" ]; then
        echo "Waiting for process $PID"
        while [ -e /proc/$PID ]
        do
            sleep 5
        done
        echo "Process $PID has finished"
    fi
}
