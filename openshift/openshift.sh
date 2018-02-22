#!/bin/bash

if [ "x$RADARGUN_HOME" = "x" ]; then
  DIRNAME=`dirname $0`
  RADARGUN_HOME=`cd $DIRNAME/..; pwd`
fi;
export RADARGUN_HOME

OSE_MAIN_VERSION=v3.7.1
OSE_SHA1_VERSION=ab0f056
TEST_PROJECT=myproject
RADARGUN_IMAGE=radargun
FULL_IMAGE=${DOCKER_REGISTRY}/${TEST_PROJECT}/${RADARGUN_IMAGE}
RADARGUN_MASTER="radargun-master.${TEST_PROJECT}.svc"
CONFIG_DIR=configs/
RADARGUN_CONFIG="/opt/radargun-configs/library-dist-reads.xml"
CUSTOM_JAVA_OPTS="-Xmx300M -XX:+UseG1GC -XX:MaxGCPauseMillis=300 -XX:InitiatingHeapOccupancyPercent=70 -verbose:gc -XX:+PrintGCDateStamps -XX:+PrintGCDetails"
                                    #-Dlog4j.configurationFile=file:///opt/radargun-configs/log4j2-trace.xml           
TOTAL_CONTAINER_MEM=768
NUMBER_OF_SLAVES=2

help_and_exit() {
  echo "Usage: TBD"
  exit 0
}

function wrappedecho {
  echo "${1}" | fold -s -w 80
}

function download_oc_client {
  if [ -f ./oc ]; then
    echo "OC client installed"
  else
    echo "Installing OC Client ..."
    wget -q -N https://github.com/openshift/origin/releases/download/$OSE_MAIN_VERSION/openshift-origin-client-tools-$OSE_MAIN_VERSION-$OSE_SHA1_VERSION-linux-64bit.tar.gz
    tar -zxf openshift-origin-client-tools-$OSE_MAIN_VERSION-$OSE_SHA1_VERSION-linux-64bit.tar.gz
    cp openshift-origin-client-tools-$OSE_MAIN_VERSION-$OSE_SHA1_VERSION-linux-64bit/oc .
    rm -rf openshift-origin-client-tools-$OSE_MAIN_VERSION+$OSE_SHA1_VERSION-linux-64bit
    rm -rf openshift-origin-client-tools-$OSE_MAIN_VERSION-$OSE_SHA1_VERSION-linux-64bit.tar.gz
  fi
}
    
function build_radargun_image {
    cp -R ${RADARGUN_HOME}/target/distribution/RadarGun-3.0.0-SNAPSHOT ./
    sudo docker build -t ${RADARGUN_IMAGE} .
    sudo docker login -u $(./oc whoami) -p $(./oc whoami -t) ${DOCKER_REGISTRY}
    sudo docker tag ${RADARGUN_IMAGE} ${FULL_IMAGE}
    sudo docker push ${FULL_IMAGE}
    ./oc set image-lookup ${RADARGUN_IMAGE}
}

function new_project {
    ./oc delete project ${TEST_PROJECT} || true
    ( \
        while ./oc get projects | grep -e ${TEST_PROJECT} > /dev/null; do \
            echo "Waiting for deleted projects..."; \
            sleep 5; \
        done; \
    )
    ./oc new-project ${TEST_PROJECT} || true
}

function deploy_template {
    ./oc create -f radargun-template.json
    ./oc process radargun \
           -p TOTAL_CONTAINER_MEM=${TOTAL_CONTAINER_MEM} \
           -p NUMBER_OF_SLAVES=${NUMBER_OF_SLAVES} \
           -p RADARGUN_MASTER=${RADARGUN_MASTER} \
           -p RADARGUN_CONFIG=${RADARGUN_CONFIG} \
           -p CUSTOM_JAVA_OPTS="${CUSTOM_JAVA_OPTS}" | oc create -f -
}

function create_config_map {
    ./oc create configmap radargun-configs \
    --from-file=${CONFIG_DIR}
    ./oc label configmap radargun-configs template=radargun
}

function purge_project {
    ./oc delete all,secrets,sa,templates,configmaps,daemonsets,clusterroles,rolebindings,serviceaccounts,statefulsets --selector=template=radargun || true
    ./oc delete persistentvolumeclaims --selector=application=radargun-master
    ./oc delete persistentvolumeclaims --selector=application=radargun-slave
    ./oc delete template radargun || true
}

function get_results {
    rm -rf radargun-data
    NODES=`./oc get pods --selector=template=radargun -o jsonpath='{.items[*].metadata.name}{"\n"}'`
    for node in $NODES; do
        ./oc rsync ${node}:/opt/radargun-data .
    done
}

download_oc_client

## read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-n"|"--newproject")
        new_project
    ;;
    "-p"|"--purge")
        purge_project
    ;;
    "-b"|"--build")
        build_radargun_image
    ;;
    "-d"|"--deploy")
        create_config_map
        deploy_template 
    ;;
    "-m")
        TOTAL_CONTAINER_MEM=$2
        shift
    ;;
    "-n")
        NUMBER_OF_SLAVES=$2
        shift
    ;;
    "-cd"|"--config-dir")
        CONFIG_DIR=$2
        shift
    ;;
    "-cf"|"--config-file")
        RADARGUN_CONFIG=$2
        shift
    ;;
    "-r"|"--results")
        get_results
    ;;
    "-J")
        CUSTOM_JAVA_OPTS=$2
        shift
    ;;
    "-L"|"--login")
        LOGIN_COMMAND=$2
        exec ./${LOGIN_COMMAND}
        shift
    ;;
    *)
      echo "Warning: unknown argument ${1}" 
      help_and_exit
      ;;
  esac
  shift
done
