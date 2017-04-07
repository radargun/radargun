---
---

Scenario
--------

`Scenario` element contains the series of `stages` that will be executed on each [cluster](./clusters.html)-[configuration](./configurations.html) pair. Several base stages are defined on RadarGun core project, most stages are defined by RadarGun extensions they belong to and all are described on their respective pages.  

**Stage** element attributes (shared)
> slaves (**optional**) - specifies slaves the stage should be ran on by index (slave.index property)
> groups (**optional**) - specifies which groups the stage will be ran on by name


#### Example scenario

{% highlight xml %}
    <rg:scenario xmlns:rg="urn:radargun:benchmark:3.0"
      xmlns="urn:radargun:stages:core:3.0"
      xmlns:cache="urn:radargun:stages:cache:3.0">

      <service-start />

      <jvm-monitor-start />

      <cache:load num-entries="10000"/>
      
      <cache:basic-operations-test test-name="warmup" num-operations="100000" num-threads-per-node="5">
        <cache:key-selector>
          <cache:concurrent-keys total-entries="5000" />
        </cache:key-selector>
      </cache:basic-operations-test>
      
      <repeat from="10" to="30" inc="10">
        <cache:basic-operations-test test-name="stress-test" amend-test="true"
          duration="1m" num-threads-per-node="${repeat.counter}">
          <cache:key-selector>
            <cache:concurrent-keys total-entries="10000"/>
          </cache:key-selector>
          <statistics>
            <common/>
          </statistics>
        </cache:basic-operations-test>
      </repeat>

      <jvm-monitor-stop />

    </rg:scenario>
{% endhighlight %}

This is a rather basic scenario example rigged to test basic operations performance of cache.  

* **service-start**		- starts assigned services on all slaves (can specify groups, slaves, delays etc. allowing for specific starting procedure)
* **jvm-monitor-start**		- starts JVM monitor threads which monitor various JVM attributes (CPU usage, memory consumption etc.) on all slaves (including managed servers if plugin provides JMX connection)
* **load**			- loads cache with 10000 entries, used to make sure all keys are loaded to cache
* **basic-operations-test**	- the name removes any ambiguity about this stages purpose, it runs basic operations agains the cache, ratio of operations is configurable
  * **test-name** 		- test is named "warmup" so it will not store test results as to not influence final results, this is done to "warm up" the cluster
  * **num-operations**		- test will finish after 100000 operations
  * **key-selector** 		- test specifies [key-selector](./key_selectors.html) which will be selecting keys to use for the cache, limiting key range to 5000 in this case. This overrides default `key-selector`
* **repeat**			- this is not stage unto itself but RG equivalent of the "for" cycle
  * enclosed stages will be repeated for as many times as it takes to reach `to` value by adding `inc` value to the `from` value (so 3 times in this case)
  * current value can be accessed via `repeat.counter` [property](./properties.html).
* **basic-operations-test**	- this is the core test, it will be ran 3 times with increasing number of threads per node each itteration
  * **amend-test** 		- this property will make sure all itterations of the test are appended in one list (as opposed to being stored as separate tests), making analysis easier
  * **duration**		- test will run for 1 minute
  * **key-selector**		-  [key-selector](./key_selectors.html)  for this test will be choosing keys from 10000 values
  * **statistics**		- specifies which statistics will be stored for reporting
* **jvm-monitor-stop**		- stops JVM monitor threads (if not called JVM monitors are stopped after final stage)


#### Scenario design

There are no strict requirements placed on the scenario design, however there are some tips and points to be shared in order to obtain useful results:
* Cluster should be "warmed up" before the actual measurement is started in order to get past small load optimalizations and variable initializations
  * Any stage named **warmup** will not be stored into the results (unique name restrictions do not apply to this keyword)
  * I case of caches warmup should include various keys and value sizes
* Most "actual" test stages allow specifying [key-selector](./key_selectors.html), [key-generator](./key_generators.html) and  [value generator](./value_generators.html)  
* In case of tests on caches including replace operations be aware that keys might not be loaded in the cache, use of appropriate load stage and  [key-selector](./key_selectors.html)  might mitigate this if desired