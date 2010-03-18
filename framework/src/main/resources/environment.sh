#!/bin/sh
###### This script is designed to be called from other scripts, to set environment variables including the bind
###### for cache products, as well as any JVM options.

### Set your bind address for the tests to use. Could be an IP, host name or a reference to an environment variable.
BIND_ADDRESS=${MYTESTIP_2}
JG_FLAGS="-Dresolve.dns=false -Djgroups.timer.num_threads=4"
JVM_OPTS="-server -Xmx1G -Xms1G -Xmn700M -Xss128K -XX:CompileThreshold=10000 -XX:+AggressiveHeap -XX:ThreadStackSize=64K -XX:SurvivorRatio=8 -XX:TargetSurvivorRatio=90 -XX:MaxTenuringThreshold=31 -Xshare:off"
JVM_OPTS="$JVM_OPTS $JG_FLAGS"
JPROFILER_HOME=${HOME}/jprofiler6
JPROFILER_CFG_ID=103

