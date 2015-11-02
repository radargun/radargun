#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then
  DIRNAME=`dirname $0`
  RADARGUN_HOME=`cd $DIRNAME/..; pwd`
fi
export RADARGUN_HOME
. `dirname $0`/includes.sh


MASTER_HOST=""
MASTER_PORT=""
SLAVE_INDEX=""
DEBUG=""
LOG4J_PREFIX=`hostname`-$RANDOM
PLUGIN_PATHS=""
PLUGIN_CONFIGS=""
TAILF=false
WAIT=false
OUT_FILE="undef-out-file"

default_master() {
  MASTER_HOST=`sed -n -e '/bindAddress/{
                           s/.*bindAddress="${//
                           s/}".*//
                           s/.*://
                           p
                           }' ${RADARGUN_HOME}/conf/benchmark-dist.xml`
  MASTER_PORT=`sed -n -e '/port="/{
                           s/.*port="${//
                           s/}".*//
                           s/.*://
                           p
                           }' ${RADARGUN_HOME}/conf/benchmark-dist.xml`
}

default_master
MASTER=${MASTER_HOST}:${MASTER_PORT}

help_and_exit() {
  echo "Usage: "
  echo '  $ slave.sh [-m host:port] [-p log4j_file_prefix] [-i slaveIndex] [-d [host:]port] [-J "-Dopt1 -Dopt2"]'
  echo ""
  echo "   -m              Master host and port. Optional, defaults to ${MASTER}. (this value is taken from ./conf/benchmark-dist.xml)."
  echo ""
  echo "   -n              Name of this slave (used for log files and to select a slave configuration from environment.sh). Optional."
  echo ""
  echo "   -i              Index of this slave. Optional."
  echo ""
  echo "   -d              Debug address. Optional."
  echo ""
  echo "   -J              Add custom Java options."
  echo ""
  echo "   -t              After starting the slave it will run 'tail -f' on the slave node's log file."
  echo ""
  echo "   -o, --out-file  File where stdout and stderr should be redirected to."
  echo ""
  echo "   -w, --wait      Waits until the process finishes and passes the return value."
  echo ""
  echo "   --add-plugin    Path to custom plugin directory. Can be specified multiple times."
  echo ""
  echo "   --add-config    Path to config file for specified plugin. Specified as pluginName:/path/config.xml. Can be specified multiple times."
  echo ""
  echo "   -h              Displays this help screen"
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
    "-n")
      SLAVE_NAME=$2
      shift
      ;;
    "-i")
      SLAVE_INDEX=$2
      shift
      ;;
    "-d")
      DEBUG=$2
      shift
      ;;
    "-t")
      TAILF=true
      ;;
    "-h")
      help_and_exit
      ;;
    "--add-plugin")
      PLUGIN_PATHS="--add-plugin=${2} ${PLUGIN_PATHS}"
      shift
      ;;
    "--add-config")
      PLUGIN_CONFIGS="--add-config=${2} ${PLUGIN_CONFIGS}"
      shift
      ;;
    "-w"|"--wait")
      WAIT="true"
      ;;
    "-o"|"--out-file")
      OUT_FILE=${2}
      shift
      ;;
    "-J")
      JVM_OPTS="${JVM_OPTS} ${2}"
      shift
      ;;
    *)
      echo "Warn: unknown param \"${1}\""
      help_and_exit
      ;;
  esac
  shift
done

CONF="--master $MASTER"
if [ "x$SLAVE_INDEX" != "x" ]; then
   CONF="$CONF --slaveIndex $SLAVE_INDEX"
fi

add_fwk_to_classpath
set_env

if [ -n "$SLAVE_NAME" ] ; then
  LOG4J_PREFIX=$SLAVE_NAME

  # The slave_BIND_ADDRESS variable may be defined in environment.sh
  eval MY_BIND_ADDRESS=\$${SLAVE_NAME}_BIND_ADDRESS
  if [ -n $MY_BIND_ADDRESS ] ; then
    BIND_ADDRESS=$MY_BIND_ADDRESS
  fi
  echo Using bind address $BIND_ADDRESS
fi

if [ "x$OUT_FILE" == "xundef-out-file" ]; then
  OUT_FILE=stdout_slave_${LOG4J_PREFIX}.out
fi

D_VARS="-Dmaster.address=${MASTER} -Djava.net.preferIPv4Stack=true -Dlog4j.file.prefix=${LOG4J_PREFIX} "
if [ -n "$BIND_ADDRESS" ]; then
  D_VARS="$D_VARS -Dbind.address=${BIND_ADDRESS} -Djgroups.bind_addr=${BIND_ADDRESS}";
fi

if [ "x$DEBUG" != "x" ]; then
   JVM_OPTS="${JVM_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DEBUG}"
fi
RUN_CMD="${JAVA} ${JVM_OPTS} ${D_VARS} -classpath $CP org.radargun.Slave ${CONF} ${PLUGIN_PATHS} ${PLUGIN_CONFIGS}"

if [ -z $OUT_FILE ]; then
   echo ${RUN_CMD}
   ${RUN_CMD} &
else
   echo ${RUN_CMD} > $OUT_FILE;
   echo "--------------------------------------------------------------------------------" >> $OUT_FILE
   nohup ${RUN_CMD} >> $OUT_FILE 2>&1 &
fi

export SLAVE_PID=$!
echo "... done! Slave process started on host ${HOSTNAME}! Slave PID is ${SLAVE_PID}"
echo ""
if [ $TAILF == "true" ]
then
  tail -f stdout_slave_${LOG4J_PREFIX}.out --pid $SLAVE_PID
fi

if [ $WAIT == "true" ]
then
  wait $SLAVE_PID
fi