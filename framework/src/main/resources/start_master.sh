#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

INIT_NODES=2
STEP_COUNT=2
NOW=`date +%y%m%d`
TEST_NAME="benchmark-${NOW}"
help_and_exit() {
  echo "Usage: "
  echo '  $ start_master.sh -[mncistx]'
  echo ""
  echo "   -m        MASTER host[:port]. Master host is required, the port is optional and defaults to 2103.  This is REQUIRED."
  echo ""
  echo "   -n        Number of slaves to launch.  This is the MAXIMUM number of slaves that will be expected.  This is REQUIRED."
  echo ""
  echo "   -c        Path to the framework configuration XML file.  This is REQUIRED."
  echo ""
  echo "   -i        Initial number of slave nodes to start with.  This defaults to ${INIT_NODES}, and is OPTIONAL."
  echo ""
  echo "   -s        The increment step.  This is how many slave nodes are added to the initial node count, until we reach the max number of nodes."
  echo "             This defaults to ${STEP_COUNT} and is OPTIONAL."
  echo ""
  echo "   -t        The name of the benchmark being run.  This is a string that is used when naming reports, etc.  Defaults to ${TEST_NAME}, is OPTIONAL"
  echo ""
  echo "   -x        This is the name of the plugin configuration file.  Must be able to be located on a classpath, or be an absolute path.  This is REQUIRED."
  echo ""
  echo "   -h        Displays this help screen"
  echo ""
  exit 0
}

welcome "This script is used to launch the master process, which coordinates tests run on slaves."

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-m")
      MASTER=$2
      shift
      ;;
    "-n")
      NUM_SLAVES=$2
      shift
      ;;
    "-c")
      CONFIG=$2
      shift
      ;;
    "-i")
      INIT_NODES=$2
      shift
      ;;      
    "-s")
      STEP_COUNT=$2
      shift
      ;;
    "-t")
      TEST_NAME=$2
      shift
      ;;
    "-x")
      PLUGIN_CONFIG=$2
      shift
      ;;
    *)
      help_and_exit
      ;;
  esac
  shift
done

### Make sure the vars are properly set
if [ -z $MASTER ] ; then
  echo "FATAL: required information (-m) missing!"
  help_and_exit
fi

if [ -z $NUM_SLAVES ] ; then
  echo "FATAL: required information (-n) missing!"
  help_and_exit
fi

if [ -z $PLUGIN_CONFIG ] ; then
  echo "FATAL: required information (-x) missing!"
  help_and_exit
fi

add_fwk_to_classpath
set_env
D_VARS="-Djava.net.preferIPv4Stack=true -Dbind.address=${BIND_ADDRESS} -Dcbf.nodes.count=${NUM_SLAVES} -Dcbf.nodes.initsize=${INIT_NODES} -Dcbf.nodes.increment=${STEP_COUNT} -Dcbf.test.name=${TEST_NAME} -Dcbf.test.config=${PLUGIN_CONFIG}"
java ${JVM_OPTS} -classpath $CP ${D_VARS} org.cachebench.Master -config ${CONFIG} > stdout_master.out 2>&1 &
export CBF_MASTER_PID=$!
echo "Master's PID is $CBF_MASTER_PID"
