#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ start_master.sh [-m MASTER_IP] [-p PORT] -pc PLUGIN_CONFIG_FILE -c BENCHMARK_CONFIG_FILE -n NUM_SLAVES'
  wrappedecho ""
  wrappedecho "   -m     IP address to bind to.  Defaults to 127.0.0.1."
  wrappedecho ""
  wrappedecho "   -p     Port to bind to.  Defaults to 1234"
  wrappedecho ""
  wrappedecho "   -pc    Plugin configuration file, relative to its location in plugins/<PLUGIN>/conf. E.g., repl-sync.xml. This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -c     Benchmark configuration file. This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -n     Number of slave nodes to wait for. This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -h     Displays this help screen"
  wrappedecho ""
  exit 0
}

welcome "This script is used to launch the master (controller) process."

MASTER_IP="127.0.0.1"
MASTER_PORT="1234"

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-m")
      MASTER_IP=$2
      shift
      ;;
    "-p")
      MASTER_PORT=$2
      shift
      ;;
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
set_env

java -classpath $CP ${JVM_OPTS} -Djava.net.preferIPv4Stack=true -Dcbf.numslaves=${NUM_SLAVES} -Dcbf.bind.address=${MASTER_IP} -Dcbf.bind.port=${MASTER_PORT} -Dcbf.plugin.configfile=${PLUGIN_CONF} org.cachebench.fwk.BenchmarkServer ${CONF}
