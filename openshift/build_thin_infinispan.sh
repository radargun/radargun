#!/bin/bash

cd ..
mvn clean install -Dmaven.javadoc.skip=true -Dno-chm -Dno-ehcache -Dno-jbosscache -Dno-zip -Dno-hazelcast -Dno-jgroups -Dno-redis -Dno-resteasy-http -Dno-spark -Dno-spymemcached -Dno-tomcat -Dno-etcd -Dno-docker -Dno-couchbase -Dno-eap -DskipTests -Dlog4j2
cd openshift

REMOVE_ISPN_PLUGIN=('infinispan4' 'infinispan50' 'infinispan51' 'infinispan52' 'infinispan53' 'infinispan60' 'infinispan70' 'infinispan71' 'infinispan72' 'infinispan80' 'infinispan81' 'infinispan82' 'infinispan90' 'infinispan91' 'infinispan92' 'infinispan93')

for ispn in "${REMOVE_ISPN_PLUGIN[@]}"
do
  echo "Removing plugin $ispn, please wait ..."
  rm -fR ../target/distribution/RadarGun-3.0.0-SNAPSHOT/plugins/$ispn/lib
done