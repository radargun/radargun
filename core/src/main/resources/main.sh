#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then
  DIRNAME=`dirname $0`
  RADARGUN_HOME=`cd $DIRNAME/..; pwd`
fi;
export RADARGUN_HOME
. `dirname $0`/includes.sh

CONFIG=${RADARGUN_HOME}/conf/benchmark-dist.xml
WORKER_COUNT_ARG=""
TAILF=false
RADARGUN_MAIN_PID=""
DEBUG=""
DEBUG_SUSPEND="n"
PLUGIN_PATHS=""
PLUGIN_CONFIGS=""
REPORTER_PATHS=""
WAIT=false
OUT_FILE=stdout_main.out

main_pid() {
   RADARGUN_MAIN_PID=`ps -ef | grep "org.radargun.LaunchMain" | grep -v "grep" | awk '{print $2}'`
   return 
}

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ main.sh [-c CONFIG] [-s WORKER_COUNT] [-d [host:]port] [-J "-Dopt1 -Dopt2"] [-status] [-stop]'
  wrappedecho ""
  wrappedecho "   -c              Path to the framework configuration XML file. Optional - if not supplied benchmark will load ./conf/benchmark-dist.xml"
  wrappedecho ""
  wrappedecho "   -s              Number of workers.  Defaults to maxSize attribute in framework configuration XML file."
  wrappedecho ""
  wrappedecho "   -t              After starting the benchmark it will run 'tail -f' on the main node's log file."
  wrappedecho ""
  wrappedecho "   -o, --out-file  File where stdout and stderr should be redirected to."
  wrappedecho ""
  wrappedecho "   -m              MAIN host[:port]. An optional override to override the host/port defaults that the main listens on."
  wrappedecho ""
  wrappedecho "   -d              Debug main on given port."
  wrappedecho ""
  wrappedecho "   --debug-suspend Wait for the debugger to connect."
  wrappedecho ""
  wrappedecho "   -J              Add custom Java options."
  wrappedecho ""
  wrappedecho "   -status         Prints infromation on main's status: running or not."
  wrappedecho ""
  wrappedecho "   -stop           Forces the main to stop running."
  wrappedecho ""
  wrappedecho "   -w, --wait      Waits until the process finishes and passes the return value."
  wrappedecho ""
  wrappedecho "   --add-plugin    Path to custom plugin directory. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   --add-config    Path to config file for specified plugin. Specified as pluginName:/path/config.xml. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   --add-reporter  Path to custom reporter directory. Can be specified multiple times."
  wrappedecho ""
  wrappedecho "   -h              Displays this help screen"
  wrappedecho ""

  exit 0
}


### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-status")
      main_pid;
      if [ -z "${RADARGUN_MAIN_PID}" ]
      then
        echo "Main not running." 
      else
        echo "Main is running, pid is ${RADARGUN_MAIN_PID}."
      fi 
      exit 0
      ;;
     "-stop")
      main_pid;
      if [ -z "${RADARGUN_MAIN_PID}" ]
      then
        echo "Main not running." 
      else
        kill -15 ${RADARGUN_MAIN_PID}
        if [ $? ]
        then 
          echo "Successfully stopped main (pid=${RADARGUN_MAIN_PID})"
        else 
          echo "Problems stopping main(pid=${RADARGUN_MAIN_PID})";
        fi  
      fi 
      exit 0
      ;;
     "-s")
      WORKER_COUNT_ARG="-Dworkers=$2 "
      shift
      ;;
    "-c")
      CONFIG=$2
      shift
      ;;
    "-m")
      MAIN=$2
      shift
      ;;
    "-t")
      TAILF=true
      ;;
    "-d")
      DEBUG=$2
      shift
      ;;
    "--debug-suspend")
      DEBUG_SUSPEND="y"
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
    "--add-reporter")
      REPORTER_PATHS="--add-reporter=${2} ${REPORTER_PATHS}"
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
      wrappedecho "Warning: unknown argument ${1}" 
      help_and_exit
      ;;
  esac
  shift
done

welcome "This script is used to launch the main process, which coordinates tests run on workers."

add_fwk_to_classpath
set_env

D_VARS="-Djava.net.preferIPv4Stack=true"

if [ "x${MAIN}" != "x" ]; then
  get_port ${MAIN}
  get_host ${MAIN}
  D_VARS="${D_VARS} -Dmain.address=${HOST}"
  if [ "x${PORT}" != "x" ]; then
    D_VARS="${D_VARS} -Dmain.port=${PORT}"
  fi
fi

if [ "x${DEBUG}" != "x" ]; then
  JVM_OPTS="${JVM_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND},address=${DEBUG}"
fi

RUN_CMD="${JAVA} ${JVM_OPTS} -classpath $CP ${D_VARS} $WORKER_COUNT_ARG org.radargun.LaunchMain --config ${CONFIG} ${PLUGIN_PATHS} ${PLUGIN_CONFIGS} ${REPORTER_PATHS}"
if [ -z $OUT_FILE ]; then
  echo ${RUN_CMD}
  ${RUN_CMD} &
else
  echo ${RUN_CMD} > ${OUT_FILE}
  ${RUN_CMD} >> ${OUT_FILE} 2>&1 &
fi
export RADARGUN_MAIN_PID=$!
HOST_NAME=`hostname`
echo "Main's PID is $RADARGUN_MAIN_PID running on ${HOST_NAME}"
echo $RADARGUN_MAIN_PID > main.pid
if [ $TAILF == "true" ]
then
  touch ${OUT_FILE}
  tail_log ${OUT_FILE} "Main process is being shutdown|All reporters have been executed, exiting|Main process: unexpected shutdown\!" org.radargun.LaunchMain
fi

if [ $WAIT == "true" ]
then
  wait $RADARGUN_MAIN_PID
  EXIT_VALUE=$?
  echo "Main $RADARGUN_MAIN_PID finished with value $EXIT_VALUE"
  exit $EXIT_VALUE
fi
