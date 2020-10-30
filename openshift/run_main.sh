#!/bin/bash

exec find . | grep "stdout_main.out" > /dev/null
if [ $? -eq 0 ]; then
    echo "RadarGun had previously run. Check out existing log files."
    echo "Waiting for manual re-deployment..."
    exec sleep infinity
else
    exec /opt/radargun/bin/main.sh -c ${RADARGUN_CONFIG} -t -m 0.0.0.0:2103 -J "${CUSTOM_JAVA_OPTS}"
fi
