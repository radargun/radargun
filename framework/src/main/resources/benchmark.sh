#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh


#### parse plugins we want to test
SSH_USER=$USER
WORKING_DIR=`pwd`
VERBOSE=false
REMOTE_CMD='ssh -q -o "StrictHostKeyChecking false"'
MASTER=`hostname`
SLAVES=""
SLAVE_COUNT=0
TAILF=false

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ benchmark.sh [-u ssh_user] [-w WORKING DIRECTORY] [-m MASTER_IP[:PORT]] SLAVE...'
  wrappedecho ""
  wrappedecho "e.g."
  wrappedecho "  $ benchmark.sh node1 node2 node3 node4"
  wrappedecho "  $ benchmark.sh node{1..4}"
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
      SLAVE_COUNT=$#
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
. ${CBF_HOME}/bin/master.sh -s ${SLAVE_COUNT} -m ${MASTER}
PID_OF_MASTER_PROCESS=$CBF_MASTER_PID


####### then start the rest of the nodes
CMD="source ~/.bash_profile ; cd $WORKING_DIR"
CMD="$CMD ; bin/slave.sh -m ${MASTER}"

for slave in $SLAVES; do
  TOEXEC="$REMOTE_CMD -l $SSH_USER $slave '$CMD'"
  echo "$TOEXEC"
  eval $TOEXEC
done

if [ $TAILF == "true" ]
then
  tail -f cachebench.log
fi

