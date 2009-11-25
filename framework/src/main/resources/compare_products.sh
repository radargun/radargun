#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh


#### parse plugins we want to test
PLUGINS="infinispan4 jbosscache3"
JBC3_CFGS="mvcc/mvcc-repl-sync.xml mvcc/mvcc-repl-async.xml"
ISPN4_CFGS="repl-sync.xml repl-async.xml dist-sync.xml dist-async.xml dist-sync-l1.xml dist-async-l1.xml"
MAX_NODES=8
HOST_IP=${MYTESTIP_2}


for plugin in $PLUGINS ; do
  ### Somehow get the plugin name
  if [ $plugin = "infinispan4" ] ; then
    CFGS=${ISPN4_CFGS}
  else
    CFGS=${JBC3_CFGS}
  fi
  
  for config in $CFGS ; do
    ### Start the master process    
    ${CBF_HOME}/bin/start_master.sh -m $HOST_IP -n $MAX_NODES -x $config
    PID_OF_MASTER_PROCESS=$!
    
    ## Now start slaves
    ${CBF_HOME}/bin/start_remote_slaves.sh -n $MAX_NODES -m $HOST_IP -plugin $plugin
    
    wait_for_process $PID_OF_MASTER_PROCESS
  done
done
