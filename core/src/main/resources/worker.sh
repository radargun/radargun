#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then
  DIRNAME=`dirname $0`
  RADARGUN_HOME=`cd $DIRNAME/..; pwd`
fi
export RADARGUN_HOME
. `dirname $0`/includes.sh


MAIN_HOST=""
MAIN_PORT=""
WORKER_INDEX=""
DEBUG=""
DEBUG_SUSPEND="n"
LOG4J_PREFIX=`hostname`-"${RG_LOG_ID:-$RANDOM}"
PLUGIN_PATHS=""
PLUGIN_CONFIGS=""
TAILF=false
WAIT=false
OUT_FILE="undef-out-file"

default_main() {
  MAIN_HOST=`sed -n -e '/bindAddress/{
                           s/.*bindAddress="${//
                           s/}".*//
                           s/.*://
                           p
                           }' ${RADARGUN_HOME}/conf/benchmark-dist.xml`
  MAIN_PORT=`sed -n -e '/port="/{
                           s/.*port="${//
                           s/}".*//
                           s/.*://
                           p
                           }' ${RADARGUN_HOME}/conf/benchmark-dist.xml`
}

default_main
MAIN=${MAIN_HOST}:${MAIN_PORT}

help_and_exit() {
  echo "Usage: "
  echo '  $ worker.sh [-m host:port] [-p log4j_file_prefix] [-i workerIndex] [-d [host:]port [--debug-suspend]] [-J "-Dopt1 -Dopt2"]'
  echo ""
  echo "   -m              Main host and port. Optional, defaults to ${MAIN}. (this value is taken from ./conf/benchmark-dist.xml)."
  echo ""
  echo "   -n              Name of this worker (used for log files and to select a worker configuration from environment.sh). Optional."
  echo ""
  echo "   -i              Index of this worker. Optional."
  echo ""
  echo "   -d              Debug address. Optional."
  echo ""
  echo "   --debug-suspend Wait for the debugger to connect. Optional."
  echo ""
  echo "   -J              Add custom Java options."
  echo ""
  echo "   -t              After starting the worker it will run 'tail -f' on the worker node's log file."
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

welcome "This script is used to launch the local worker process."

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-m")
      MAIN=$2
      shift
      ;;
    "-n")
      WORKER_NAME=$2
      shift
      ;;
    "-i")
      WORKER_INDEX=$2
      shift
      ;;
    "-d")
      DEBUG=$2
      shift
      ;;
    "--debug-suspend")
      DEBUG_SUSPEND="y"
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

CONF="--main $MAIN"
if [ "x$WORKER_INDEX" != "x" ]; then
   CONF="$CONF --workerIndex $WORKER_INDEX"
fi

add_fwk_to_classpath
set_env

if [ -n "$WORKER_NAME" ] ; then
  LOG4J_PREFIX=$WORKER_NAME

  # The worker_BIND_ADDRESS variable may be defined in environment.sh
  eval MY_BIND_ADDRESS=\$${WORKER_NAME}_BIND_ADDRESS
  if [ -n $MY_BIND_ADDRESS ] ; then
    BIND_ADDRESS=$MY_BIND_ADDRESS
  fi
  echo Using bind address $BIND_ADDRESS
fi

if [ "x$OUT_FILE" == "xundef-out-file" ]; then
  OUT_FILE=stdout_worker_${LOG4J_PREFIX}.out
fi

D_VARS="-Dmain.address=${MAIN} -Djava.net.preferIPv4Stack=true -Dlog4j.file.prefix=${LOG4J_PREFIX} "
if [ -n "$BIND_ADDRESS" ]; then
  D_VARS="$D_VARS -Dbind.address=${BIND_ADDRESS} -Djgroups.bind_addr=${BIND_ADDRESS}";
fi

if [ "x$DEBUG" != "x" ]; then
   JVM_OPTS="${JVM_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND},address=${DEBUG}"
fi
RUN_CMD="${JAVA} ${JVM_OPTS} ${D_VARS} -classpath $CP org.radargun.Worker ${CONF} ${PLUGIN_PATHS} ${PLUGIN_CONFIGS}"

if [ -z $OUT_FILE ]; then
   echo ${RUN_CMD}
   ${RUN_CMD} &
else
   echo ${RUN_CMD} > $OUT_FILE;
   echo "--------------------------------------------------------------------------------" >> $OUT_FILE
   nohup ${RUN_CMD} >> $OUT_FILE 2>&1 &
fi

export WORKER_PID=$!
echo "... done! Worker process started on host ${HOSTNAME}! Worker PID is ${WORKER_PID}"
echo ""
if [ $TAILF == "true" ]
then
  touch ${OUT_FILE}
  tail_log ${OUT_FILE} "Main shutdown\!|Worker process: unexpected shutdown\!|Communication with main failed|Unexpected error in scenario" ${LOG4J_PREFIX}
fi

if [ $WAIT == "true" ]
then
  wait $WORKER_PID
  EXIT_VALUE=$?
  if [ ! $EXIT_VALUE -eq 0 ]; then
    echo "Worker $WORKER_PID finished with value $EXIT_VALUE"
    exit $EXIT_VALUE
  fi
  wait_pid `ps -ef | grep ${LOG4J_PREFIX} | grep org.radargun.Worker | awk '{print $2}'`
fi
