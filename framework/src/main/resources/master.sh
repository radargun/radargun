#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

CONFIG=./conf/benchmark.xml

help_and_exit() {
  echo "Usage: "
  echo '  $ master.sh -[cm]'
  echo ""
  echo "   -c        Path to the framework configuration XML file. Optional - if not supplied benchmark will load ./conf/benchmark.xml"
  echo ""
  echo "   -m        MASTER host[:port]. Master host is required, the port is optional and defaults to 2103.  This is REQUIRED."
  echo ""

  exit 0
}

welcome "This script is used to launch the master process, which coordinates tests run on slaves."

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-c")
      CONFIG=$2
      shift
      ;;
    *)
      help_and_exit
      ;;
  esac
  shift
done

add_fwk_to_classpath
set_env
D_VARS="-Djava.net.preferIPv4Stack=true"
java ${JVM_OPTS} -classpath $CP ${D_VARS} -Dbind.address=${BIND_ADDRESS} org.cachebench.LaunchMaster -config ${CONFIG} > stdout_master.out 2>&1 &
export CBF_MASTER_PID=$!
echo "Master's PID is $CBF_MASTER_PID"
