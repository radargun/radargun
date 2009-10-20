#!/bin/bash

#this is a template file. Implementation of this should be present in each benchmark directory,
#e.g. ./jbosscache-2.0.0/config.sh
# This file will be called by FWK_ROOT/runNode.sh, and is intended for adding specific config params
# to the benchmark runner.

#Uncomment and append values to the variables below, as needed by a specific product

#CLASSPATH=$CLASSPATH:<some other needed values>
#JVM_OPTIONS="$JVM_OPTIONS -DsomeSysProp=value1 -Xprop=value2"

#specify the fqn of the class wrapper for specific cache products
JVM_OPTIONS="$JVM_OPTIONS -DcacheBenchFwk.cacheWrapperClassName=<fqn cache wrapper>"

