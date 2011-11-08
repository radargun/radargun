#!/bin/bash

## Load includes
if [ "x$RADARGUN_HOME" = "x" ]; then DIRNAME=`dirname $0`; RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
. ${RADARGUN_HOME}/bin/includes.sh

add_fwk_to_classpath
set_env
${JAVA} ${JVM_OPTS} -classpath $CP org.radargun.reporting.LineReportGenerator $*
