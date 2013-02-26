#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then DIRNAME=`dirname $0`; RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
. ${RADARGUN_HOME}/bin/includes.sh

set_env

#### parse plugins we want to test
SSH_USER=$USER
WORKING_DIR=`pwd`
CONFIG=$WORKING_DIR/conf/benchmark.xml
VERBOSE=false
REMOTE_CMD='ssh -q -o "StrictHostKeyChecking false"'
MASTER=`hostname`
SLAVES=""
SLAVE_COUNT=0
TAILF=false

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ benchmark.sh [-c config_file] [-u ssh_user] [-w WORKING DIRECTORY] [-m MASTER_IP[:PORT]] SLAVE...'
  wrappedecho ""
  wrappedecho "e.g."
  wrappedecho "  $ benchmark.sh node1 node2 node3 node4"
  wrappedecho "  $ benchmark.sh node{1..4}"
  wrappedecho ""
  wrappedecho "   -c       Configuration file. Defaults to '$CONFIG'."
  wrappedecho ""
  wrappedecho "   -u       SSH user to use when SSH'ing across to the slaves.  Defaults to '$SSH_USER'."
  wrappedecho ""
  wrappedecho "   -w       Working directory on the slave.  Defaults to '$WORKING_DIR'."
  wrappedecho ""
  wrappedecho "   -m       Connection to MASTER server.  Specified as host or host:port.  Defaults to '$MASTER'."
  wrappedecho ""
  wrappedecho "   -r       Command for remote command execution.  Defaults to '$REMOTE_CMD'."
  wrappedecho ""
  wrappedecho "   -t       After starting the benchmark it will run 'tail -f' on the master node's log file."
  wrappedecho ""
  wrappedecho "   -h       Displays this help screen"
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
      MASTER=$2
      shift
      ;;
    "-x")
      SLAVE_COUNT=1
      ;;
    "-t")
      TAILF=true
      ;;      
    "-h")
      help_and_exit
      ;;
    *)
      if [ ${1:0:1} = "-" ] ; then
        echo "Warning: unknown argument ${1}" 
        help_and_exit
      fi
      SLAVES=$@
      SLAVE_COUNT=$(($SLAVE_COUNT + $#))
      shift $#
      ;;
  esac
  shift
done

### Make sure the vars are properly set
if [ -z "$SLAVES" ] ; then
  echo "FATAL: No slave nodes specified!"
  help_and_exit
fi


####### first start the master
. ${RADARGUN_HOME}/bin/master.sh -s ${SLAVE_COUNT} -m ${MASTER} -c ${CONFIG}
PID_OF_MASTER_PROCESS=$RADARGUN_MASTER_PID
#### Sleep for a few seconds so master can open its port

####### then start the rest of the nodes

for slave in $SLAVES; do
  CMD="source ~/.bash_profile ; cd $WORKING_DIR"
  CMD="$CMD ; bin/slave.sh -m ${MASTER} -n $slave"

  # The slave_SLAVE_ADDRESS variable may be defined in environment.sh
  eval SLAVE_ADDRESS=\$${slave}_SLAVE_ADDRESS
  if [ -z $SLAVE_ADDRESS ] ; then
    SLAVE_ADDRESS=$slave
  fi

  TOEXEC="$REMOTE_CMD -l $SSH_USER $SLAVE_ADDRESS '$CMD'"
  echo "$TOEXEC"
  eval $TOEXEC
done

if [ $TAILF == "true" ]
then
  tail -f radargun.log
fi

