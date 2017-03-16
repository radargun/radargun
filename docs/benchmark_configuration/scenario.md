---
---

Scenario
--------

`Scenario` element contains the series of `stages` that will be executed on each [cluster](./clusters.html)-[configuration](./configurations.html) pair. Several base stages are defined on RadarGun core project, most stages are defined by RadarGun extensions they belong to and all are described on their respective pages.  

**Stage element attributes (shared)**
> slaves (**optional**) - specifies slaves the stage should be ran on by index (slave.index property)
> groups (**optional**) - specifies which groups the stage will be ran on by name

#### Scenario design

There are no strict requirements place on the scenario design, however there are some tips and points to be shared to in order to obtain useful results:
* Cluster should be "warmed up" before the actual measurement is started in order to get past small load optimalizations and variable initializations
  * Any stage named **warmup** will not be stored into the results (unique name restrictions do not apply to this keyword)
  * I case of caches warmup should include various keys and valus sizes
* In case of tests on caches including replace operations be aware that keys might not be loaded in the cache, use of appropriate load stage and key-selector might mitigate this if desired