#!/bin/bash
# author: Mircea.Markus@jboss.com
# cygwin users: add the scripts from :pserver:anoncvs@cygwin.com:/cvs/cygwin-apps/wrappers/java to the $cygwin_home/usr/local/bin
# those would make an automatic conversion from unix CLASSPATH to win classpath, needed when executing java -cp
# todo mmarkus - add another configuration file to set all the jvm params, which should be called by this one (phps benchmatrk-jvm.sh)
# todo mmarkus - move the scripts in an bin directory as there are more and more scrips now
# todo mmarkus - if allJbossCacheTests.sh is launched with nohup it does not work, make that work

preferIPv4Stack=true
DEBUG=debug
CURRENT_INDEX=${1}
CACHE_PRODUCT=${2}
TEST_CFG=${3}
CLUSTER_SIZE=${4}
hostname=`hostname`
PIDFILE=PID.${hostname}.pid

if [ -z $1 ]
then
   echo Usage:
   echo 
   echo      ./runNode.sh [current node index] [cache product to test] [test config file] [cluster size]
   echo param [current node index]    : the index of this node in the list of nodes in the cluster [0 .. <cluster size> - 1]
   echo param [cache product to test] : must be one of the directories names under './cache-products'
   echo param [test config file]      : configuration file to use with the cache product.  Typically resides in './cache-products/XXX/conf/'
   echo param [cluster size]          : total number of nodes that will run tests.
   echo
   echo Example: './runNode.sh 0 jbosscache-2.2.0 pess-repl-sync.xml 3' will start the 1st node running an instance of jbc2.0.0 on a cluster made out of 3 nodes, using the repl_async configuration.
   exit 1
fi

#libraries needed by the fwk, add them to the classpath
for JAR in ./lib/*
do
   CLASSPATH=$CLASSPATH:$JAR
done

export CLASSPATH=$CLASSPATH:./conf:./classes/production/Framework

configFile=./cache-products/${CACHE_PRODUCT}/config.sh

#first check whether the config file exists and load it
if [ -f ${configFile} ]
then
  . ${configFile}
  echo Calling ${configFile} exit code is $?
else
  echo could not find config file ${configFile}, aborting!
  exit 2
fi

. ./bindAddress.sh
. ./jvm_params.sh

JVM_OPTIONS="${JVM_OPTIONS} -Xms${JVM_XMS} -Xmx${JVM_XMX} -DcacheBenchFwk.cacheProductName=${CACHE_PRODUCT} -Dbind.address=${BIND_ADDRESS} -DcacheBenchFwk.cacheConfigFile=${TEST_CFG} -DcurrentIndex=${CURRENT_INDEX} -DclusterSize=${CLUSTER_SIZE} -Djava.net.preferIPv4Stack=${preferIPv4Stack}"

# Sample JPDA settings for remote socket debuging
#JVM_OPTIONS="$JVM_OPTIONS -Xrunjdwp:transport=dt_socket,address=8686,server=y,suspend=y"

TO_EXECUTE="java $JVM_OPTIONS -cp $CLASSPATH org.cachebench.CacheBenchmarkRunner"

if [ "$DEBUG" = "debug" ]
then
   echo Executing:
   echo ${TO_EXECUTE}
   echo
fi

${TO_EXECUTE}
#echo $!>${PIDFILE}
echo "Return code from benchmark runner is $?"

