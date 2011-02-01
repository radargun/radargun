#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then DIRNAME=`dirname $0`; RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
. ${RADARGUN_HOME}/bin/includes.sh


help_and_exit() {
  wrappedecho "Usage: "
  wrappedecho '  $ dist.sh <path to masters log file>'
  wrappedecho ""
  wrappedecho "This script parses master's log file and outputs information about entry distribution over each node."
  wrappedecho "If no file param is provided it defaults to radargun.log"
  exit 0
}

MASTER_LOG_FILE=$1

if test -z "$1"
then 
   MASTER_LOG_FILE="./radargun.log"
fi 

line_identifier="Received size info"
configs=`grep "${line_identifier}" ${MASTER_LOG_FILE} | sed -n 's/^.*Received size info//p' | awk -F: '{print $4}'| awk -F, '{print $1}'|  uniq`

if test -z "$configs" 
then
	help_and_exit
fi	

clusterSizes=`grep "Received size info" $MASTER_LOG_FILE | sed -n 's/^.*clusterSize://p' | awk -F, '{print $1}' | sort |  uniq;`

for config in $configs
do
	echo
	echo "Configuration : $config"			
	for size in $clusterSizes
	do		
		entryCount=`grep "Received size info" $MASTER_LOG_FILE  | grep "config:${config}, clusterSize:${size}" | sed -n 's/^.*cacheSize://p' | sed -n 's/$/,/p'`
		entries=""		
		for aCount in $entryCount
		do
			entries="${entries} ${aCount/,/}"
		done
		echo "Cluster size: ${size} -> (${entries})"
	done	
done