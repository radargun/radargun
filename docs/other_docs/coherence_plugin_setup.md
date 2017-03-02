---
---

Coherence plugin setup
----------------------

### Installing Coherence in your local Maven repository


1. You need to compile from scratch in order to enable coherence support. Follow the steps from [Building Binaries]({{page.path_to_root}}getting_started/building_binaries.html) first.
2. Go to [Coherence Download](http://www.oracle.com/technetwork/middleware/coherence/downloads/index.html) and download Coherence locally. You'll have to accept the OTN license agreement first.
3. Before the download starts, you'll be asked for your oracle account. If you don't have one follow the `sign-up` link and create one.
4. Unzip the archive and install Coherence in your local Maven repository:


#### Steps to install Coherence

    $ unzip coherence-java-v3.5.3b465.zip
        $ mvn install:install-file -Dfile=coherence/lib/coherence.jar -DgroupId=com.oracle.coherence  -DartifactId=coherence -Dversion=3.5 -Dpackaging=jar
        $ mvn install:install-file -Dfile=coherence/lib/commonj.jar -DgroupId=com.oracle.coherence  -DartifactId=coherence-common -Dversion=3.5 -Dpackaging=jar
        $ mvn install:install-file -Dfile=coherence/lib/tangosol.jar -DgroupId=com.oracle.coherence  -DartifactId=tangosol -Dversion=3.5 -Dpackaging=jar

### Building the Radargun distribution with the Coherence plugin

This is all encapsulated in a Maven profile titled `coherence`.  Note that this profile is disabled by default.  To enable it, use Maven's `-P` switch.  For example, the following command will build the entire framework and all plugins, *including* the Coherence plugin.

    `$ mvn clean install -Pcoherence`

### Verify installation

Ensure that `target/distribution/RadarGun-X.Y.Z/plugins` contains a directory named `coherence3`
