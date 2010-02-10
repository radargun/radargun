#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

add_fwk_to_classpath
set_env
java ${JVM_OPTS} -classpath $CP org.cachebench.reporting.ChartGenerator ${*}
