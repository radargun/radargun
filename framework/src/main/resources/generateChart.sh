#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

welcome "This script generates charts from the output CSV files, generated after running thr benchmark framework."

java -cp $CP org.cachebench.reporting.ChartGenerator ${*}
