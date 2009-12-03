#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh


#### parse plugins we want to test
MAX_NODES=8
SLAVE_PREFIX=slave
SSH_USER=$USER
WORKING_DIR=`pwd`
ASYNC=true
VERBOSE=false

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ benchmark.sh [-v] [-p SLAVE_PREFIX] [-u ssh_user] [-sync] [-w WORKING DIRECTORY] [-g GIT_URL] -plugin plugin_name -n num_slaves -m MASTER_IP:PORT '
  wrappedecho ""
  wrappedecho "   -v       Be verbose"
  wrappedecho ""
  wrappedecho "   -p       Provides a prefix to all slave names entered in /etc/hosts"
  wrappedecho "            Defaults to '$SLAVE_PREFIX'"
  wrappedecho ""
  wrappedecho "   -u       SSH user to use when SSH'ing across to the slaves.  Defaults to current user."
  wrappedecho ""
  wrappedecho "   -sync    If provided, then each SSH connection will NOT be forked as a separate process. Forks by default."
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


####### first start the master
. ${CBF_HOME}/bin/master.sh
PID_OF_MASTER_PROCESS=$CBF_MASTER_PID


####### then start the rest of the nodes
CMD="source ~/.bash_profile ; cd $WORKING_DIR"
CMD="$CMD ; bin/slave.sh -m $MASTER"

loop=1
while [ $loop -le $NUM_SLAVES ]
do
  if [ "$ASYNC" = "true" ] ; then
    ssh -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD" &
  else
    ssh -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD "
  fi
  let "loop+=1"
done
