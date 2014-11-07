#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then DIRNAME=`dirname $0`; RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
. ${RADARGUN_HOME}/bin/includes.sh

kill -9 `jps | grep 'Slave\|LaunchMaster' | cut -f "1" -d " "` > /dev/null

rm *.out > /dev/null
rm *.log > /dev/null
rm *.pid > /dev/null
rm -rf results > /dev/null

