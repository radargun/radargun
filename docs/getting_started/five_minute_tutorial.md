---
---
 
Five minute tutorial
--------------------

### RadarGun basics?

RadarGun is based on the main-worker pattern and the concept of **stages**. The execution is controlled from the `main node`, which sequentially signalizes the `worker nodes` that a stage should be ran, all workers run this stage in parallel and send results to the main to process them. After all workers have finished execution another stage is executed.

The `worker nodes` may be (and usually are) divided into groups fulfilling different roles in the cluster (cliens/servers), acording to configuration, `main node` does not participate in the actual benchmarking.

### Install

Provided you use one of the released versions [RadarGun 2.1.0](https://github.com/radargun/radargun/releases/download/RadarGun-2.1.0.Final/RadarGun-2.1.0.Final.zip) or [RadarGun 1.1.0](https://github.com/radargun/radargun/releases/download/RadarGun-1.1.0.Final/RadarGun-1.1.0.Final.zip), just download the file and unzip it.

    $ unzip radargun-x.y.z.zip
    $ cd radargun-x.y.z


Radargun 3.0 is still under development, but you can check out sources from git and build, using:

    $ git clone https://github.com/radargun/radargun.git
    $ cd radargun
    $ mvn clean install -DskipTests


The installation will create `distribution` folder in `target`. For more details you mmay reffer to [Building binaries]({{page.path_to_root}}getting_started/building_binaries.html).

### Edit your configuration

Configurations reside in the `conf` folder. Please see `conf/benchmark-dist.xml` config file for basic distributed benchmark example. There are several examples covering MapReduce, querying, JCache integration etc. Please see these files for more details on how to configure RadarGun.

Some notes about the configuration file:

* the **main** element defines where the main process listens for connections from workers. The main process is the one that coordinates multiple workers for running a distributed benchmark.

* the [**clusters**]({{page.path_to_root}}benchmark_configuration/clusters.html) element specifies the size (and optionally group distribution) of cluster(s) the benchmark will be ran on.
* the [**configurations**]({{page.path_to_root}}benchmark_configuration/configurations.html) element specifies which products are benchmarked and their configurations (optionally specific to individual groups). 

        For each configuration-cluster combo, one benchmark (described by `scenario` element) is ran.

* the [**scenario**]({{page.path_to_root}}benchmark_configuration/scenario.html) element configures what stages are performed during benchmark. The main coordinates all the workers, so that each stage starts on all workers at the same time.
* the [**reports**]({{page.path_to_root}}benchmark_configuration/reports.html) element configures what reports are generated from data gathered during scenarios.

### Start the main node

    ./bin/main.sh
      === Cache Benchmark Framework: main.sh ===
      This script is used to launch the main process, which coordinates tests run
      on workers.
      
      Main's PID is 37133 running on spark.local

You can also specify which benchmark configuration file will be used by running `./bin/main.sh -c /path/to/benchmark`. If `-c` option is not provided, default value is `./conf/benchmark-dist.xml`.

Check that main is successfully started:

    ./bin/main.sh -status
      Main is running, pid is 37037.

See all available options by showing help with `./bin/main.sh -h`.

### Start the workers

The number of workers that need to be started is defined by highest settings in `clusters` element, in our example it is 3.

    $ ./bin/worker.sh
      === Radargun: worker.sh ===
      This script is used to launch the local worker process.
      
      ... done! Worker process started on host spark.local! Worker PID is 19320

    $ ./bin/worker.sh
      === Radargun: worker.sh ===
      This script is used to launch the local worker process.
      ... done! Worker process started on host spark.local! Worker PID is 19357

    $ ./bin/worker.sh
      === Radargun: worker.sh ===
      This script is used to launch the local worker process.
      ... done! Worker process started on host spark.local! Worker PID is 19386


*Note* in this example we use `worker.sh` that starts a process on the same machine.  In real-world, you would most likely have multiple nodes running on multiple machines. For easily starting remote worker processes on remote machines, refer to `dist.sh`: this knows how to ssh on remote nodes and run `worker.sh` there. It can be configured via `environment.sh`, where addresses of individual workers need to be specified (WORKER_ADDRESS). Optionally, addresses to which the workers bind can be included (BIND_ADDRESS).

    worker1_WORKER_ADDRESS=127.0.0.1
    worker1_BIND_ADDRESS=127.0.0.2

    worker2_WORKER_ADDRESS=127.0.0.1
    worker2_BIND_ADDRESS=127.0.0.3

    worker3_WORKER_ADDRESS=127.0.0.1
    worker3_BIND_ADDRESS=127.0.0.4

Running the main and workers is then straightforward.

    $ ./bin/dist.sh worker1 worker2 worker3

See all available options by showing help with `./bin/worker.sh -h`.

### Check on progress

At this point the benchmark is running, and it will take a while until all the benchmarks are run on all the nodes. To monitor progress run the following:

    $ tail -f radargun.log

When the benchmark is finished you should see something line this:

    13:38:02,017 INFO  [org.radargun.Main] (main) Executed all benchmarks in 4 mins 35 secs, reporting...
    ...
    13:38:13,906 INFO  [org.radargun.Main] (main) All reporters have been executed, exiting.
    13:38:13,914 INFO  [org.radargun.ShutDownHook] (Thread-0) Main process is being shutdown

### See reports

All the reports are generated in the `results` directory. Depending on the settings in `reports` element in configuration, it will contain directories with results in desired format, e.g. `csv` directory, `html` directory etc.

### Where do we go from here

For more details on how to configure and run the benchmarks go to [Benchmark configuration]({{page.path_to_root}}benchmark_configuration/general.html).

For more details on how the scripts should be used refer to [Using scripts]({{page.path_to_root}}getting_started/using_the_scripts.html)
