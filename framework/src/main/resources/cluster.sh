#!/bin/bash

##################################################################################
#
# This script allows you to start multiple cache bench slaves across a cluster
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

# Number of servers to launch on - taken from the command line.
NUM_SERVERS=${2}

COMMAND=${1}

# the DNS domain of your servers
DOMAIN="qa.atl2.redhat.com"
# the host names of available servers
HOSTNAMES=( cluster01 cluster02 cluster03 cluster04 cluster05 cluster06 cluster07 cluster08 cluster09 cluster10 )

CACHE_BENCHMARK_HOME=`pwd`
echo "Using cache benchmark home: $CACHE_BENCHMARK_HOME"

for ((idx=0; idx < NUM_SERVERS ; idx++))
do
   server="${HOSTNAMES[idx]}"
   echo Starting CacheBenchmarkFramework on host ${server}
   ssh ${SSH_USER}@${server}.${DOMAIN} ". .bash_profile && cd ${CACHE_BENCHMARK_HOME} &&  ./start_local_slave.sh -mh ${MYTESTIP_2} -mp 1234 -i ${idx} -plugin infinispan4&" &
echo "Return code from ssh call  is $?"
   sleep 2
done
