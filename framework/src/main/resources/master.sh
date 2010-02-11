#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

CONFIG=./conf/benchmark.xml
SLAVE_COUNT_ARG=""
TAILF=false

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ master.sh [-c CONFIG] [-s SLAVE_COUNT]'
  wrappedecho ""
  wrappedecho "   -c       Path to the framework configuration XML file. Optional - if not supplied benchmark will load ./conf/benchmark.xml"
  wrappedecho ""
  wrappedecho "   -s       Number of slaves.  Defaults to maxSize attribute in framework configuration XML file."
  wrappedecho ""
  wrappedecho "   -t       After starting the benchmark it will run 'tail -f' on the master node's log file."
  wrappedecho ""
  wrappedecho "   -m       MASTER host[:port]. An optional override to override the host/port defaults that the master listens on."
  wrappedecho ""
  wrappedecho "   -h       Displays this help screen"
  wrappedecho ""

  exit 0
}

welcome "This script is used to launch the master process, which coordinates tests run on slaves."

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
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
    "-h")
      help_and_exit
      ;;
    *)
      wrappedecho "Warning: unknown argument ${1}" 
      help_and_exit
      ;;
  esac
  shift
done

add_fwk_to_classpath
set_env

D_VARS="-Djava.net.preferIPv4Stack=true"

if ! [ "x${MASTER}" = "x" ] ; then
  get_port ${MASTER}
  get_host ${MASTER}
  D_VARS="${D_VARS} -Dmaster.address=${HOST}"
  if ! [ "x${PORT}" = "x" ] ; then
    D_VARS="${D_VARS} -Dmaster.port=${PORT}"
  fi
fi

java ${JVM_OPTS} -classpath $CP ${D_VARS} $SLAVE_COUNT_ARG org.cachebench.LaunchMaster -config ${CONFIG} > stdout_master.out 2>&1 &
export CBF_MASTER_PID=$!
HOST_NAME=`hostname`
echo "Master's PID is $CBF_MASTER_PID running on ${HOST_NAME}"
if [ $TAILF == "true" ]
then
  tail -f cachebench.log 
fi  
