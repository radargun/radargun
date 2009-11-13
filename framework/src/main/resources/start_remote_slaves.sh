#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

SLAVE_PREFIX=slave
SSH_USER=$USER
CLEAN=true
WORKING_DIR=work
ASYNC=false
VERBOSE=false
SKIP_CHECKOUT=false
SKIP_BUILD=false

help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ start_remote_slaves.sh [-v] [-p SLAVE_PREFIX] [-u ssh_user] [-nc] [-async] [-sb] [-sc] [-w WORKING DIRECTORY] [-g GIT_URL] -plugin plugin_name -n num_slaves -m MASTER_IP:PORT '
  wrappedecho ""
  wrappedecho "   -v       Be verbose"
  wrappedecho ""
  wrappedecho "   -p       Provides a prefix to all slave names entered in /etc/hosts"
  wrappedecho "            Defaults to '$SLAVE_PREFIX'"
  wrappedecho ""
  wrappedecho "   -u       SSH user to use when SSH'ing across to the slaves.  Defaults to current user."
  wrappedecho ""
  wrappedecho "   -nc      If this flag is passed in, then the working directory on the slave is not cleaned. Defaults to $CLEAN"
  wrappedecho ""
  wrappedecho "   -async   If provided, then each SSH connection will be forked as a separate process. Defaults to $ASYNC"
  wrappedecho ""
  wrappedecho "   -sb      If provided, the framework and tests will NOT be rebuilt on slaves prior to running. Defaults to $SKIP_BUILD"
  wrappedecho ""
  wrappedecho "   -sc      If provided, the framework will NOT be checked out of source control prior to building. Defaults to $SKIP_CHECKOUT"
  wrappedecho ""
  wrappedecho "   -w       Working directory on the slave.  Defaults to '$WORKING_DIR'."
  wrappedecho ""
  wrappedecho "   -g       URL from which to perform a git clone to pull sources.  Only needed if -sc is NOT specified."
  wrappedecho ""
  wrappedecho "   -plugin  Name of the cache plugin to load onto the slave's classpath.  This is REQUIRED."  
  wrappedecho ""
  wrappedecho "   -n       Number of slaves.  This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -m       Connection to MASTER server.  IP address:port is needed.  This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -h       Displays this help screen"
  wrappedecho ""
  exit 0
}

welcome "This script is used to launch slaves on remote nodes, via SSH.  Relies on SSH keys set up such that remote commands may be executed via SSH."

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
    "-plugin")
      PLUGIN=$2
      shift
      ;;
    *)
      help_and_exit
      ;;
  esac
  shift
done

if [ -z $NUM_SLAVES ] ; then
  echo "FATAL: required information (-n) missing!"
  help_and_exit
fi

if [ -z $MASTER ] ; then
  echo "FATAL: required information (-m) missing!"
  help_and_exit
fi

if [ -z $PLUGIN ] ; then
  echo "FATAL: required information (-plugin) missing!"
  help_and_exit
fi

if ! [ -d ${CBF_HOME}/plugins/$PLUGIN ] ; then
  echo "FATAL: unknown plugin ${PLUGIN}! Directory doesn't exist in ${CBF_HOME}/plugins!"
  exit 2
fi

if [ -z $GIT_URL ] ; then
  if [ $SKIP_BUILD = "false" ] ; then
    echo "FATAL: required information (-g) missing!"
    help_and_exit
  fi
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
  echo "Plugin: $PLUGIN"
  echo "Working directory: $WORKING_DIR"
  echo "Skip checkout? $SKIP_CHECKOUT  Skip build? $SKIP_BUILD"
fi

loop=1
CMD="source ~/.bash_profile ; cd $WORKING_DIR"
if [ "$CLEAN" = "true" ] ; then
  CMD="rm -rf $WORKING_DIR ; mkdir $WORKING_DIR ; cd $WORKING_DIR"
fi

if ! [ "$SKIP_CHECKOUT" = "true" ] ; then
  CMD="$CMD ; git pull $GIT_URL ; cd cachebenchfwk "
fi

if ! [ "$SKIP_BUILD" = "true" ] ; then
  CMD="$CMD ; mvn clean install -Dmaven.test.skip.exec=true"
fi

CMD="$CMD ; bin/start_local_slave.sh -m $MASTER -plugin $PLUGIN"

while [ $loop -le $NUM_SLAVES ]
do
  if [ "$ASYNC" = "true" ] ; then
    ssh -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD -i $loop" &
  else
    ssh -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD -i $loop"
  fi
  let "loop+=1"
done

echo ""
echo "... done!  You should have slaves started on all nodes now!"
echo ""

