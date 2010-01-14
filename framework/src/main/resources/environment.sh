#!/bin/sh
###### This script is designed to be called from other scripts, to set environment variables including the bind
###### for cache products, as well as any JVM options.

### Set your bind address for the tests to use. Could be an IP, host name or a reference to an environment variable.
BIND_ADDRESS=${MYTESTIP_2}
JVM_OPTS="-Xms1G -Xmx1G"
JPROFILER_HOME=${HOME}/jprofiler
JPROFILER_CFG_ID=103

