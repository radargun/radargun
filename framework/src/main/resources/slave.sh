#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh


MASTER_HOST=""
MASTER_PORT=""
LOG4J_PREFIX=`hostname`


default_master() {
  MASTER_HOST=`sed -n -e '/bindAddress/{
                           s/.*bindAddress="${//
                           s/}".*//
                           s/:.*//
                           p
                           }' ${CBF_HOME}/conf/benchmark.xml`
  MASTER_PORT=`sed -n -e '/port="/{
                           s/.*port="${//
                           s/}".*//
                           s/:.*//
                           p
                           }' ${CBF_HOME}/conf/benchmark.xml`
}

default_master
MASTER=${MASTER_HOST}:${MASTER_PORT}

help_and_exit() {
  echo "Usage: "
  echo '  $ slave.sh [-m host:port] [-p log4j_file_prefix]'
  echo ""
  echo "   -m        Master host and port. Optional, defaults to ${MASTER}. (this value is taken from ./conf/benchmark.xml)."
  echo ""
  echo "   -p        Prefix to be appended to the generated log4j file (useful when running multiple nodes on the same machine). Optional."
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
    "-p")
      LOG4J_PREFIX=$2
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

add_fwk_to_classpath
set_env

D_VARS="-Djava.net.preferIPv4Stack=true -Dlog4j.file.prefix=${LOG4J_PREFIX} -Dbind.address=${BIND_ADDRESS}"
HOST_NAME=`hostname`
nohup java ${JVM_OPTS} ${D_VARS} -classpath $CP org.cachebench.Slave ${CONF} > stdout_slave_${HOST_NAME}.out 2>&1 &
echo "... done! Slave process started on host ${HOST_NAME}!"
echo ""


