---
---

Mapreduce stages
----------------

#### urn:radargun:stages:mapreduce:3.0

### map-reduce
Stage which executes a MapReduce Task against all keys in the cache.
> collator-fqn (**optional**) - Fully qualified class name of the org.infinispan.distexec.mapreduce.Collator implementation to execute. The default is null.  
> collator-params (**optional**) - A list of key-value pairs in the form of 'methodName:methodParameter' that allows invoking a method on the Collator Object. The method must be public and take a String parameter. The default is null.  
> combiner-fqn (**optional**) - Fully qualified class name of the org.infinispan.distexec.mapreduce.Reducer implementation to use as a combiner.  
> combiner-params (**optional**) - A list of key-value pairs in the form of 'methodName:methodParameter' that allows invoking a method on the Reducer Object used as a combiner. The method must be public and take a String parameter. The default is null.  
> deep-compare-previous-executions (**optional**) - Compare results of previous executions on entry-by-entry basis. WARNING: This can be lengthy operation on data sets with many distinct keys. On false, only result sizes are checked. The default is true.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified workers, groups and roles. Default is all groups.  
> mapper-fqn (**mandatory**) - Fully qualified class name of the mapper implementation to execute.  
> mapper-params (**optional**) - A list of key-value pairs in the form of 'methodName:methodParameter' that allows invoking a method on the Mapper Object. The method must be public and take a String parameter. The default is null.  
> num-executions (**optional**) - The number of times to execute the Map/Reduce task. The default is 10.  
> print-result (**optional**) - Boolean value that determines if the final results of the MapReduceTask are written to the log of the first worker node. The default is false.  
> reducer-fqn (**mandatory**) - Fully qualified class name of the org.infinispan.distexec.mapreduce.Reducer implementation to execute.  
> reducer-params (**optional**) - A list of key-value pairs in the form of 'methodName:methodParameter' that allows invoking a method on the Reducer Object. The method must be public and take a String parameter. The default is null.  
> roles (**optional**) - Specifies on which workers this stage should actively run, by their roles. The result set is intersection of specified workers, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> source-name (**optional**) - Name of the source to execute map-reduce task on. Default value is implementation specific.  
> store-result (**optional**) - Boolean value that determines if the final results of the MapReduceTask are stored in the worker state. The default is false.  
> timeout (**optional**) - A timeout value for the remote communication that happens during a Map/Reduce task. The default is zero which means to wait forever.  
> total-bytes-key (**optional**) - The name of the key in the MainState object that returns the total number of bytes processed by the Map/Reduce task. The default is RandomDataStage.RANDOMDATA_TOTALBYTES_KEY.  
> workers (**optional**) - Specifies on which workers this stage should actively run. The result set is intersection of specified workers, groups and roles. Default is all workers.  

