FROM quay.io/centos/centos:stream9

MAINTAINER Martin Gencur <mgencur@redhat.com>

RUN yum install -y epel-release && \
        rpm --import /etc/pki/rpm-gpg/RPM-GPG-KEY-EPEL-9 && \
        yum -y update && \
        yum -y install procps-ng unzip java-11-openjdk-devel hostname rsync iputils && \
        yum -y clean all

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
