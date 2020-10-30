#!/bin/bash

exec find . | grep "stdout_worker*" > /dev/null
if [ $? -eq 0 ]; then
    echo "RadarGun had previously run. Check out existing log files."
    echo "Waiting for manual re-deployment..."
    exec sleep infinity
else
    export DNS_SERVER=`cat /etc/resolv.conf | grep nameserver | awk '{print $2}'`
    export HOSTNAME=`hostname -i`
    exec /opt/radargun/bin/worker.sh -t -m ${RADARGUN_MAIN}:2103 -J "${CUSTOM_JAVA_OPTS} -Ddns.address=${DNS_SERVER} -Dbind.address=${HOSTNAME}"
fi

