FROM centos:centos7

MAINTAINER Martin Gencur <mgencur@redhat.com>

RUN yum -y update && \
        yum -y install unzip java-1.8.0-openjdk-devel which hostname ls ps grep pwd id rsync && \
        yum clean all -y

RUN mkdir /opt/radargun /opt/radargun-data 
COPY RadarGun-3.0.0-SNAPSHOT /opt/radargun/

RUN chown -R 0 /opt/radargun /opt/radargun-data && \
        chmod -R g=u /opt/radargun /opt/radargun-data

# Expose RadarGun main port AND clustering port
EXPOSE 2103 7800

WORKDIR /opt/radargun-data
USER 1001

# Set the JAVA_HOME variable to make it clear where Java is located
ENV JAVA_HOME /usr/lib/jvm/java

COPY run_main.sh /opt/radargun/
COPY run_worker.sh /opt/radargun/

CMD ["/opt/radargun/run_main.sh"]
