---
---

Building binaries
-----------------

### Prerequisites

Following tools are required to build RadarGun from sources

* git >= 1.6.5, needed for obtaining the source code.  Alternatively, a ZIPped archive of the sources can be [used instead](https://github.com/radargun/radargun/zipball/master).
* A [Java Development Kit](http://www.oracle.com/technetwork/java/javase/downloads/index.html) version 1.8 or above is required.
* [Apache Maven](http://maven.apache.org) >= 3.0, needed for downloading dependencies and building the source code


### Download source code

Move to a directory where you will build the binaries and run:

    $ git clone git://github.com/radargun/radargun.git

### Compile and build distribution

    $ cd  radargun
    $ mvn clean install -DskipTests

This operation might take a few minutes, as it will download the dependencies needed by the various plugins (e.g., it will download Infinispan and its dependencies, EHCache and its dependencies, etc). Once the install is complete, you will find the distribution in `./target/distribution/RadarGun-<version>.zip`. Unzip this on cluster nodes where you wish to run RadarGun.

### Distributions's content

    bin
    conf
        test-data
    lib
    plugins(*)
        chm
        docker
        ehcache25
        hazelcast2/36/37
        infinispan4/50/51/52/53/60/70/71/72/80/81/82/90
        jbosscache2/3
        jcache
        jgroups30/32/33/34/35/36
        process
        resteasy-http
        spymemcached
        tomcat8
    reporters
        reporter-default
        reported-perfrepo
    schema

    *-All plugin folders contain lib and conf folder

* The `bin` directory contains scripts for launching the master, the slaves and some other utility scripts.  This directory also contains scripts to run RadarGun in local mode. For details on how to run a distributed benchmark see [Benchmark configuration]({{page.path_to_root}}benchmark_configuration/general.html) and [Using scripts]({{page.path_to_root}}getting_started/using_the_scripts.html).

* The `conf` directory contains example [configuration files](./example_configurations.html) for various benchmarks, log4j configuration and test-data folder with various benchmark-specific data sets

* The `plugins` directory contains a directory for each (product,version) combo that can be benchmarked. `conf` folder contains configuration files used by the product, which can be referenced in benchmark configuration. `lib` folder contains all libraries and other resources needed to run selected product.

* The `reporters` directory includes reporters, which provide way to process and output statistics gathered during test runs.

* The `schema` directory includes XSD schema files which can be used to verify whether benchmark configuration is correct.

### Build parameters

The build process can be altered by providing command line properties. By default maven will build all plugins and extensions (with exceptions listed below) as well es execute all tests. By disabling of extension or plugins unrelated to your present work you can significantly speed up the build itself.

All properties have to be provided separatelly preceeded by "-D" -> property "skipTests" can be enabled (therefore disabling the execution od test cases) by adding "-DskipTests" or "-DskipTests=true" to maven command. 

**Note:** Some extensions depend on other and build will break if dependencies are not resolved

#### Various

* skipTests - will disable test case execution, by far the most time consuming part of the build process
* make-site - will render the project siteinto target folder (**NOTE**: Requires [jekyll](https://jekyllrb.com/) to be installed and accessible trough PATH)

#### Extensions

* **Enabled by default**:
    * **cache**			(parameter `-Dno-cache` to disable)
    * **query**			(parameter `-Dno-query` to disable)
    * **hdrhistogram**		(parameter `-Dno-hdrhistogram` to disable)
    * **mapreduce**		(parameter `-Dno-mapreduce` to disable)
    * **rest**			(parameter `-Dno-rest` to disable)
    * **reporter-default**	(parameter `-Dno-reporter-default` to disable)
    * **reporter-perfrepo**	(parameter `-Dno-reporter-perfrepo` to disable)

* **Disabled by default**:
    * **jpa**			(parameter `-Djpa` to enable)
    * **example-extension**	(parameter `-Dexample-extension` to enable)
    
#### Plugins

* **Enabled by default**:
    * **chm**			(parameter `-Dno-chm` to disable)
    * **ehcache**		(parameter `-Dno-ehcache` to disable)
    * **hazelcast**		(parameter `-Dno-hazelcast` to disable)
    * **jbosscache**		(parameter `-Dno-jbosscache` to disable)
    * **jgroups**		(parameter `-Dno-jgroups` to disable)
    * **infinispan**		(parameter `-Dno-infinispan` to disable)
    * **process**		(parameter `-Dno-process` to disable)
    * **resteasy-http**		(parameter `-Dno-resteasy-http` to disable)
    * **spark**			(parameter `-Dno-spark` to disable)
    * **spymemcached**		(parameter `-Dno-spymemcached` to disable)
    * **jcache**		(parameter `-Dno-jcache` to disable)
    * **tomcat**		(parameter `-Dno-tomcat` to disable)
    * **docker**		(parameter `-Dno-docker` to disable)

* **Disabled by default**:
    * **jgroups35**	(parameter `-Djgroups35` to enable)
    * **coherence**	(parameter `-Dcoherence` to enable)
    * **jdg-early**	(parameter `-Djdg-early` to enable)
    * **jdg**		(parameter `-Djdg` to enable)
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
