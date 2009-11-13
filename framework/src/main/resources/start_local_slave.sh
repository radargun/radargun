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
  echo "   -i        Node's index. Optional, if present will be prepended to the log file."
  echo ""
  echo "   -plugin   The plugin to be benchmarked. Shoule be a dir in ../plugins/<plugin_dir>"
  echo ""
  echo "   -ba       Bind address to be used."
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
    "-i")
      NODE_INDEX=$2
      shift
      ;;
    "-plugin")
      PLUGIN=$2
      shift
      ;;
    "-ba")
      BIND_ADDRESS=$2
      shift
      ;;
    *)
      help_and_exit
      ;;
  esac
  shift
done

DJAVA="-Djava.net.preferIPv4Stack=true"
if ! [ "$NODE_INDEXx" = "x" ] ; then
  DJAVA="$DJAVA -Dlog4j.file.prefix=$NODE_INDEX"
fi

if ! [ "x$BIND_ADDRESS" = "x" ] ; then
  DJAVA="$DJAVA -Djgroups.bind_addr=${BIND_ADDRESS}"
fi

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

CP=${CBF_HOME}/conf
for jar in ${CBF_HOME}/lib/*.jar ; do
  CP=$CP:$jar
done
for jar in ${CBF_HOME}/plugins/${PLUGIN}/lib/*.jar ; do
  CP=$CP:$jar
done
CP=$CP:${CBF_HOME}/plugins/${PLUGIN}/conf

echo "java $DJAVA -classpath $CP org.cachebench.Slave $CONF > out_slave_${NODE_INDEX}.txt 2>&1 &"
nohup java $DJAVA -classpath $CP org.cachebench.Slave $CONF > out_slave_${NODE_INDEX}.txt 2>&1 &

echo "... done! Slave process started!"
echo ""

