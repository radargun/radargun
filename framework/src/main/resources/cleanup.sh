#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

rm -rf ${CBF_HOME}/reports
rm -rf ${CBF_HOME}/file_ping_dir
rm -f ${CBF_HOME}/*.out
rm -f ${CBF_HOME}/*.log
rm -f ${CBF_HOME}/*.log.*

