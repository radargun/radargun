#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

help_and_exit() {
  echo "Usage: "
  echo '  $ start_local_slave.sh -m MASTER_IP -plugin plugin_name'
  echo ""
  echo "   -m        MASTER host[:port]. Master host is required, the port is optional and defaults to 2103."
  echo ""
  echo "   -plugin   The plugin to be benchmarked. Shoule be a dir in ../plugins/<plugin_dir>"
  echo ""
  echo "   -h        Displays this help screen"
  echo ""
  exit 0
}

welcome "This script is used to launch the local slave process."

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-m")
      MASTER=$2
      shift
      ;;
    "-plugin")
      PLUGIN=$2
      shift
      ;;
    *)
      echo "Warn: unknown param ${1}" 
      help_and_exit
      ;;
  esac
  shift
done

if [ -z $MASTER ] ; then
  echo "FATAL: required information (-m) missing!"
  help_and_exit
fi

CONF="-master $MASTER"

if [ -z $PLUGIN ] ; then
  echo "FATAL: required information (-plugin) missing!"
  help_and_exit
fi

if ! [ -d ${CBF_HOME}/plugins/$PLUGIN ] ; then
  echo "FATAL: unknown plugin ${PLUGIN}! Directory doesn't exist in ${CBF_HOME}/plugins!"
  exit 2
fi

add_fwk_to_classpath
add_plugin_to_classpath ${PLUGIN}

HOST_NAME=`hostname`

set_env
nohup java ${JVM_OPTS} -classpath $CP -Dlog4j.file.prefix=${HOST_NAME} -Dbind.address=${BIND_ADDRESS} -Djava.net.preferIPv4Stack=true org.cachebench.Slave $CONF > stdout_slave_${HOST_NAME}.out 2>&1 &

echo "... done! Slave process started on host ${HOST_NAME}!"
echo ""

