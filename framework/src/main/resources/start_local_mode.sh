#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ start_local_mode.sh [-n BENCHMARK_NAME] -p PLUGIN -pc PLUGIN_CONFIG_FILE -c BENCHMARK_CONFIG_FILE'
  wrappedecho ""
  wrappedecho "   -n     String to name the benchmark.  Defaults to current date."
  wrappedecho ""
  wrappedecho "   -p     Plugin to benchmark.  Needs to exist in the plugins directory.  This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -pc    Plugin configuration file, relative to its location in plugins/<PLUGIN>/conf. E.g., repl-sync.xml. This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -c     Benchmark configuration file. This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -h     Displays this help screen"
  wrappedecho ""
  exit 0
}

welcome "This script is used to launch a LOCAL mode test."

BENCHNAME=`date +%Y%m%d_%H%M`
BENCHNAME="benchmark-${BENCHNAME}"

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-pc")
      PLUGIN_CONF=$2
      shift
      ;;
    "-c")
      CONF=$2
      shift
      ;;
    "-p")
      PLUGIN=$2
      shift
      ;;
    "-n")
      BENCHNAME=$2
      shift
      ;;
    *)    
      help_and_exit
      ;;
  esac
  shift
done

if [ -z $PLUGIN_CONF ] ; then
  echo "FATAL: required information (-pc) missing!"
  help_and_exit
fi

if [ -z $PLUGIN ] ; then
  echo "FATAL: required information (-p) missing!"
  help_and_exit
fi


if [ -z $CONF ] ; then
  echo "FATAL: required information (-c) missing!"
  help_and_exit
fi

add_fwk_to_classpath
add_plugin_to_classpath $PLUGIN
set_env

java -classpath $CP ${JVM_OPTS} -Djava.net.preferIPv4Stack=true -Dcbf.plugin.configfile=${PLUGIN_CONF} -Dcbf.benchmarkname=${BENCHNAME} org.cachebench.LocalModeRunner -c ${CONF}
