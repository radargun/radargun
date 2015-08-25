#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then
  DIRNAME=`dirname $0`
  RADARGUN_HOME=`cd $DIRNAME/..; pwd`
fi;
export RADARGUN_HOME
. `dirname $0`/includes.sh

CONFIG_FILE=""
RESULT_DIRS=""
REPORTER_DIRS=""
DEBUG=""

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ report.sh -c path_to_config_file [--add-result-dir path_to_result_dir]+ [--add-reporter-dir path_to_reporter_dir ]*'
  wrappedecho ""
  wrappedecho "   -c                  Configuration file (benchmark.xml) - only the reporters section will be used."
  wrappedecho ""
  wrappedecho "   -d                  Debug master on given port."
  wrappedecho ""
  wrappedecho "   --add-result-dir    Path to Directory with serialized data. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   --add-reporter-dir  Path to custom reporter directory. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   -h                  Displays this help screen"
  wrappedecho ""

  exit 0
}


### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-c")
      CONFIG_FILE=$2;
      shift
      ;;
    "-d")
      DEBUG=$2
      shift
      ;;
    "--add-result-dir")
      RESULT_DIRS="--add-result-dir=${2} ${RESULT_DIRS}";
      shift
      ;;
    "--add-reporter-dir")
      REPORTER_DIRS="--add-reporter-dir=${2} ${REPORTER_DIRS}"
      shift
      ;;
    "-J")
      JVM_OPTS="${JVM_OPTS} ${2}"
      shift
      ;;
    "-h")
      help_and_exit
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

if [ "x${RESULT_DIRS}" == "x" ]; then
  wrappedecho "Result dirs have not been specified"
  help_and_exit
fi

RUN_CMD="${JAVA} ${JVM_OPTS} -classpath $CP org.radargun.reporting.serialized.SerializedReporter $CONFIG_FILE ${RESULT_DIRS} ${REPORTER_DIRS}"
echo ${RUN_CMD}
${RUN_CMD}
