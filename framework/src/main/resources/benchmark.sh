#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh


#### parse plugins we want to test
NUM_SLAVES=""
SLAVE_PREFIX=slave
SSH_USER=$USER
WORKING_DIR=`pwd`
VERBOSE=false

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ benchmark.sh [-p SLAVE_PREFIX] [-u ssh_user] [-w WORKING DIRECTORY] -n num_slaves -m MASTER_IP:PORT '
  wrappedecho ""
  wrappedecho "   -p       Provides a prefix to all slave names entered in /etc/hosts"
  wrappedecho "            Defaults to '$SLAVE_PREFIX'"
  wrappedecho ""
  wrappedecho "   -u       SSH user to use when SSH'ing across to the slaves.  Defaults to current user."
  wrappedecho ""
  wrappedecho "   -w       Working directory on the slave.  Defaults to '$WORKING_DIR'."
  wrappedecho ""
  wrappedecho "   -n       Number of slaves.  This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -m       Connection to MASTER server.  IP address:port is needed.  This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -h       Displays this help screen"
  wrappedecho ""
  exit 0
}

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-p")
      SLAVE_PREFIX=$2
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
    "-n")
      NUM_SLAVES=$2
      shift
      ;;
    "-m")
      MASTER=$2
      shift
      ;;
    *)
      echo "Warn: unknown param ${1}" 
      help_and_exit
      ;;
  esac
  shift
done

### Make sure the vars are properly set
if [ -z $NUM_SLAVES ] ; then
  echo "FATAL: required information (-n) missing!"
  help_and_exit
fi

if [ -z $MASTER ] ; then
  echo "FATAL: required information (-m) missing!"
  help_and_exit
fi


####### first start the master
. ${CBF_HOME}/bin/master.sh
PID_OF_MASTER_PROCESS=$CBF_MASTER_PID


####### then start the rest of the nodes
CMD="source ~/.bash_profile ; cd $WORKING_DIR"
CMD="$CMD ; bin/slave.sh -m $MASTER"

loop=1
while [ $loop -le $NUM_SLAVES ]
do
  ssh -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD "
  let "loop+=1"
done
