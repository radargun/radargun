#!/usr/bin/env bash

export JAVA_HOME=/Users/dlovison/.sdkman/candidates/java/current
export PLUGINNAME=infinispan-snapshot
export TRANSPORT=udp
export WORKSPACE=/Users/dlovison/Documents/GitHub/infinispan/
export LIBRARY_MODE=dist
export JGROUPS_CONFIG=default-configs/default-jgroups-$TRANSPORT.xml
export ENABLE_SERVER_FLIGHT_RECORDER=false
export SERVER_CONFIGURATION_FILE=clustered-transform.xslt

./clean.sh
./main.sh -c library-repl-writes-ispn.xml
./worker.sh -d 7777 --debug-suspend
./worker.sh

tail -f stdout_main.out
