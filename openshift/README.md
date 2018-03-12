# RadarGun in OpenShift

The openshift.sh script provides functionality for building Docker image for RadarGun, pushing the image to the
given Docker repository, running performance tests within a remote OpenShift instance and downloading results to 
the local filesystem when the tests finish. All processes run inside OpenShift within a common namespace/project.

The master and slave processes will run in separate Pods in OpenShift. The user can specify the number of slaves that will result
in creating that many Pods for slaves in OpenShift. There's always a single Pod for the master process. The user can also specify
custom JVM options for RadarGun processes.

Follow these steps to build and deploy RadarGun image in OpenShift and run performance tests:

1) Build RadarGun from top-level directory using Maven. This will produce target/distribution/RadarGun-3.0.0-SNAPSHOT directory
     that will be used in the resulting Docker image.

    `mvn clean install`

2) export DOCKER_REGISTRY address where RadarGun images will be pushed for consumption by OpenShift deployments.

    `export DOCKER_REGISTRY=<OpenShift's internal docker registry address>`
    
    Note: The registry address can be usually obtained by opening "About" link in the top-right corner of your OpenShift project's web UI.
    
3) Pass login command specific to OpenShift provider and log in

    `./openshift -L "oc login https://api.rh-us-xxx-1.openshift.com --token=l5gGjuKOPAWsdf6564sdfeOI7qhmQCGhJLvyj4oa4"`
    
    Note: The login command can be usually obtained by opening "Command line tools" link in the top-right corner of your OpenShift project's web UI. 

4) Create your project. By default called "myproject". Customization is only possible by changing the openshift.sh script.

    `./openshift -n`

5) Build RG image and push it to the remote Docker registry.

    `./openshift -b`

6) Create a subdirectory with configuration files for RadarGun and individual plugins. This directory will be mounted in master
     and slave pods in OpenShift as /opt/radargun-configs. 

7) Create RadarGun deployment via template.

    `./openshift -d -cd "config/" -cf "radargun-benchmark.xml" -n 2`
    
    Note: The example uses a RadarGun config file named "radargun-benchmark.xml" . It needs to be placed in the config/ sub-directory before
    running the command. 
    
    Note: The config file will be finally placed in the running container under /opt/radargun-configs/ and RadarGun will consume it from there.

8) Collect results and log files when the tests finish. Results are available in the Master pod as /opt/radargun-data/results.
     Logs are available in all RadarGun pods in /opt/radargun-data

    `./openshift -r`

9) Optional: Purge your project before running the tests again

    `./openshift -p`



