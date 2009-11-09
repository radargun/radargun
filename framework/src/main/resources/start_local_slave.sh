#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ start_local_slave.sh -m MASTER_IP:PORT -plugin plugin_name'
  wrappedecho ""
  wrappedecho "   -m     Connection to MASTER server.  IP address and port is needed.  This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -h     Displays this help screen"
  wrappedecho ""
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
      help_and_exit
      ;;
  esac
  shift
done

if [ -z $MASTER ] ; then
  echo "FATAL: required information (-m) missing!"
  help_and_exit
fi

if [ -z $PLUGIN ] ; then
  echo "FATAL: required information (-plugin) missing!"
  help_and_exit
fi

add_fwk_to_classpath
add_plugin_to_classpath $PLUGIN
set_env

nohup java -classpath $CP ${JVM_OPTS} -Djava.net.preferIPv4Stack=true -Dbind.address=${BIND_ADDRESS} -Djgroups.bind_addr=${BIND_ADDRESS} org.cachebench.fwk.BenchmarkNode -serverHost $MASTER > out_slave_`hostname`.txt 2>&1 &

echo "... done! Slave process started!"
echo ""

