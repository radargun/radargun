# RadarGun in OpenShift

Follow these steps to build and deploy RadarGun image in OpenShift and run performance tests there:

1) export DOCKER_REGISTRY address where RadarGun images will be pushed for consumption by OpenShift deployments.

    `export DOCKER_REGISTRY=<OpenShift's internal docker registry address>`
    
    Note: The registry address can be usually obtained by opening "About" link in the top-right corner of your OpenShift project's web UI.
    
2) Pass login command specific to OpenShift provider and log in

    `./openshift.sh -L "oc login https://api.rh-us-xxx-1.openshift.com --token=l5gGjuKOPAWsdf6564sdfeOI7qhmQCGhJLvyj4oa4"`
    
    Note: The login command can be usually obtained by opening "Command line tools" link in the top-right corner of your OpenShift project's web UI. 

3) Create your project

    `./openshift.sh -n`

4) Build RG image and push it to the remote Docker registry

    `./openshift.sh -b`

5) Create a subdirectory with configuration files for RadarGun and individual plugins. This directory will be mounted in master
     and slave pods in OpenShift as /opt/radargun-configs. 

6) Create RadarGun deployment via template.

    `./openshift.sh -d -cd "config/" -cf "/opt/radargun-configs/library-dist-reads.xml" -n 2`
    
    Note: The RadarGun config file is library-dist-reads.xml which we placed in the config/ sub-directory.

7) Collect results and log files when the tests finish. Results are available in the Master pod as /opt/radargun-data/results.
     Logs are available in all RadarGun pods in /opt/radargun-data

    `./openshift.sh -r`

8) Optional: Purge your project before running the tests again

    `./openshift.sh -p`



