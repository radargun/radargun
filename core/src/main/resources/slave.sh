#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then DIRNAME=`dirname $0`; RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
. ${RADARGUN_HOME}/bin/includes.sh


MASTER_HOST=""
MASTER_PORT=""
SLAVE_INDEX=""
DEBUG=""
LOG4J_PREFIX=`hostname`-$RANDOM


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
  echo '  $ slave.sh [-m host:port] [-p log4j_file_prefix] [-i slaveIndex] [-d [host:]port]'
  echo ""
  echo "   -m        Master host and port. Optional, defaults to ${MASTER}. (this value is taken from ./conf/benchmark-dist.xml)."
  echo ""
  echo "   -n        Name of this slave (used for log files and to select a slave configuration from environment.sh). Optional."
  echo ""
  echo "   -i        Index of this slave. Optional."
  echo ""
  echo "   -d        Debug address. Optional."
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
    "-h")
      help_and_exit
      ;;
    *)
      echo "Warn: unknown param \"${1}\"" 
      help_and_exit
      ;;
  esac
  shift
done

CONF="-master $MASTER"
if [ "x$SLAVE_INDEX" != "x" ]; then
   CONF="$CONF -slaveIndex $SLAVE_INDEX"
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

D_VARS="-Djava.net.preferIPv4Stack=true -Dlog4j.file.prefix=${LOG4J_PREFIX} -Dbind.address=${BIND_ADDRESS} -Djgroups.bind_addr=${BIND_ADDRESS}"
if [ "x$DEBUG" != "x" ]; then
   JVM_OPTS="${JVM_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DEBUG}"
fi
echo "${JAVA} ${JVM_OPTS} ${D_VARS} -classpath $CP org.radargun.Slave ${CONF}" > stdout_slave_${LOG4J_PREFIX}.out
echo "--------------------------------------------------------------------------------" >> stdout_slave_${LOG4J_PREFIX}.out
nohup ${JAVA} ${JVM_OPTS} ${D_VARS} -classpath $CP org.radargun.Slave ${CONF} >> stdout_slave_${LOG4J_PREFIX}.out 2>&1 &
echo "... done! Slave process started on host ${HOSTNAME}!"
echo ""
