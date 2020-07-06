#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then DIRNAME=`dirname $0`; RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
. ${RADARGUN_HOME}/bin/includes.sh

set_env

#### parse plugins we want to test
SSH_USER=$USER
WORKING_DIR=`pwd`
CONFIG=$RADARGUN_HOME/conf/benchmark-dist.xml
VERBOSE=false
REMOTE_CMD='ssh -q -o "StrictHostKeyChecking false"'
MAIN=`hostname`
WORKERS=""
DEBUG=""
DEBUG_SUSPEND=""
WORKER_COUNT=0
TAILF=false
WAITF=false
PLUGIN_PATHS=""
PLUGIN_CONFIGS=""
REPORTER_PATHS=""
OUT_DIR=""
EXTRA_JAVA_OPTS=""
EXTRA_JAVA_OPTS_MAIN=""
EXTRA_JAVA_OPTS_WORKERS=""

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ dist.sh [-c config_file] [-u ssh_user] [-w WORKING DIRECTORY] [-m MAIN_IP[:PORT]] [-d port [--debug-suspend main|WORKER]*] WORKER...'
  wrappedecho ""
  wrappedecho "e.g."
  wrappedecho "  $ dist.sh node1 node2 node3 node4"
  wrappedecho "  $ dist.sh node{1..4}"
  wrappedecho ""
  wrappedecho "   -c              Configuration file. Defaults to '$CONFIG'."
  wrappedecho ""
  wrappedecho "   -u              SSH user to use when SSH'ing across to the workers.  Defaults to '$SSH_USER'."
  wrappedecho ""
  wrappedecho "   -w              Working directory on the worker.  Defaults to '$WORKING_DIR'."
  wrappedecho ""
  wrappedecho "   -m              Connection to MAIN server.  Specified as host or host:port.  Defaults to '$MAIN'."
  wrappedecho ""
  wrappedecho "   -r              Command for remote command execution.  Defaults to '$REMOTE_CMD'."
  wrappedecho ""
  wrappedecho "   -t              After starting the benchmark it will run 'tail -f' on the main node's log file."
  wrappedecho ""
  wrappedecho "   -d              Open debugging port on each node."
  wrappedecho ""
  wrappedecho "   --debug-suspend Wait for the debugger to connect on given worker/main."
  wrappedecho ""
  wrappedecho "   -J              Add Java options to both main and workers."
  wrappedecho ""
  wrappedecho "   -Jm             Add Java option to main."
  wrappedecho ""
  wrappedecho "   -Js             Add Java option to all workers."
  wrappedecho ""
  wrappedecho "   --add-plugin    Path to custom plugin directory. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   --add-config    Path to config file for specified plugin. Specified as pluginName:/path/config.xml. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   --add-reporter  Path to custom reporter directory. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   --wait          Wait until the main process finishes."
  wrappedecho ""
  wrappedecho "   -o              Output directory. The logs will then be main.log and worker_/hostname/.log in this directory."
  wrappedecho ""
  wrappedecho "   -h              Displays this help screen"
  wrappedecho ""
  exit 0
}

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-c")
      CONFIG=$2
      shift
      ;;
    "-u")
      SSH_USER=$2
      shift
      ;;
    "-w")
      WORKING_DIR=$2
      shift
      ;;
    "-r")
      REMOTE_CMD=$2
      shift
      ;;
    "-m")
      MAIN=$2
      shift
      ;;
    "-x")
      WORKER_COUNT=1
      ;;
    "-t")
      TAILF=true
      ;;
    "-d")
      DEBUG=$2
      shift
      ;;
    "--debug-suspend")
      DEBUG_SUSPEND="$DEBUG_SUSPEND $2"
      shift
      ;;
    "-h")
      help_and_exit
      ;;
    "--add-plugin")
      PLUGIN_PATHS="--add-plugin ${2} ${PLUGIN_PATHS}"
      shift
      ;;
    "--add-config")
      PLUGIN_CONFIGS="--add-config ${2} ${PLUGIN_CONFIGS}"
      shift
      ;;
    "--add-reporter")
      REPORTER_PATHS="--add-reporter=${2} ${REPORTER_PATHS}"
      shift
      ;;
    "-J")
      EXTRA_JAVA_OPTS="${EXTRA_JAVA_OPTS} ${2}"
      shift
      ;;
    "-Jm")
      EXTRA_JAVA_OPTS_MAIN="${EXTRA_JAVA_OPTS_MAIN} ${2}"
      shift
      ;;
    "-Js")
      EXTRA_JAVA_OPTS_WORKERS="${EXTRA_JAVA_OPTS_WORKERS} ${2}"
      shift
      ;;
    "--wait")
      WAITF="true"
      ;;
    "-o")
      OUT_DIR=$2
      shift
      ;;
    *)
      if [ ${1:0:1} = "-" ] ; then
        echo "Warning: unknown argument ${1}" 
        help_and_exit
      fi
      WORKERS=$@
      WORKER_COUNT=$(($WORKER_COUNT + $#))
      shift $#
      ;;
  esac
  shift
done

### Make sure the vars are properly set
if [ -z "$WORKERS" ] ; then
  echo "FATAL: No worker nodes specified!"
  help_and_exit
fi


####### first start the main
DEBUG_CMD=""
if [ ! -z $DEBUG ]; then
   DEBUG_CMD="-d localhost:$DEBUG"
fi
if echo $DEBUG_SUSPEND | grep -e "\bmain\b"; then
   DEBUG_CMD="${DEBUG_CMD} --debug-suspend"
fi
if [ ! -z $OUT_DIR ]; then
   OUT_CMD="-o $OUT_DIR/main.log"
fi
${RADARGUN_HOME}/bin/main.sh -s ${WORKER_COUNT} -m ${MAIN} -c ${CONFIG} ${DEBUG_CMD} ${OUT_CMD} -J "${EXTRA_JAVA_OPTS} ${EXTRA_JAVA_OPTS_MAIN}" ${REPORTER_PATHS} -w &
MAIN_SH_PID=$!
#### Sleep for a few seconds so main can open its port

####### then start the rest of the nodes

INDEX=0
for worker in $WORKERS; do
  CMD="source ~/.bash_profile ; cd $WORKING_DIR"
  CMD="$CMD ; ${RADARGUN_HOME}/bin/worker.sh -m ${MAIN} -n $worker -i $INDEX -J \"$EXTRA_JAVA_OPTS $EXTRA_JAVA_OPTS_WORKERS\" ${PLUGIN_PATHS} ${PLUGIN_CONFIGS}"
  if [ ! -z $DEBUG ]; then
     CMD="$CMD -d $worker:$DEBUG"
  fi
  if echo $DEBUG_SUSPEND | grep -e "\b${worker}\b"; then
     CMD="$CMD --debug-suspend"
  fi
  if [ ! -z $OUT_DIR ]; then
     CMD="$CMD -o $OUT_DIR/worker_${worker}.log"
  fi
  let INDEX=INDEX+1

  # The worker_WORKER_ADDRESS variable may be defined in environment.sh
  eval WORKER_ADDRESS=\$${worker}_WORKER_ADDRESS
  if [ -z $WORKER_ADDRESS ] ; then
    WORKER_ADDRESS=$worker
  fi

  TOEXEC="$REMOTE_CMD -l $SSH_USER $WORKER_ADDRESS '$CMD'"
  echo "$TOEXEC"
  eval $TOEXEC
done

if [ $TAILF == "true" ]; then
  tail -f radargun.log --pid `cat main.pid`
fi
if [ $WAITF == "true" ]; then
  wait $MAIN_SH_PID
  exit $?
fi

