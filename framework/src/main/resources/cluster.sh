#!/bin/bash

##################################################################################
# 
# This script allows you to start multiple cache bench instances across a cluster
#
# Note that this requires BASH, as well as that all your instances have the cache
# bench framework installed in the same location.  Finally, it requires SSH keys
# set up such that executing commands using 
#
#     $ ssh hostName "command" 
# 
# will work without prompting for passwords, or any user input.
#
##################################################################################

## Some user-cnfigurable variables:

# Defaults to the currently logged in user
SSH_USER=${USER}

# Cache instance you wish to run - taken from the command line.
CACHE_DIST=${2}

CFG_FILE=${3}

# Number of servers to launch on - taken from the command line.
NUM_SERVERS=${4}

COMMAND=${1}

# the DNS domain of your servers
DOMAIN="qa.atl.jboss.com"
# the host names of available servers
HOSTNAMES=( cluster01 cluster02 cluster03 cluster04 cluster05 cluster06 cluster07 cluster08 cluster09 cluster10 )

CACHE_BENCHMARK_HOME=`pwd`
echo "Using cache benchmark home: $CACHE_BENCHMARK_HOME"

case $COMMAND in
    start)
         for ((idx=0; idx < NUM_SERVERS ; idx++))
         do
            server="${HOSTNAMES[idx]}"
            echo Starting CacheBenchmarkFramework on host ${server}
            ssh ${SSH_USER}@${server}.${DOMAIN} ". .bash_profile && cd ${CACHE_BENCHMARK_HOME} &&  ./runNode.sh ${idx} ${CACHE_DIST} ${CFG_FILE} ${NUM_SERVERS} &" &
	    echo "Return code from ssh call  is $?"
            sleep 2
         done
        ;;
    stop)
      for ((idx=0; idx < ${#HOSTNAMES[*]} ; idx++))
         do
            server="${HOSTNAMES[idx]}"
            echo Stopping CacheBenchmarkFramework on host ${server}
            ssh ${SSH_USER}@${server}.${DOMAIN} "cd ${CACHE_BENCHMARK_HOME} &&  ./killNode.sh &"
         done
      ;;
    *)
        echo "Usage: ${0} [start | stop] [cache distribution to test] [name of configuration file to use] [number of servers to launch on]"
        echo
        echo "Example:"
        echo "        ${0} start jbosscache-2.0.0 repl_async.xml 6"
        echo "        ${0} stop"
        echo "             Note that the stop command will stop all instances on all configured hosts"
        ;;
esac
