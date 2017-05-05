---
---

Example configurations
----------------------

Following benchmark [configuration]({{page.path_to_root}}benchmark_configuration/general.html) files can be found in `/conf` folder of [built](./building_binaries.html) project.

**NOTE:** When working with external application builds always check you have appropriate version, in case of weird errors check again.

**[benchmark-aggregation-query.xml](https://github.com/radargun/radargun/blob/master/extensions/query/src/main/resources/benchmark-aggregation-query.xml)** - Shows correct usage of `query` extension on Infinispan 9.0

**[benchmark-analysis.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-analysis.xml)** - Shows how to analyze results of a stage and then re-use the results in another stage/store them to results via `analyze-test` and `add-result` stages

**[benchmark-coherence-hazelcast.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-coherence-hazelcast.xml)** - Shows how to run comparative analysis of three products (Coherence, Hazelcast and Infinispan)

This benchmark requires [Coherence plugin]({{page.path_to_root}}other_docs/coherence_plugin.html) which is not built by default due to licensing constraints.

**[benchmark-combined-report.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-combined-report.xml)** - Shows how to property handle combining different tests

This benchmark is using colliding-keys [key selector]({{page.path_to_root}}benchmark_configuration/key_selectors.html), consequently it produces a **lot** of errors in logs due to key clashes.

**[benchmark-condition.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-condition.xml)** - Shows how to use conditions in the `repeat` stage

**[benchmark-continuous-query.xml](https://github.com/radargun/radargun/blob/master/extensions/query/src/main/resources/benchmark-continuous-query.xml)** - Shows how to call continuous queries using `continuous-query` stage

**[benchmark-dist-custom-vars.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-dist-custom-vars.xml)** - Shows how to define custom properties through `define` stage

**[benchmark-distexec.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-distexec.xml)** - Shows how to call `java.util.concurrent.Callable` implementations via `distributed-task` stage

**[benchmark-dist.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-dist.xml)** - Shows the most basic benchmark on multiple clusters and configurations.

**[benchmark-hazelcast-server.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-hazelcast-server.xml)** - Shows performance test for Hazelcast client/server configuration

**[benchmark-hotrod-rest-memcached.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-hotrod-rest-memcached.xml)** - Shows performance testing for same-server/different-client configurations

> Environment variables:
> * *ISPN_8_HOME*	- pointing to Infinispan server home directory

**[benchmark-hotrod-spark-mapreduce.xml](https://github.com/radargun/radargun/blob/master/extensions/mapreduce/src/main/resources/benchmark-hotrod-spark-mapreduce.xml)** - Shows how to benchmark MapReduce operations on Infinispan coupled with Spark

> Environment variables:
> * *SPARK_161_HOME*	- pointing to Spark home directory
> * *ISPN_82_HOME*	- pointing to Infinispan server home directory

**[benchmark-hotrod-streaming.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-hotrod-streaming.xml)** - Shows how to compare memory usage on Infinispan clients using and not using Streaming API

> Environment variables:
> * *ISPN_9_ZIP_PATH*	- path to Infinispan server distribution file
> * *RG_WORK*		- the directory RadarGun will use to unpack Infinispan server instances to

**[benchmark-iteration.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-iteration.xml)** - Shows how to used`iterate` stage to iterate over all entries in the cache

**[benchmark-jcache.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-jcache.xml)** - Shows how to configure comparative benchmark on two products over JCache

**[benchmark-jgroups.xml](https://github.com/radargun/radargun/blob/master/core/src/main/resources/benchmark-jgroups.xml)** - Shows how to execute JGroups probe queries using `j-groups-probe` stage

**[benchmark-mapreduce.xml](https://github.com/radargun/radargun/blob/master/extensions/mapreduce/src/main/resources/benchmark-mapreduce.xml)** - Shows how to execute mapReduce operations using `map-reduce` stage

**[benchmark-query.xml](https://github.com/radargun/radargun/blob/master/extensions/query/src/main/resources/benchmark-query.xml)** - Shows how to load values from dictionary and then query them using `query` extension

**[benchmark-redis.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-redis.xml)** - Shows how to setup a performance test for Redis

This benchmark requires re-packaged Redis distribution, in order to run it you will have to download and build it according to instruction on [Redis homepage](https://redis.io/), then pack the folder contents (not folder itself) as `redisdistro.zip` and provide path to it as environment variable.

> Environment variables:
> * *REDIS_DISTRO_ZIP_PATH*  - pointing to zip containing built Redis

**[benchmark-rest-cs-tomcat.xml](https://github.com/radargun/radargun/blob/master/extensions/rest/src/main/resources/benchmark-rest-cs-tomcat.xml)** - Shows how to call REST operation on web server using `rest-operations-test` stage

This configurations requires manual intervention in Tomcat installation which is to be used, specifically:

* `conf/tomcat-users.xml` file in the Tomcat home folder has to contain following lines:

{% highlight xml %}
    <role rolename="manager-script"/>
    <user username="tomcat" password="tomcat" roles="manager-script"/>
{% endhighlight %}

* [clusterbench ee7](https://github.com/clusterbench/clusterbench) application has to be deployed as `clusterbench`, follow instructions on the site

> Environment variables:
> * *CATALINA_HOME*	- path to tomcat folder

**[benchmark-stream.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-stream.xml)** - Shows how to execute distributed stream tasks via `stream` stage

**[benchmark-xsite-jmx.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-xsite-jmx.xml)** - Shows how to invoke JMX-exposed methods via `jmx-invocation` stage and how to test cross-site replication via `xs-repl-check` stage

**[benchmark-xsite-repl.xml](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/resources/benchmark-xsite-repl.xml)** - Shows hot to test cross-site replication via `xs-repl-check` stage
