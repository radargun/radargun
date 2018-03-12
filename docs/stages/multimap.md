---
---

Multimap stages
---------------

#### urn:radargun:stages:multimap:3.0

### multimap-cache-operations-test
Test using MiltimapCacheOperations
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> contains-entry-ratio (**optional**) - Ratio of CONTAINS_ENTRY requests. Default is 0.  
> contains-key-ratio (**optional**) - Ratio of CONTAINS_KEY requests. Default is 0.  
> contains-value-ratio (**optional**) - Ratio of CONTAINS_VALUE requests. Default is 0.  
> delay-between-requests (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> entry-size (**optional**) - Size of the value in bytes. Default is 1000.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> get-ratio (**optional**) - Ratio of GET requests. Default is 4.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> key-generator (**optional**) - Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.  
> key-selector (**mandatory**) - Selects which key IDs are used in the test.  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
> merge-thread-stats (**optional**) - Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.  
> multimap-cache-name (**optional**) - MultimapCache name.  
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> num-values-per-key (**optional**) - Defines the number of the values saved under same key. Default is 1.  
> put-ratio (**optional**) - Ratio of PUT requests. Default is 1.  
> ramp-up (**optional**) - Delay to let all threads start executing operations. Default is 0.  
> remove-by-key-value-ratio (**optional**) - Ratio of REMOVE_BY_KEY_VALUE requests. Default is 0.  
> remove-by-predicate-ratio (**optional**) - Ratio of REMOVE_BY_PREDICATE requests. Default is 0.  
> remove-ratio (**optional**) - Ratio of REMOVE requests. Default is 0.  
> repeat-condition (**optional**) - If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> size-ratio (**optional**) - Ratio of SIZE requests. Default is 0.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> synchronous-requests (**optional**) - Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, there is still the random ramp-up delay. Default is false.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values used in the test. By default the generator is retrieved from slave state.  

