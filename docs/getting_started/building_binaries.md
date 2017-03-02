---
---

Building binaries
-----------------

### Prerequisites

Following tools are required to build RadarGun from sources

* git &gt;= 1.6.5, needed for obtaining the source code.  Alternatively, a ZIPped archive of the sources can be <a href="https://github.com/radargun/radargun/zipball/master">used instead</a>.
* A [Java Development Kit](http://www.oracle.com/technetwork/java/javase/downloads/index.html) version 1.7 or above is required.
* [Apache Maven](http://maven.apache.org) &gt;= 3.0, needed for downloading dependencies and building the source code


### Download source code

Move to a directory where you will build the binaries and run:

    $ git clone git://github.com/radargun/radargun.git

### Compile and build distribution

    $ cd  radargun
       $ mvn clean install -DskipTests

This operation might take a few minutes, as it will download the dependencies needed by the various plugins (e.g., it will download Infinispan and its dependencies, EHCache and its dependencies, etc). Once the install is complete, you will find the distribution in `./target/distribution/RadarGun-<version>.zip`. Unzip this on cluster nodes where you wish to run RadarGun.

### Distributions's content (for RadarGun 2.1.x)

       |-bin
       |-conf
       |-lib
       |-plugins
       |---ehcache26
       |-----conf
       |-----lib
       |---hazelcast3
       |-----conf
       |-----lib
       |---infinispan80
       |-----conf
       |-----lib
       |---...
       |-reporters
       |-schema
       |-results (optional)


* The `bin` directory contains scripts for launching the master, the slaves and some other utility scripts.  This directory also contains scripts to run RadarGun in local mode. For details on how to run a distributed benchmark see [Benchmark configuration]({{page.path_to_root}}architecture/benchmark_configuration.html) and [Using scripts]({{page.path_to_root}}getting_started/using_the_scripts.html).

* The `conf` directory contains configuration files for running distributed benchmarks (`benchmark-dist.xml`), local benchmarks (`local-benchmark.xml`) and the log4j cofiguration file.

* The `plugins` directory contains a directory for each (product,version) combo that can be benchmarked. `conf` folder contains configuration files used by the product, which can be referenced in benchmark configuration. `lib` folder contains all libraries and other resources needed to run selected product.

* The `reporters` directory includes reporters, which provide way to process and output statistics gathered during test runs.

* The `schema` directory includes XSD schema files which can be used to verify whether benchmark configuration is correct.

* By default the `results` directory is created once the test finishes and reporters generate results. Result location is configurable.
