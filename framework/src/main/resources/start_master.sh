#!/bin/bash

DIRNAME=`dirname $0`

# Setup CBF_HOME
if [ "x$CBF_HOME" = "x" ]; then
    # get the full path (without any relative bits)
    CBF_HOME=`cd $DIRNAME/..; pwd`
fi
export CBF_HOME

CP=${CBF_HOME}/conf
for i in ${CBF_HOME}/lib/*.jar ; do
  CP=$CP:$i
done

java -classpath $CP -Djava.net.preferIPv4Stack=true -Dbind.address=${MYTESTIP_2} org.cachebench.fwk.BenchmarkServer ${*}
