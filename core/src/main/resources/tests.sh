#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then
  DIRNAME=`dirname $0`
  RADARGUN_HOME=`cd $DIRNAME/..; pwd`
fi;
export RADARGUN_HOME
. `dirname $0`/includes.sh

# By default run all tests
RUN_ALL=true
RUN_FAILED=false
RUN_SKIPPED=false

FAST_OPTS=""

function help_and_exit() {
   echo "Usage: tests.sh [OPTIONS...] WORKERS..."
   echo "Options:"
   echo "    -f      Run failed tests."
   echo "    -s      Run skipped tests."
   echo "    -h      Show this help."
   echo "    --fast  Run tests in fast mode (short duration)."
   exit 0
}


while ! [ -z $1 ]
do
  case "$1" in
    "-f")
      RUN_ALL=false
      RUN_FAILED=true
      ;;
    "-s")
      RUN_ALL=false
      RUN_SKIPPED=true
      ;;
    "--fast")
      FAST_OPTS="-J \"-Dtest.duration=5s -Dtest.long.duration=5s -Dtest.short.duration=3s -Dwarmup.duration=3s -Drepeat.times=3\""
      ;;
    "-h")
      help_and_exit
      ;;
    *)
      if [ ${1:0:1} = "-" ] ; then
        echo "Warning: unknown argument ${1}"
        help_and_exit
      fi
      WORKERS="$@"
      WORKER_COUNT=$#
      shift $#
      ;;
  esac
  shift
done

AVAILABLE_PLUGINS=`ls -1 $RADARGUN_HOME/plugins`

add_fwk_to_classpath
set_env

CMD_PREFIX="${JAVA} ${JVM_OPTS} -Dworkers=$WORKER_COUNT -classpath $CP org.radargun.config.DomConfigParser "

if [ $RUN_ALL == "true" ]; then
   TEST_LIST=`ls -1 $RADARGUN_HOME/conf/benchmark-*.xml`
fi
if [ $RUN_FAILED == "true" ]; then
   TEST_LIST="$TEST_LIST "`cat failed.tests`
fi
if [ $RUN_SKIPPED == "true" ]; then
   TEST_LIST="$TEST_LIST "`cat skipped.tests`
fi
> failed.tests
> skipped.tests

SUCCESSFUL=0
SKIPPED=0
FAILED=0
if [ -n "$FAST_OPTS" ]; then
   echo "INFO: running tests in fast mode"
fi
for CONFIG_FILE in $TEST_LIST; do
   TEST_NAME=`echo $CONFIG_FILE | sed 's/^.*benchmark-\([^.]*\).xml/\1/'`
   MAX_CLUSTER_SIZE=`$CMD_PREFIX $CONFIG_FILE getMaxClusterSize | tail -n 1`
   PLUGINS=`$CMD_PREFIX $CONFIG_FILE getPlugins | tail -n 1 | tr -s -c '[:alnum:]-._' ' '`
   SKIP=false
   for PLUGIN in $PLUGINS; do
      FOUND=false
      for AP in $AVAILABLE_PLUGINS; do
         if [ $PLUGIN == $AP ]; then
            FOUND=true
            break
         fi
      done
      if [ $FOUND != "true" ]; then
         SKIP=true
         echo "WARN: skipped test $TEST_NAME since plugin '$PLUGIN' was not found in distribution"
         #echo "NOTE: available plugins are $(echo $AVAILABLE_PLUGINS | tr '\n' ' ')"
         let "SKIPPED+=1"
         echo $CONFIG_FILE > skipped.tests
         break
      fi;
   done
   if [ $SKIP == "true" ]; then
      continue
   fi
   if [ $MAX_CLUSTER_SIZE -gt $WORKER_COUNT ]; then
      echo "WARN: skipped test $TEST_NAME since we don't have enough workers; have $WORKER_COUNT, need $MAX_CLUSTER_SIZE";
      let "SKIPPED+=1"
      echo $CONFIG_FILE > skipped.tests
   else
      mkdir -p results/$TEST_NAME
      rm results/$TEST_NAME/* 2> /dev/null

      TEST_WORKERS=`echo $WORKERS | tr -s '[:space:]' '\n' | head -n $MAX_CLUSTER_SIZE | tr '\n' ' '`

      echo "INFO: running test $TEST_NAME with $MAX_CLUSTER_SIZE workers: $TEST_WORKERS"
      if eval "$RADARGUN_HOME/bin/dist.sh --wait -c $CONFIG_FILE $FAST_OPTS -o results/$TEST_NAME $TEST_WORKERS" > /dev/null; then
         echo "INFO: test $TEST_NAME succeeded"
         let "SUCCESSFUL+=1"
      else
         echo "ERROR: test $TEST_NAME failed"
         let "FAILED+=1"
         echo $CONFIG_FILE > failed.tests
      fi
   fi
done;
echo "RESULT: Succeeded: $SUCCESSFUL, Failed: $FAILED, Skipped: $SKIPPED"
