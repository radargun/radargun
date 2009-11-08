#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ start_local_mode.sh -pc PLUGIN_CONFIG_FILE -c BENCHMARK_CONFIG_FILE'
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

if [ -z $CONF ] ; then
  echo "FATAL: required information (-c) missing!"
  help_and_exit
fi

add_fwk_to_classpath
add_plugin_to_classpath $PLUGIN

java -classpath $CP -Djava.net.preferIPv4Stack=true -Dcbf.plugin.configfile=${PLUGIN_CONF} org.cachebench.LocalModeRunner ${CONF}
