#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then DIRNAME=`dirname $0`; RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
. ${RADARGUN_HOME}/bin/includes.sh

CONFIG=./conf/benchmark-dist.xml
SLAVE_COUNT_ARG=""
TAILF=false
RADARGUN_MASTER_PID=""
DEBUG=""
PLUGIN_PATHS=""
PLUGIN_CONFIGS=""
REPORTER_PATHS=""

master_pid() {
   RADARGUN_MASTER_PID=`ps -ef | grep "org.radargun.LaunchMaster" | grep -v "grep" | awk '{print $2}'`
   return 
}

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ master.sh [-c CONFIG] [-s SLAVE_COUNT] [-d [host:]port] [-status] [-stop]'
  wrappedecho ""
  wrappedecho "   -c              Path to the framework configuration XML file. Optional - if not supplied benchmark will load ./conf/benchmark-dist.xml"
  wrappedecho ""
  wrappedecho "   -s              Number of slaves.  Defaults to maxSize attribute in framework configuration XML file."
  wrappedecho ""
  wrappedecho "   -t              After starting the benchmark it will run 'tail -f' on the master node's log file."
  wrappedecho ""
  wrappedecho "   -m              MASTER host[:port]. An optional override to override the host/port defaults that the master listens on."
  wrappedecho ""
  wrappedecho "   -d              Debug master on given port."
  wrappedecho ""
  wrappedecho "   -status         Prints infromation on master's status: running or not."
  wrappedecho ""
  wrappedecho "   -stop           Forces the master to stop running."
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
      master_pid;
      if [ -z "${RADARGUN_MASTER_PID}" ]
      then
        echo "Master not running." 
      else
        echo "Master is running, pid is ${RADARGUN_MASTER_PID}."
      fi 
      exit 0
      ;;
     "-stop")
      master_pid;
      if [ -z "${RADARGUN_MASTER_PID}" ]
      then
        echo "Master not running." 
      else
        kill -15 ${RADARGUN_MASTER_PID}
        if [ $? ]
        then 
          echo "Successfully stopped master (pid=${RADARGUN_MASTER_PID})"
        else 
          echo "Problems stopping master(pid=${RADARGUN_MASTER_PID})";
        fi  
      fi 
      exit 0
      ;;
     "-s")
      SLAVE_COUNT_ARG="-Dslaves=$2 "
      shift
      ;;
    "-c")
      CONFIG=$2
      shift
      ;;
    "-m")
      MASTER=$2
      shift
      ;;
    "-t")
      TAILF=true
      ;;
    "-d")
      DEBUG=$2
      shift
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
    *)
      wrappedecho "Warning: unknown argument ${1}" 
      help_and_exit
      ;;
  esac
  shift
done

welcome "This script is used to launch the master process, which coordinates tests run on slaves."

add_fwk_to_classpath
set_env

D_VARS="-Djava.net.preferIPv4Stack=true"

if [ "x${MASTER}" != "x" ]; then
  get_port ${MASTER}
  get_host ${MASTER}
  D_VARS="${D_VARS} -Dmaster.address=${HOST}"
  if [ "x${PORT}" != "x" ]; then
    D_VARS="${D_VARS} -Dmaster.port=${PORT}"
  fi
fi

if [ "x${DEBUG}" != "x" ]; then
  JVM_OPTS="${JVM_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DEBUG}"
fi

${JAVA} ${JVM_OPTS} -classpath $CP ${D_VARS} $SLAVE_COUNT_ARG org.radargun.LaunchMaster --config ${CONFIG} ${PLUGIN_PATHS} ${PLUGIN_CONFIGS} ${REPORTER_PATHS} > stdout_master.out 2>&1 &
export RADARGUN_MASTER_PID=$!
HOST_NAME=`hostname`
echo "Master's PID is $RADARGUN_MASTER_PID running on ${HOST_NAME}"
if [ $TAILF == "true" ]
then
  touch radargun.log
  tail -f radargun.log --pid $RADARGUN_MASTER_PID
fi  
