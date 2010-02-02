#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

CONFIG=./conf/benchmark.xml
SLAVE_COUNT_ARG=""
TAILF=false

help_and_exit() {
  echo "Usage: "
  echo '  $ master.sh [-c CONFIG] [-s SLAVE_COUNT]'
  echo ""
  echo "   -c       Path to the framework configuration XML file. Optional - if not supplied benchmark will load ./conf/benchmark.xml"
  echo ""
  echo "   -s       Number of slaves.  Defaults to maxSize attribute in framework configuration XML file."
  echo ""
  echo "   -t       After starting the bechmark it will run 'tail -f' on the server's log file. By default this is set to ${TUILF}"
  echo ""
  echo "   -h       Displays this help screen"
  echo ""

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
   "-t")
     TAILF=true
     ;;
    "-h")
      help_and_exit
      ;;
    *)
      echo "Warning: unknown argument ${1}" 
      help_and_exit
      ;;
  esac
  shift
done

add_fwk_to_classpath
set_env
D_VARS="-Djava.net.preferIPv4Stack=true"
java ${JVM_OPTS} -classpath $CP ${D_VARS} $SLAVE_COUNT_ARG -Dbind.address=${BIND_ADDRESS} org.cachebench.LaunchMaster -config ${CONFIG} > stdout_master.out 2>&1 &
export CBF_MASTER_PID=$!
HOST_NAME=`hostname`
echo "Master's PID is $CBF_MASTER_PID running on ${HOST_NAME}"
if [ $TAILF == "true" ]
then
  tail -f cachebench.log 
fi  
