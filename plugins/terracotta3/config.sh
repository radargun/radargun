#!/bin/bash

#see "$CACHE_ROOT/cache-products/cache.sh" for details

THIS_DIR=./cache-products/terracotta-2.5.0

#next line should be modified based on the environment
TC_HOME="${HOME}/java/terracotta-2.5.0"

#add terracotta wrapper class to classpath
CLASSPATH=$CLASSPATH:./classes/production/terracotta-2.5.0

#other specific JVM options
JVM_OPTIONS="$JVM_OPTIONS -DpreferIPv4Stack=true -Dtc.config=$THIS_DIR/tc-client-config.xml -Dtc.install-root=${TC_HOME}"

#next line should be modified based on the environment
JVM_OPTIONS="$JVM_OPTIONS -Xbootclasspath/p:${TC_HOME}/lib/dso-boot/dso-boot-hotspot_linux_150_11.jar"
JVM_OPTIONS="$JVM_OPTIONS -Dradargun.cacheWrapperClassName=org.radargun.cachewrappers.TerracottaWrapper"

