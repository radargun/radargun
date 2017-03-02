---
---

Stress test
-----------
        
### Introduction
            
This is a benchmark that simulates web session replication within a cluster of web container. On each node, several threads are started. These threads load some entries into the cache and execute requests on the keys.
In next paragraphs we will describe the common use cases and how to configure the stress test to do that. For full description of parameters see [Stages reference]({{page.path_to_root}}architecture/stage_list.html)
Note that StressTest should be preceded by StressTestWarmup to heat up the cache. The warmup should have the same parameters as the following StressTest in order to use the same code paths. After warmup (and before StressTest) the cache should be also cleared with ClearCluster stage.
    
### Entry access pattern
        
#### No conflict on keys

This is the default configuration. Each of 10 stressor threads owns unique set of keys and randomly does requests to these keys. Therefore, no locking conflict can occur. As you have 10 threads, each owning 100 entries, the test uses 1000 entries per node. In a cluster of 4 nodes, the cache will contain 4000 entries.

This test is executed for 5 minutes.
            
    <basic-operations-test test-name="stress-test" duration="5m" num-threads-per-node="10">
        <key-selector>
            <concurrent-keys total-entries="4000"/>
        </key-selector>
    </basic-operations-test>
    
#### Conflicting keys
          
If you want to test the performance if key conflicts occur, simply set sharedKeys to true. Then all numEntries will be accessed by all stressors in the cluster. The same number of entries but with conflicts will be used with this config:
      
    <basic-operations-test test-name="stress-test" duration="5m" num-threads-per-node="10">
        <key-selector>
            <colliding-keys num-entries="4000"/>
        </key-selector>
    </basic-operations-test>   
        
#### Changing keys
            
By default, all the keys are loaded into the cache prior to starting to gather the performance statistics. However, sometimes you don’t want to overwrite the old keys all the time, and want to change the set of accessed keys. Then, you should change the fixedKeys to false.
In this scenario, each entry has a scheduled removal time. The lifespan is controlled by entryLifespan; note that this is only maximal value, the actual lifespan of each entry is random with decreasing probability.
After the stressor thread detects that the lifespan of the entry was exceeded, it removes the entry. The stressor tries to keep the numEntries written by this stressor - if there are less entries (because these have been removed), it adds a new entry. Otherwise, it overwrites one of the entries.
Note that you can’t use sharedKeys=true and fixedKeys=false simultaneously. The configuration itself looks simple:
            
    <StressTest duration="5m" numThreads="10" numEntries="100" fixedKeys="false"/>   
        
### Read/Write/Remove ratio
          
There are two attributes for controlling the ratio of operations executed on the cache. These are writePercentage and removePercentage. There’s no attribute readPercentage - this is simply the complement of the former two operations.
For executing 30 % writes, 20 % removes and 50 % reads use
      
    <StressTest duration="5m" writePercentage="30" removePercentage="20" />    
        
### Transactions

RadarGun supports transactions - you can find more info about that in [Benchmarking JTA performance]({{page.path_to_root}}other_docs/benchmarking_jta_performance.html)article.
By default, StressTest checks if the cache wrapper has transactions configured in and if it has, uses transactions (otherwise it does not). You can force the behaviour by specifying useTransactions attribute.
The number of operations in single transaction is controlled by transactionSize attribute. Another attribute commitTransactions specifies whether the transactions should be committed or rolled back.
            
Example:         
              
    <StressTest duration="5m" useTransactions="true" transactionSize="10" />    
        
### Atomic (conditional) operations
          
When the used cache wrapper (product, plugin) supports the AtomicOperationsCapable feature you may test these conditional operations as well. The supported ones are conditional replace, putIfAbsent and conditional remove. You may start testing such operations by setting useAtomics to true.
However, the performance of these operation may be dependent on the operations outcome. Therefore, the stressor keeps the current values in the cache (this may double memory requirements!) and we add two attributes removeInvalidPercentage and replaceInvalidPercentage. Then, the operation logic is as follows:
* If there’s null in the cache, the stressor always executes putIfAbsent (and this is successful)

Otherwise:
* in writePercentage cases putIfAbsent is executed (but this should fail)
* in removePercentage cases remove with correct conditional value is executed
* in removeInvalidPercentage cases the stressor executes remove with random value (most probably not the correct one)
* in replaceInvalidPercentage cases it executes replace with random value
* in rest of the cases it executes correct replace operation

