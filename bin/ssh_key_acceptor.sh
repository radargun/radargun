#!/bin/bash

echo ""
echo "=== RadarGun ==="
echo " This script is to be used on environments where worker nodes are provisioned via PXE boot"
echo " and a main provides this PXE image and also acts as a DHCP server.  This script should"
echo " *only* be run on the main, to SSH into each worker and 'accept' its SSH key."
echo ""

WORKER_PREFIX=worker
SSH_USER=$USER
KEEP=false
ASYNC=false
VERBOSE=false

help_and_exit() {
  echo "Usage: "
  echo '  $ ssh_key_acceptor.sh [-p WORKER_PREFIX] [-u ssh user] [-keep] [-async] [-v] -n num_workers '
  echo ""
  echo "   -p     Provides a prefix to all worker names entered in /etc/hosts"
  echo "          Defaults to '$WORKER_PREFIX'"
  echo ""
  echo "   -u     SSH user to use when SSH'ing across to the workers.  Defaults to current user."
  echo ""
  echo "   -keep  If this flag is passed in, then the user's '.ssh/known_hosts' file is NOT nuked"
  echo ""
  echo "   -async If provided, then each SSH connection will be forked as a separate process"
  echo ""
  echo "   -v     Be verbose"
  echo ""
  echo "   -n     Number of workers.  This is REQUIRED."
  echo ""
  echo "   -h     Displays this help screen"
  echo ""
  exit 0
}

### read in any command-line params
while ! [ -z $1 ] 
do
  case "$1" in
    "-v")
      VERBOSE=true
      ;;
    "-p")
      WORKER_PREFIX=$2
      shift 
      ;;
    "-u")
      SSH_USER=$2
      shift
      ;;
    "-keep")
      KEEP=true
      ;;
    "-n")
      NUM_WORKERS=$2
      shift
      ;;
    "-async")
      ASYNC=true
      ;;
    *)
      help_and_exit
      ;;
  esac
  shift
done


if [ -z $NUM_WORKERS ] ; then
  echo "FATAL: need to provide the number of workers to connect to."
  exit 1
fi

if [ "$VERBOSE" = "true" ] 
then
  echo "Worker prefix: $WORKER_PREFIX"
  echo "SSH user: $SSH_USER"
  echo "Keep known_hosts file? $KEEP"
  echo "Number of worker nodes: $NUM_WORKERS"
  echo "Async? $ASYNC" 
fi

sshcmd='ssh'

if [ "$KEEP" = "false" ] ; then
  if [ "$VERBOSE" = "true" ] ; then
    echo "Removing ${HOME}/.ssh/known_hosts"
  fi
  rm ${HOME}/.ssh/known_hosts >> /dev/null
fi

CMD="pwd"

loop=1
while [ $loop -le $NUM_WORKERS ] ; do
  echo "  ... processing $WORKER_PREFIX$loop ..."
  if [ "$ASYNC" = "true" ]
  then
    $sshcmd -q -o "StrictHostKeyChecking false" $SSH_USER@$WORKER_PREFIX$loop "$CMD" >> /dev/null &
  else
    $sshcmd -q -o "StrictHostKeyChecking false" $SSH_USER@$WORKER_PREFIX$loop "$CMD" >> /dev/null
  fi
  let "loop+=1"
done

echo ""
echo "... done!  You should now be able to SSH without warning prompts!"
echo ""


