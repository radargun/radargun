#!/bin/bash

exec find . | grep "stdout_slave*" > /dev/null
if [ $? -eq 0 ]; then
    echo "RadarGun had previously run. Check out existing log files."
    echo "Waiting for manual re-deployment..."
    exec sleep infinity
else
    export DNS_SERVER=`cat /etc/resolv.conf | grep nameserver | awk '{print $2}'`
    export HOSTNAME=`hostname -i`
    exec /opt/radargun/bin/slave.sh -t -m ${RADARGUN_MASTER}:2103 -J "${CUSTOM_JAVA_OPTS} -Ddns.address=${DNS_SERVER} -Dbind.address=${HOSTNAME}"
fi

