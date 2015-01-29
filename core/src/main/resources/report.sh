#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then
  DIRNAME=`dirname $0`
  RADARGUN_HOME=`cd $DIRNAME/..; pwd`
fi;
export RADARGUN_HOME
. `dirname $0`/includes.sh

CONFIG=""
SERIALIZED_DIR=""
DEBUG=""
REPORTER_PATHS=""

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ report.sh -c CONFIG -s SERIALIZED_DIR [--add-reporter reporter ...]'
  wrappedecho ""
  wrappedecho "   -c              Configuration file (benchmark.xml) - only the reporters section will be used."
  wrappedecho ""
  wrappedecho "   -s              Directory with the serialized data."
  wrappedecho ""
  wrappedecho "   -d              Debug master on given port."
  wrappedecho ""
  wrappedecho "   --add-reporter  Path to custom reporter directory. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   -h              Displays this help screen"
  wrappedecho ""

  exit 0
}


### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-c")
      CONFIG=$2;
      shift
      ;;
    "-d")
      DEBUG=$2
      shift
      ;;
    "-s")
      SERIALIZED_DIR=$2;
      shift
      ;;
    "-h")
      help_and_exit
      ;;
    "--add-reporter")
      REPORTER_PATHS="${REPORTER_PATHS} ${2}"
      shift
      ;;
    "-J")
      JVM_OPTS="${JVM_OPTS} ${2}"
      shift
      ;;
    *)
      wrappedecho "Warning: unknown argument ${1}" 
      help_and_exit
      ;;
  esac
  shift
done

add_fwk_to_classpath
set_env
CP="$CP:$RADARGUN_HOME/reporters/reporter-default/*"

if [ "x${DEBUG}" != "x" ]; then
  JVM_OPTS="${JVM_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DEBUG}"
fi

RUN_CMD="${JAVA} ${JVM_OPTS} -classpath $CP org.radargun.reporting.serialized.SerializedReporter $CONFIG $SERIALIZED_DIR ${REPORTER_PATHS}"
echo ${RUN_CMD}
${RUN_CMD}
