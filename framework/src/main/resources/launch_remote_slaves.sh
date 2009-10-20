#!/bin/bash

echo ""
echo "=== Cache Benchmark Framework ==="
echo " This script is used to launch slaves on remote nodes, via SSH."
echo ""

SLAVE_PREFIX=slave
SSH_USER=$USER
CLEAN=true
WORKING_DIR=work
ASYNC=false
VERBOSE=false
SKIP_CHECKOUT=false
SKIP_BUILD=false

help_and_exit() {
  echo "Usage: "
  echo '  $ launch_slaves.sh [-v] [-p SLAVE_PREFIX] [-u ssh user] [-nc] [-async] [-sb] [-sc] [-w WORKING DIRECTORY] -n num_slaves -m MASTER_IP:PORT -g GIT_URL'
  echo ""
  echo "   -v     Be verbose"
  echo ""
  echo "   -p     Provides a prefix to all slave names entered in /etc/hosts"
  echo "          Defaults to '$SLAVE_PREFIX'"
  echo ""
  echo "   -u     SSH user to use when SSH'ing across to the slaves.  Defaults to current user."
  echo ""
  echo "   -nc    If this flag is passed in, then the working directory on the slave is not cleaned."
  echo ""
  echo "   -async If provided, then each SSH connection will be forked as a separate process"
  echo ""
  echo "   -sb    If provided, the framework and tests will NOT be rebuilt on slaves prior to running."
  echo ""
  echo "   -sc    If provided, the framework will NOT be checked out of source control prior to building."
  echo ""
  echo "   -w     Working directory on the slave.  Defaults to '$WORKING_DIR'."
  echo ""
  echo "   -n     Number of slaves.  This is REQUIRED."
  echo ""
  echo "   -m     Connection to MASTER server.  IP address and port is needed.  This is REQUIRED."
  echo ""
  echo "   -g     URL from which to perform a git clone to pull sources."
  echo ""
  echo "   -h     Displays this help screen"
  echo ""
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
    "-nc")
      CLEAN=false
      ;;
    "-n")
      NUM_SLAVES=$2
      shift
      ;;
    "-async")
      ASYNC=true
      ;;
    "-m")
      MASTER=$2
      shift
      ;;
    "-g")
      GIT_URL=$2
      shift
      ;;
    "-w")
      WORKING_DIR=$2
      shift
      ;;
    "-v")
      VERBOSE=true
      ;;
    "-sb")
      SKIP_BUILD=true
      ;;
    "-sc")
      SKIP_CHECKOUT=true
      ;; 
    *)
      help_and_exit
      ;;
  esac
  shift
done

if [ -z $NUM_SLAVES ] ; then
  echo "FATAL: required information missing!"
  help_and_exit
fi

if [ -z $MASTER ] ; then
  echo "FATAL: required information missing!"
  help_and_exit
fi

if [ -z $GIT_URL ] ; then
  echo "FATAL: required information missing!"
  help_and_exit
fi

if [ "$VERBOSE" = "true" ]
then
  echo "Slave prefix: $SLAVE_PREFIX"
  echo "SSH user: $SSH_USER"
  echo "Clean: $CLEAN"
  echo "Number of slaves: $NUM_SLAVES"
  echo "Async: $ASYNC"
  echo "Master: $MASTER"
  echo "Git URL: $GIT_URL"
  echo "Working directory: $WORKING_DIR"
  echo "Skip checkout? $SKIP_CHECKOUT  Skip build? $SKIP_BUILD"
fi

loop=1
CMD="cd $WORKING_DIR"
if [ "$CLEAN" = "true" ] ; then
  CMD="rm -rf $WORKING_DIR ; mkdir $WORKING_DIR ; cd $WORKING_DIR"
fi

if ! [ "$SKIP_CHECKOUT" = "true" ] ; then
  CMD="$CMD ; git pull $GIT_URL"
fi

CMD="$CMD ; cd cachetester"

if ! [ "$SKIP_BUILD" = "true" ] ; then
  CMD="$CMD ; mvn clean install -Dmaven.test.skip.exec=true"
fi

CMD="$CMD ; bin/launch_local_slave.sh $MASTER"

while [ loop -le $NUM_SLAVES ]
do
  if [ "$ASYNC" = "true" ] ; then
    ssh -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD" &
  else
    ssh -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD"    
  fi 
done

echo ""
echo "... done!  You should have slaves started on all nodes now!"
echo ""

