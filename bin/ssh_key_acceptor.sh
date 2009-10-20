#!/bin/bash

echo ""
echo "=== Cache Benchmark Framework ==="
echo " This script is to be used on environments where slave nodes are provisioned via PXE boot"
echo " and a master provides this PXE image and also acts as a DHCP server.  This script should"
echo " *only* be run on the master, to SSH into each slave and 'accept' its SSH key."
echo ""

SLAVE_PREFIX=slave
SSH_USER=$USER
KEEP=false
ASYNC=false
VERBOSE=false

help_and_exit() {
  echo "Usage: "
  echo '  $ ssh_key_acceptor.sh [-p SLAVE_PREFIX] [-u ssh user] [-keep] [-async] [-v] -n num_slaves '
  echo ""
  echo "   -p     Provides a prefix to all slave names entered in /etc/hosts"
  echo "          Defaults to '$SLAVE_PREFIX'"
  echo ""
  echo "   -u     SSH user to use when SSH'ing across to the slaves.  Defaults to current user."
  echo ""
  echo "   -keep  If this flag is passed in, then the user's '.ssh/known_hosts' file is NOT nuked"
  echo ""
  echo "   -async If provided, then each SSH connection will be forked as a separate process"
  echo ""
  echo "   -v     Be verbose"
  echo ""
  echo "   -n     Number of slaves.  This is REQUIRED."
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
      SLAVE_PREFIX=$2
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
      NUM_SLAVES=$2
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


if [ -z $NUM_SLAVES ] ; then
  echo "FATAL: need to provide the number of slaves to connect to."
  exit 1
fi

if [ "$VERBOSE" = "true" ] 
then
  echo "Slave prefix: $SLAVE_PREFIX"
  echo "SSH user: $SSH_USER"
  echo "Keep known_hosts file? $KEEP"
  echo "Number of slave nodes: $NUM_SLAVES"
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
while [ $loop -le $NUM_SLAVES ] ; do
  echo "  ... processing $SLAVE_PREFIX$loop ..."
  if [ "$ASYNC" = "true" ]
  then
    $sshcmd -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD" >> /dev/null &
  else
    $sshcmd -q -o "StrictHostKeyChecking false" $SSH_USER@$SLAVE_PREFIX$loop "$CMD" >> /dev/null
  fi
  let "loop+=1"
done

echo ""
echo "... done!  You should now be able to SSH without warning prompts!"
echo ""