As you may have noted, get operations are not executed in this settings. Also keep in mind that sharedKeys are not supported for atomic operation (as the stressor cannot know what’s the current value).
Here is an example configuration:

    <StressTest duration="5m" useAtomics="true" writePercentage="10" removePercentage="20" removeInvalidPercentage="10" replaceInvalidPercentage="10" />
       
You might think that there are 50 % of valid replace operations, but in fact 20 % of operations end up with entry having null value. Therefore, this leads to equation p = (1 - p) * 0.2 ⇒ there will be p = 16.6 % successful putIfAbsent operations and removes, 8.3 % of invalid removes, replaces or putIfAbsents, and 41.6 % of valid replaces.
    
### Bulk operations
          
The cache wrapper can support another type of operations, called bulk operations. Increasing bulkSize makes the stressor use requests which send multiple writes/reads in single operation.
Such bulk operation may be implemented by multiple asynchronous requests (waiting until all of them complete). By default the cache wrapper should try to execute the native bulk operations, with preferAsyncOperations set to true it should use the asynchronous ones.
            
    <StressTest duration="5m" bulkSize="10" preferAsyncOperations="false" />

### Used entries
        
By default RadarGun uses random 1000-byte arrays as values for the entries. The length can be modified by setting the entrySize (right, it should be rather called valueSize). Multiple sizes of entries are supported as well - for 1:4 ratio of 1kB and 2kB values you may specify

    <StressTest duration="5m" entrySize="1: 1024, 4: 2048" />
 
You may use percents as well, for example for 20 % of 1kB entries, 30 % of 2kB entries and the rest (50 %) of 4kB entries you should specify
 
    <StressTest duration="5m" entrySize="20%: 1024, 30%: 2048, 1: 4096" />
    
When specifying percents, it’s recommended to use the form with one entry without percentage specification (above the 1 holds for weight ratio from the rest). This is because of imprecise floating point arithmetic involved which could result in 50 % + 50 % = 100.0000001 %.

If useAtomics is set to true, plain byte arrays cannot be used (equals() method does not compare two arrays with identical content to be identical), therefore, the value is a thin wrapper for these arrays. This may not be of importance but it still carries some additional memory overhead for each entry.     
        
For multiple entry sizes it may be convenient to specify the total sum of all values rather than just number of entries and their size. This is handled by the numBytes attribute which computes the necessary number of entries automatically. Warning: currently this is implemented only for fixedKeys="false".

There are two more attributes for changing of the key format - keyGeneratorClass and keyGeneratorParam. The default key generator transforms key ids into strings in format key_%016X - the %016X means zero-padded 16-character long number in hexadecimal format. You can alter this format with the keyGeneratorParam attribute.

Another keyGeneratorClass available is org.radargun.stressors.CargoKeyGenerator. This provides objects with the key id and random byte array with length specified by keyGeneratorParam attribute. Simpler version org.radargun.stressors.ObjectKeyGenerator holds only the key id (no byte array cargo included).
        
### Gathering statistics and results
        
In order to provide results, StressTest must be eventually followed by [CsvReportGeneration](https://github.com/radargun/radargun/wiki/Stages#csvreportgeneration) stage. Then, after all benchmarks finish you’ll find the report in the results directory (unless you specify otherwise). You can find more about the generated CSV files in [Understanding StressTest results]({{page.path_to_root}}measuring_performance/measuring_performance_rg2.html).
        
        
With default settings, the results are provided as single set of metrics - for one StressTest stage you get one average latency/throughput value.
            
If you want to see the histogram of requests' latencies, the setup is a bit more complicated. You have to run a short (e.g. 1 minute) stress test first where you gather a sample of the latencies. Results from this stage are not published anywhere, but the histograms initialize its buckets from these data.
            
Then you should clear up the cluster and start the real test stage.
      
    <StressTest duration="1m" generateHistogramRange="true"/>
    <ClearCluster />
    <StressTest duration="5m" useHistogramStatistics="true"/>
      
In other setup you’d like to see if the latency/throughput changes during the test execution. For this case you should setup the statisticsPeriod. This will provide results for each period separately (you can find the values plotted in the report). Note that there may be the "last period" with few results - you may ignore this value (eventually the code should be fixed to detect this).
    
    <StressTest duration="5m" statisticsPeriod="1m"/>
          
        
