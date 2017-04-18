---
---

Example configurations
----------------------

Following benchmark [configuration]({{page.path_to_root}}benchmark_configuration/general.html) files can be found in `/conf` folder of [built](./building_binaries.html) project.

**[benchmark-aggregation-query.xml](https://github.com/radargun/radargun/blob/master/extensions/query/src/main/resources/benchmark-aggregation-query.xml)** - Shows correct usage of `query` extension on Infinispan 9.0

**[benchmark-analysis.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-analysis.xml)** - Shows how to analyze results of a stage and then re-use the results in another stage/store them to results via `analyze-test` and `add-result` stages

**[benchmark-coherence-hazelcast.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-coherence-hazelcast.xml)** - Shows how to run comparative analysis of three products (Coherence, Hazelcast and Infinispan)

**[benchmark-combined-report.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-combined-report.xml)** - Shows how to property handle combining different tests

**[benchmark-condition.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-condition.xml)** - Shows how to use conditions in the `repeat` stage

**[benchmark-continuous-query.xml](https://github.com/radargun/radargun/blob/master/extensions/query/src/main/resources/benchmark-continuous-query.xml)** - Shows how to call continuous queries using `continuous-query` stage

**[benchmark-dist-custom-vars.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-dist-custom-vars.xml)** - Shows how to define custom properties through `define` stage

**[benchmark-distexec.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-distexec.xml)** - Shows how to call `java.util.concurrent.Callable` implementations via `distributed-task` stage

**[benchmark-dist.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-dist.xml)** - Shows the most basic benchmark on multiple clusters and configurations.

**[benchmark-hazelcast-server.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-hazelcast-server.xml)** - Shows performance test for Hazelcast client/server configuration

**[benchmark-hotrod-rest-memcached.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-hotrod-rest-memcached.xml)** - Shows performance testing for same-server/different-client configurations

**[benchmark-hotrod-spark-mapreduce.xml](https://github.com/radargun/radargun/blob/master/extensions/mapreduce/src/main/resources/benchmark-hotrod-spark-mapreduce.xml)** - Shows how to benchmark MapReduce operations on Infinispan coupled with Spark

**[benchmark-hotrod-streaming.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-hotrod-streaming.xml)** - Shows how to compare memory usage on Infinispan clients using and not using Streaming API

**[benchmark-iteration.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-iteration.xml)** - Shows how to use `iterate` stage to iterate over all entries in the cache

**[benchmark-jcache.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-jcache.xml)** - Shows how to configure comparative benchmark on two products over JCache

**[benchmark-jgroups.xml](https://github.com/radargun/radargun/blob/master/core/src/main/resources/benchmark-jgroups.xml)** - Shows how to execute JGroups probe queries using `j-groups-probe` stage

**[benchmark-mapreduce.xml](https://github.com/radargun/radargun/blob/master/extensions/mapreduce/src/main/resources/benchmark-mapreduce.xml)** - Shows how to execute mapReduce operations using `map-reduce` stage

**[benchmark-query.xml](https://github.com/radargun/radargun/blob/master/extensions/query/src/main/resources/benchmark-query.xml)** - Shows how to load values from dictionary and then query them using `query` extension

**[benchmark-redis.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-redis.xml)** - Shows how to setup a performance test for Redis

Environment variables:
* *RADARGUN_DISTRO_PATH*   - pointing to Radargun distribution
* *REDIS_DISTRO_ZIP_PATH*  - pointing to zip containing built Redis

**[benchmark-rest-cs-tomcat.xml](https://github.com/radargun/radargun/blob/master/extensions/rest/src/main/resources/benchmark-rest-cs-tomcat.xml)** - Shows how to call REST operation on web server using `rest-operations-test` stage

**[benchmark-stream.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-stream.xml)** - Shows how to execute distributed stream tasks via `stream` stage

**[benchmark-xsite-jmx.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-xsite-jmx.xml)** - Shows how to invoke JMX-exposed methods via `jmx-invocation` stage and how to test cross-site replication via `xs-repl-check` stage

**[benchmark-xsite-repl.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-xsite-repl.xml)** - Shows hot to test cross-site replication via `xs-repl-check` stage
