#!/bin/bash

DIRNAME=`dirname $0`

# Setup CBF_HOME
if [ "x$CBF_HOME" = "x" ]; then
    # get the full path (without any relative bits)
    CBF_HOME=`cd $DIRNAME/..; pwd`
fi
export CBF_HOME

echo ""
echo "=== Cache Benchmark Framework ==="
echo " This script is used to launch the master (controller) process."
echo ""

help_and_exit() {
  echo "Usage: "
  echo '  $ start_master.sh [-m MASTER_IP] [-p PORT] -pc PLUGIN_CONFIG_FILE -c BENCHMARK_CONFIG_FILE -n NUM_SLAVES'
  echo ""
  echo "   -m     IP address to bind to.  Defaults to 127.0.0.1."
  echo ""
  echo "   -p     Port to bind to.  Defaults to 1234"
  echo ""
  echo "   -pc    Plugin configuration file, relative to its location in plugins/<PLUGIN>/conf. E.g., repl-sync.xml. This is REQUIRED."
  echo ""
  echo "   -c     Benchmark configuration file. This is REQUIRED."
  echo ""
  echo "   -n     Number of slave nodes to wait for. This is REQUIRED."
  echo ""
  echo "   -h     Displays this help screen"
  echo ""
  exit 0
}

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

CP=${CBF_HOME}/conf
for i in ${CBF_HOME}/lib/*.jar ; do
  CP=$CP:$i
done

java -classpath $CP -Djava.net.preferIPv4Stack=true -Dcbf.numslaves=${NUM_SLAVES} -Dcbf.bind.address=${MASTER_IP} -Dcbf.bind.port=${MASTER_PORT} -Dcbf.plugin.configfile=${PLUGIN_CONF} org.cachebench.fwk.BenchmarkServer ${CONF}
