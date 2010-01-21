#!/bin/bash

#see "$CACHE_ROOT/cache-products/cache.sh" for details

THIS_DIR="./cache-products/coherence-3.3.1"

#setting up classpath
for JAR in $THIS_DIR/lib/*
do
   CLASSPATH=$CLASSPATH:$JAR
done

export CLASSPATH="$CLASSPATH:./classes/production/coherence-3.3.1"
export CLASSPATH="$THIS_DIR/conf:$CLASSPATH"
#--classpath was set

#additional JVM options
export JVM_OPTIONS="$JVM_OPTIONS -Djava.net.preferIPv4Stack=true -Dtangosol.coherence.localhost=${MYTESTIP_1}"
export JVM_OPTIONS="$JVM_OPTIONS -Dtangosol.coherence.cacheconfig=cache-config.xml"
export JVM_OPTIONS="$JVM_OPTIONS -DcacheBenchFwk.cacheWrapperClassName=org.cachebench.cachewrappers.Coherence331Wrapper"
