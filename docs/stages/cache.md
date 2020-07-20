---
---

Cache stages
------------

#### urn:radargun:stages:cache:3.0

### background-load-start
Allows to load data into a cache in the background, while other stages may take place. To force process termination, use BackgroundLoadDataStopStage.
> batch-size (**optional**) - Size of batch to be loaded into cache (using putAll). If <= 0, put() operation is used sequentially.  
> cache-selector (**optional**) - Selects which caches will be loaded. Default is the default cache.  
> entry-size (**optional**) - Size of the value in bytes. Default is 1000.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> key-generator (**optional**) - Generator of keys (transforms key ID into key object). Default is 'string'.  
> key-id-offset (**optional**) - Initial key ID used for numbering the keys. Default is 0.  
> load-all-keys (**optional**) - This option forces local loading of all keys on all slaves in this group (not only numEntries/numNodes). Default is false.  
> log-period (**optional**) - Number of loaded entries after which a log entry should be written. Default is 10000.  
> max-load-attempts (**optional**) - During loading phase, if the insert fails, try it again. This is the maximum number of attempts. Default is 10.  
> num-entries (**optional**) - Total number of key-value entries that should be loaded into cache. Default is 100.  
> num-threads (**optional**) - The number of threads that should load the entries on one slave. Default is 10.  
> remove (**optional**) - If set to true, the entries are removed instead of being inserted. Default is false.  
> request-period (**optional**) - Target period of put operations - e.g. when this is set to 10 msthe benchmark will try to do one put operation every 10 ms. By default the requests are executed at maximum speed.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> seed (**optional**) - Seed used for initialization of random generators - with same seed (and other arguments), the stage guarantees same entries added to the cache. By default the seed is not set.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> transaction-size (**optional**) - Numbers of entries loaded in one transaction. Default is to not use transactions.  
> use-async-batch-loading (**optional**) - Controls whether batch insertion is performed in asychronous way. Default is false (prefer synchronous operations).  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values. Default is byte-array.  
> wait-on-error (**optional**) - When an attempt to load an entry fails, wait this period to reduce the chances of failing again. Default is one second.  

### background-load-stop
Stops data loading process started by BackgroundLoadDataStartStage.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> timeout-duration (**optional**) - Maximum time to wait for loading threads to finish. By default, wait until the threads finish their job.  

### background-statistics-start
Starts collection of statistics from background threads and cache size.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> name (**optional**) - Name of the background operations. Default is 'Default'.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> stats-iteration-duration (**optional**) - Delay between statistics snapshots. Default is 5 seconds.  

### background-statistics-stop
Stop Statistics and return collected statistics to master.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> name (**optional**) - Name of the background operations. Default is 'Default'.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> test-name (**optional**) - Name of the test used for reports. Default is 'BackgroundStats'.  

### background-stressors-check
Stage that checks the progress in background stressors and fails if something went wrong.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> name (**optional**) - Name of the background operations. By default, all instances are checked.  
> resume-after-checked (**optional**) - Resume stressors after we have stopped them in order to let checkers check everything. Default is false.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> wait-for-progress (**optional**) - Waits until all stressors record new progress, or timeout (no-progress-timeout) elapses. Default is false.  
> wait-until-checked (**optional**) - Stops stressors and waits until all confirmed operations are checked. Default is false.  

### background-stressors-start
Starts background stressor threads.
> cache-name (**optional**) - Cache used for the background operations. Default is null (default).  
> dead-slave-timeout (**optional**) - Period after which a slave is considered to be dead. Default is 90 s.  
> delay-between-requests (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> entry-size (**optional**) - Size of value used in the entry. Default is 1024 bytes.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> gets (**optional**) - Ratio of GET requests. Default is 2.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> key-id-offset (**optional**) - First key ID used for key generation. Default is 0.  
> load-data-for-dead-slaves (**optional**) - List of slaves whose data should be loaded by other threads because these slaves are not alive. Default is empty.  
> load-data-on-slaves (**optional**) - List of slaves where the data should be loaded (others immediately start executing requests). Default is all live slaves).  
> load-only (**optional**) - If set to true, the stressor does not execute any requests after loading the data. Default is false.  
> load-with-put-if-absent (**optional**) - Use conditional putIfAbsent instead of simple put for loading the keys. Default is false.  
> log-logic-check-delayed-remove-expected-value (**optional**) - Check whether the value that is being removed matches the expected value. In failure scenarios, this may cause incorrect test failures. Default is true.  
> log-logic-check-notifications (**optional**) - Check that listeners have been fired for each operation on each node (at least once). Default is false.  
> log-logic-checking-threads (**optional**) - Number of threads on each node that are checking whether all operations from stressor threads have been logged. Default is 10.  
> log-logic-counter-update-period (**optional**) - Number of operations after which will the stressor or checker update in-cache operation counter. Default is 50.  
> log-logic-debug-failures (**optional**) - Debug a key if a missing operation or notification is detected. Service needs to provide an implementation of Debuggable iterface.  
> log-logic-enabled (**optional**) - Use values which trace all operation on these keys. Therefore, they're always growing. Default is false.  
> log-logic-ignore-dead-checkers (**optional**) - When the log value is full, the stressor needs to wait until all checkers confirm that the records have been checked before discarding oldest records. With ignoreDeadCheckers=true the stressor does not wait for checkers on dead nodes. Default is false.  
> log-logic-max-delayed-remove-attempts (**optional**) - Maximum number of attempts to perform delayed removes when using transactions (as removes are performed in a separate TX,which can fail independently of TX performing PUT operations). If the value is negative, number of attempts is unlimited. Default is -1.  
> log-logic-max-transaction-attempts (**optional**) - Maximum number of attempts to perform transaction. If the value is negative, number of attempts is unlimited. Default is -1.  
> log-logic-no-progress-timeout (**optional**) - Maximum time for which are the log value checkers allowed to show no new checked values, when waiting for checks to complete or stressors to confirm new progress. Default is 10 minutes.  
> log-logic-value-max-size (**optional**) - Maximum number of records in one entry before the older ones have to be truncated. Default is 100.  
> log-logic-write-apply-max-delay (**optional**) - Maximum allowed delay to detect operation confirmed by stressor. Default is no delay.  
> name (**optional**) - Name of the background operations. Default is 'Default'.  
> no-loading (**optional**) - Do not execute the loading, start usual request right away.  
> num-entries (**optional**) - Number of entries (key-value pairs) inserted into the cache. Default is 1024. Needs to be greater than or equal to the product of 'numThreads' and group size.  
> num-threads (**optional**) - Number of stressor threads. Default is 10.  
> put-with-replace (**optional**) - Use replace operations instead of puts during the test. Default is false.  
> puts (**optional**) - Ratio of PUT requests. Default is 1.  
> removes (**optional**) - Ratio of REMOVE requests. Default is 0.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> shared-keys (**optional**) - By default each thread accesses only its private set of keys. This allows all threads all values. Atomic operations are required for this functionality. Default is false.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> transaction-size (**optional**) - Number of requests wrapped into single transaction. By default transactions are not used (explicitly).  
> value-generator (**optional**) - Generator of values. Default is byte-array.  
> wait-until-loaded (**optional**) - Specifies whether the stage should wait until the entries are loaded by stressor threads. Default is true.  

### background-stressors-stop
Stop BackgroundStressors.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> name (**optional**) - Name of the background operations. Default is 'Default'.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### basic-operations-test
Test using BasicOperations
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> contains-ratio (**optional**) - Ratio of CONTAINS requests. Default is 0.  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to have a compensate for CO  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> entry-size (**optional**) - Size of the value in bytes. Default is 1000.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> get-and-put-ratio (**optional**) - Ratio of GET_AND_PUT requests. Default is 0.  
> get-and-remove-ratio (**optional**) - Ratio of GET_AND_REMOVE requests. Default is 0.  
> get-ratio (**optional**) - Ratio of GET requests. Default is 4.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> key-generator (**optional**) - Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.  
> key-selector (**mandatory**) - Selects which key IDs are used in the test.  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
> merge-thread-stats (**optional**) - Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.  
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> put-ratio (**optional**) - Ratio of PUT requests. Default is 1.  
> ramp-up (**optional**) - Delay to let all threads start executing operations. Default is 0.  
> remove-ratio (**optional**) - Ratio of REMOVE requests. Default is 0.  
> repeat-condition (**optional**) - If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.  
> report-latency-as-service-time (**optional**) - Enable this property in order to show the difference between latency and service.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> synchronous-requests (**optional**) - Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, there is still the random ramp-up delay. Default is false.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> think-time (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values used in the test. By default the generator is retrieved from slave state.  

### bulk-operations-test
Executes operations from BulkOperations trait.
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> bulk-size (**optional**) - Number of keys inserted/retrieved within one operation. Applicable only when the cache wrapper supports bulk operations. Default is 10.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to have a compensate for CO  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> entry-size (**optional**) - Size of the value in bytes. Default is 1000.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> get-all-async-ratio (**optional**) - Ratio of GET_ALL_ASYNC requests. Default is 0.  
> get-all-native-ratio (**optional**) - Ratio of GET_ALL_NATIVE requests. Default is 4.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> key-generator (**optional**) - Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.  
> key-selector (**mandatory**) - Selects which key IDs are used in the test.  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
> merge-thread-stats (**optional**) - Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.  
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> put-all-async-ratio (**optional**) - Ratio of PUT_ALL_ASYNC requests. Default is 0.  
> put-all-native-ratio (**optional**) - Ratio of PUT_ALL_NATIVE requests. Default is 1.  
> ramp-up (**optional**) - Delay to let all threads start executing operations. Default is 0.  
> remove-all-async-ratio (**optional**) - Ratio of REMOVE_ALL_ASYNC requests. Default is 0.  
> remove-all-native-ratio (**optional**) - Ratio of REMOVE_ALL_NATIVE requests. Default is 0.  
> repeat-condition (**optional**) - If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.  
> report-latency-as-service-time (**optional**) - Enable this property in order to show the difference between latency and service.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> synchronous-requests (**optional**) - Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, there is still the random ramp-up delay. Default is false.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> think-time (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values used in the test. By default the generator is retrieved from slave state.  

### check-cache-data
Stage for checking presence or absence of data entered in other stages.
> cache-selector (**optional**) - Selects which caches will be loaded. Default is the default cache.  
> check-entry-count (**optional**) - Number of entries that will be checked in each step. Default is 1.  
> check-subparts-are-replicas (**optional**) - Check that number of non-zero subparts is equal to number of replicas. Default is false.  
> check-subparts-equal (**optional**) - Check whether the same subparts from each cache have the same size. Default is false.  
> check-subparts-sum-local (**optional**) - Check whether the sum of subparts sizes is the same as local size. Default is false.  
> check-threads (**optional**) - Number of thread per node which check data validity. Default is 1.  
> debug-null (**optional**) - If the GET request results in null response, call wrapper-specific functions to show debug info. Default is false.  
> deleted (**optional**) - If set to true, we are checking that the data are NOT in the cluster anymore. Default is false.  
> entry-size (**mandatory**) - Number of bytes carried in single entry.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> extra-entries (**optional**) - Entries that do not have the expected form but occur in the cluster. This string specifies a polynomial in number of slaves: 1,2,3 with 4 slaves would result in 1 + 2*4 + 3*4*4 = 57 extra entries.Defaults to 0.  
> fail-on-null (**optional**) - If entry is null, fail immediately. Default is false.  
> first-entry-offset (**optional**) - Index of key of the first entry.  
> first-entry-offset-slave-index (**optional**) - Index of key of the first entry. This number will be multiplied by slaveIndex. Default is 0. Has precedence over 'first-entry-offset'.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> ignore-sum (**optional**) - Usually the test checks that sum of local nodes = numOwners * numEntries + extraEntries.This option disables such behaviour. Default is false.  
> key-generator (**optional**) - Generator of keys (transforms key ID into key object). By default the generator is retrieved from slave state.  
> live-slaves-hint (**optional**) - Hint how many slaves are currently alive - if set to > 0 then the query for number of entries in this cache is postponed until the cache appears to be fully replicated. By default this is disabled.  
> log-checks-count (**optional**) - Number of queries after which a DEBUG log message is printed. Default is 10000.  
> memory-only (**optional**) - If the cache wrapper supports persistent storage and this is set to true, the check will be executed only against in-memory data. Default is false.  
> num-entries (**mandatory**) - Number of entries with key in form specified by the last used key generator, in the cache.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> size-only (**optional**) - If true, the entries are not retrieved, this stage only checks that the sum of entries from local nodes is correct. Default is false.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> step-entry-count (**optional**) - Number of entries stepped in each step. Default is 1.  
> value-generator (**optional**) - Generator of values. By default the generator is retrieved from slave state.  

### check-topology
Controls which topology events have (not) happened recently
> cache-name (**optional**) - Name of the cache. Default is the default cache.  
> changed (**optional**) - The check controls if this event has happened (true) or not happened (false). Defaults to true.  
> check-events (**optional**) - Type of events to check in this stage. Default are TOPOLOGY, REHASH, CACHE_STATUS (see org.radargun.traits.TopologyHistory.HistoryType).  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> period (**optional**) - The period in milliseconds which is checked. Default is infinite.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### clear
Removes all data from the cache
> cache-name (**optional**) - Name of the cache to be cleared. Default is the default cache.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> local (**optional**) - Execute local variant of clear on each slave. Default is null - local clear is performed, only if it is provided by the service. True enforces local clear - if given service does not provide the feature, exception is thrown.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> use-transaction (**optional**) - Execute the clear inside explicit transaction.  

### clear-cache
Removes all data from the cache
> cache-name (**optional**) - Name of the cache to be cleared. Default is the default cache.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> local (**optional**) - Execute local variant of clear on each slave. Default is null - local clear is performed, only if it is provided by the service. True enforces local clear - if given service does not provide the feature, exception is thrown.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> use-transaction (**optional**) - Execute the clear inside explicit transaction.  

### cluster-validation
Verifies that the cluster is formed by injecting an entry into the cache and then reading it from other nodes.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> partial-replication (**optional**) - If set to true, then the slave will consider that the cluster is formed when one slave replicated the control entry. Otherwise the replication will only be considered successful if all slaves replicated the control value. Default is false.  
> replication-time-sleep (**optional**) - Delay between attempts to retrieve the control entry.  
> replication-try-count (**optional**) - How many times we should try to retrieve the control entry.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### conditional-operations-test
Tests (atomic) conditional operations. Note that there is no put-if-absent-ratio- this operation is executed anytime the selected key does not have value.
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to have a compensate for CO  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> entry-size (**optional**) - Size of the value in bytes. Default is 1000.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> get-and-replace-ratio (**optional**) - Ratio of GET_AND_REPLACE requests. Default is 1.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> key-generator (**optional**) - Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.  
> key-selector (**mandatory**) - Selects which key IDs are used in the test.  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
> match-percentage (**optional**) - Percentage of requests in which the condition should be satisfied. Default is 50%.  
> merge-thread-stats (**optional**) - Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.  
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> ramp-up (**optional**) - Delay to let all threads start executing operations. Default is 0.  
> remove-ratio (**optional**) - Ratio of REMOVE requests. Default is 1.  
> repeat-condition (**optional**) - If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.  
> replace-any-ratio (**optional**) - Ratio of REPLACE_ANY requests. Default is 1.  
> replace-ratio (**optional**) - Ratio of REPLACE requests. Default is 1.  
> report-latency-as-service-time (**optional**) - Enable this property in order to show the difference between latency and service.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> synchronous-requests (**optional**) - Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, there is still the random ramp-up delay. Default is false.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> think-time (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values used in the test. By default the generator is retrieved from slave state.  

### distributed-task
Stage which executes a MapReduce Task against all keys in the cache.
> callable (**mandatory**) - Fully qualified class name of the java.util.concurrent.Callable implementation to execute.  
> callable-params (**optional**) - A list of key-value pairs in the form of 'methodName:methodParameter;methodName1:methodParameter1' that allows invoking a method on the callable. The method must be public and take a String parameter. Default is none.  
> execution-policy (**optional**) - The name of the execution policy. The default is default policy of the service.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> failover-policy (**optional**) - The name of the failover policy. The default is default policy of the service.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> node-address (**optional**) - The node address where the task will be executed. The default is null, and tasks will be executed against all nodes in the cluster.  
> num-executions (**optional**) - The number of times to execute the Callable. The default is 1.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> total-bytes-key (**optional**) - The name of the key in the MasterState object that returns the total number of bytes processed by the Callable. The default is RandomDataStage.RANDOMDATA_TOTALBYTES_KEY.  

### isolation-level-check
Stage for testing guaranties of isolation levels.
> duration (**optional**) - How long should this stage take. Default is 1 minute.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> expected-level (**mandatory**) - Expected isolation level (should match to cache configuration). Supported values are [READ_COMMITTED, REPEATABLE_READ].  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> readers (**optional**) - Number of concurrent threads that try to retrieve the value. Default is 10.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> transaction-size (**optional**) - Number of reads executed inside on transaction. Default is 30.  
> writers (**optional**) - Number of concurrent threads that modify the value. Default is 2.  

### iterate
Iterates through all entries.
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> container-name (**optional**) - Name of the container (e.g. cache, DB table etc.) that should be iterated. Default is the default container.  
> converter-class (**optional**) - Full class name of the converter. Default is no converter (Map.Entry<K, V> is returned).  
> converter-param (**optional**) - Parameter for the converter (used to resolve its properties). No defaults.  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to have a compensate for CO  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> fail-on-failed-iteration (**optional**) - Fail the stage if some of the stressors has failed. Default is true.  
> fail-on-not-total-size (**optional**) - Fail when the number of elements is different than total size. Default is true if filter is not defined and false otherwise.  
> fail-on-uneven-elements (**optional**) - Fail when the number of elements iterated is not same. Default is true.  
> filter-class (**optional**) - Full class name of the filter used to iterate through entries. Default is none (accept all).  
> filter-param (**optional**) - Parameters for the filter (used to resolve its properties). No defaults.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
> max-next-failures (**optional**) - Number of next() calls that are allowed to fail until we break the loop. Default is 100.  
> merge-thread-stats (**optional**) - Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.  
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> ramp-up (**optional**) - Delay to let all threads start executing operations. Default is 0.  
> repeat-condition (**optional**) - If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.  
> report-latency-as-service-time (**optional**) - Enable this property in order to show the difference between latency and service.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> synchronous-requests (**optional**) - Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, there is still the random ramp-up delay. Default is false.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> think-time (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  

### key-expiration-test
During execution, keys expire (entries are removed from the cache) and new keys are used.
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to have a compensate for CO  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> entry-lifespan (**optional**) - With fixedKeys=false, maximum lifespan of an entry. Default is 1 hour.  
> entry-size (**optional**) - Size of the value in bytes. Default is 1000.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> expect-lost-keys (**optional**) - Due to configuration (eviction, expiration), some keys may spuriously disappear. Do not issue a warning for this situation. Default is false.  
> get-ratio (**optional**) - Ratio of GET requests. Default is 4.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> key-generator (**optional**) - Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
> merge-thread-stats (**optional**) - Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.  
> num-bytes-per-thread (**optional**) - Maximum number of bytes in entries' values stored in the cache by one stressor thread at one moment.  
> num-entries-per-thread (**optional**) - Maximum number of entries stored in the cache by one stressor thread at one moment.  
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> put-ratio (**optional**) - Ratio of PUT requests. Default is 1.  
> ramp-up (**optional**) - Delay to let all threads start executing operations. Default is 0.  
> repeat-condition (**optional**) - If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.  
> report-latency-as-service-time (**optional**) - Enable this property in order to show the difference between latency and service.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> synchronous-requests (**optional**) - Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, there is still the random ramp-up delay. Default is false.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> think-time (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values used in the test. By default the generator is retrieved from slave state.  

### load
Loads data into the cache using specified generators.
> batch-size (**optional**) - Size of batch to be loaded into cache (using putAll). If <= 0, put() operation is used sequentially.  
> cache-selector (**optional**) - Selects which caches will be loaded. Default is the default cache.  
> entry-size (**optional**) - Size of the value in bytes. Default is 1000.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> key-generator (**optional**) - Generator of keys (transforms key ID into key object). Default is 'string'.  
> key-id-offset (**optional**) - Initial key ID used for numbering the keys. Default is 0.  
> load-all-keys (**optional**) - This option forces local loading of all keys on all slaves in this group (not only numEntries/numNodes). Default is false.  
> log-period (**optional**) - Number of loaded entries after which a log entry should be written. Default is 10000.  
> max-load-attempts (**optional**) - During loading phase, if the insert fails, try it again. This is the maximum number of attempts. Default is 10.  
> num-entries (**optional**) - Total number of key-value entries that should be loaded into cache. Default is 100.  
> num-threads (**optional**) - The number of threads that should load the entries on one slave. Default is 10.  
> remove (**optional**) - If set to true, the entries are removed instead of being inserted. Default is false.  
> request-period (**optional**) - Target period of put operations - e.g. when this is set to 10 msthe benchmark will try to do one put operation every 10 ms. By default the requests are executed at maximum speed.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> seed (**optional**) - Seed used for initialization of random generators - with same seed (and other arguments), the stage guarantees same entries added to the cache. By default the seed is not set.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> transaction-size (**optional**) - Numbers of entries loaded in one transaction. Default is to not use transactions.  
> use-async-batch-loading (**optional**) - Controls whether batch insertion is performed in asychronous way. Default is false (prefer synchronous operations).  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values. Default is byte-array.  
> wait-on-error (**optional**) - When an attempt to load an entry fails, wait this period to reduce the chances of failing again. Default is one second.  

### load-file
Loads the contents of a file into the cache.
> bucket (**optional**) - The name of the bucket where keys are written. The default is null.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> file-path (**mandatory**) - Full pathname to the file.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> print-write-statistics (**optional**) - If true, then the time for each put operation is written to the logs. The default is false.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> string-data (**optional**) - If true, then String objects are written to the cache. The default is false.  
> value-size (**optional**) - The size of the values to put into the cache from the contents of the file. The default size is 1MB (1024 * 1024).  

### random-data
Generates random data to fill the cache.
> batch-size (**optional**) - Size of batch to be loaded into cache (using putAll). If <= 0, put() operation is used sequentially. Default is 0.  
> bucket (**optional**) - The name of the bucket where keys are written. The default is null.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> limit-word-count (**optional**) - If true, then the random word generator selects a word from a pre-defined list. The default is false.  
> max-sleep-interval (**optional**) - The maximum number of seconds to sleep before retrying a failed put command. The default is 5.  
> max-word-count (**optional**) - The maximum number of words to generate in the pre-defined list of words used with limitWordCount.The default is 100.  
> max-word-length (**optional**) - The maximum number of characters allowed in a word. The default is 20.  
> print-write-statistics (**optional**) - If true, then the time for each put operation is written to the logs. The default is false.  
> put-retry-count (**optional**) - The number of times to retry a put if it fails. Default is 10.  
> ram-percentage (**optional**) - A double that represents the percentage of the total Java heap used to determine the amount of data to put into the cache. Either valueCount or ramPercentageDataSize should be specified, but not both.  
> random-seed (**optional**) - The seed to use for the java.util.Random object. The default is the return value of Calendar.getInstance().getWeekYear().  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> share-words (**optional**) - If false, then each node in the cluster generates a list of maxWordCount words. If true, then each node in the cluster shares the same list of words. The default is false.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> string-data (**optional**) - If true, then String objects with printable characters are written to the cache.The default is false  
> target-memory-use (**optional**) - The number of bytes to write to the cache when the valueByteOverhead, stringData, and valueSize are taken into account. The code assumes this is an even multiple of valueSize plus valueByteOverhead. If stringData is true, then the code assumes this is an even multiple of (2 * valueSize) plus valueByteOverhead.  
> use-async-batch-loading (**optional**) - Controls whether batch insertion is performed in asychronous way. Default is false (prefer synchronous operations).  
> value-byte-overhead (**optional**) - The bytes used over the size of the key and value when putting to the cache. By default the stage retrieves the value from cache wrapper automatically.  
> value-count (**optional**) - The number of values of valueSize to write to the cache. Either valueCount or ramPercentageDataSize should be specified, but not both.  
> value-size (**optional**) - The size of the values to put into the cache. The default size is 1MB (1024 * 1024).  

### register-listeners
Benchmark operations performance where cluster listenersTrait are enabled or disabled.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> register-listeners (**optional**) - Before stress stage, cluster listeners would be enabled. This is flag to turn them on. Default is false.  
> reset-stats (**optional**) - Allows to reset statistics at the begining of the stage. Default is false.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> sleep-time (**optional**) - Use sleep time to simulate some work on listener. Default is -1(do not sleep) ms.  
> sync (**optional**) - Setup if cache listener is synchronous/asynchronous. Default is true  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> unregister-listeners (**optional**) - Before stress stage, cluster listeners would be disabled. This is flag to turn them off. Default is false.  

### single-tx-check
Paired with SingleTXLoadStage. Checks that the previous stage had the expected result
> commit-slave (**optional**) - Indices of slaves which should have committed the transaction (others rolled back). Default is all committed.  
> commit-thread (**optional**) - Indices of threads which should have committed the transaction (others rolled back). Default is all committed.  
> deleted (**optional**) - If this is set to true, REMOVE operation should have been executed. Default is false.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> transaction-size (**optional**) - Expected size of the transcation.  

### single-tx-load
Performs single transaction with requests on key0 .. keyN in multiple threads on multiple slaves.
> commit-slave (**optional**) - Indices of slaves which should commit the transaction (others will rollback). Default is all commit.  
> commit-thread (**optional**) - Indices of threads which should commit the transaction (others will rollback). Default is all commit.  
> delete (**optional**) - The threads by default do the PUT request, if this is set to true they will do REMOVE. Default is false.  
> duration (**optional**) - The enforced duration of the transaction. If > 0 the threads will sleep for duration/transactionSize after each request. Default is 0.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> threads (**optional**) - Number of threads that should execute the transaction. Default is 1.  
> transaction-size (**optional**) - Number of request in the transaction. Default is 20.  

### stream
Stage which executes a specified stream Task against all keys in the cache.
> cache-name (**optional**) - Name of the cache where stream task should be executed. Default is the default cache.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> num-executions (**optional**) - The number of times to execute the stream task. The default is 10.  
> parallel-stream (**optional**) - Boolean value that determines if the parallelStream is used   
> print-result (**optional**) - Boolean value that determines if the final results of the stream are written to the log of the first slave node. The default is false.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'dataOperation' statistics (fixed size memory footprint for each operation).  
> stream-operation-class (**mandatory**) - Fully qualified class name of the StreamConsumer implementation.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Stream_Stage'.  
> total-bytes-key (**optional**) - The name of the key in the MasterState object that returns the total number of bytes processed by the stream task. The default is RandomDataStage.RANDOMDATA_TOTALBYTES_KEY.  

### streaming-operations-test
Streaming operations test stage
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> buffer-size (**optional**) - Streaming operations buffer size in bytes, default is 100  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to have a compensate for CO  
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
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> put-ratio (**optional**) - Ratio of PUT requests. Default is 1.  
> ramp-up (**optional**) - Delay to let all threads start executing operations. Default is 0.  
> repeat-condition (**optional**) - If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.  
> report-latency-as-service-time (**optional**) - Enable this property in order to show the difference between latency and service.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> synchronous-requests (**optional**) - Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, there is still the random ramp-up delay. Default is false.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> think-time (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values used in the test. By default the generator is retrieved from slave state.  

### temporal-operations-test
Test using TemporalOperations
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to have a compensate for CO  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> entry-size (**optional**) - Size of the value in bytes. Default is 1000.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> get-and-put-with-lifespan-and-max-idle-ratio (**optional**) - Ratio of GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE requests. Default is 0.  
> get-and-put-with-lifespan-ratio (**optional**) - Ratio of GET_AND_PUT_WITH_LIFESPAN requests. Default is 0.  
> get-ratio (**optional**) - Ratio of GET requests. Default is 4.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> key-generator (**optional**) - Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.  
> key-selector (**mandatory**) - Selects which key IDs are used in the test.  
> lifespan (**optional**) - Lifespan to be used for all temporal operations. Default is 1000 ms.  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
> max-idle (**optional**) - MaxIdle time to be used for all temporal operations. Default is -1 (no MaxIdle time set)  
> merge-thread-stats (**optional**) - Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.  
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> put-if-absent-with-lifespan-and-max-idle-ratio (**optional**) - Ratio of PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE requests. Default is 0.  
> put-if-absent-with-lifespan-ratio (**optional**) - Ratio of PUT_IF_ABSENT_WITH_LIFESPAN requests. Default is 0.  
> put-with-lifespan-and-max-idle-ratio (**optional**) - Ratio of PUT_WITH_LIFESPAN_AND_MAXIDLE requests. Default is 0.  
> put-with-lifespan-ratio (**optional**) - Ratio of PUT_WITH_LIFESPAN requests. Default is 1.  
> ramp-up (**optional**) - Delay to let all threads start executing operations. Default is 0.  
> repeat-condition (**optional**) - If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.  
> report-latency-as-service-time (**optional**) - Enable this property in order to show the difference between latency and service.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics (**optional**) - Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).  
> synchronous-requests (**optional**) - Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, there is still the random ramp-up delay. Default is false.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  
> think-time (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  
> value-generator (**optional**) - Generator of values used in the test. By default the generator is retrieved from slave state.  

### wait-for-topology-event
Waits until some event occurs. Note that the initial rehash is not recorded in this manner, therefore waiting for that will result in timeout.
> cache-name (**optional**) - Name of the cache where we detect the events. Default is the default cache.  
> condition (**optional**) - Condition we are waiting for. Default is END (see org.radargun.traits.TopologyHistory.Event.EventType).  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> max-members (**optional**) - The maximum number of slaves that participated in this event. Default is indefinite.  
> min-members (**optional**) - The minimum number of slaves that participated in this event. Default is 0.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> set (**optional**) - Set last state before finishing. Default is true.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> timeout (**optional**) - How long should we wait until we give up with error, 0 means indefinitely. Default is 10 minutes.  
> type (**optional**) - Type of event we are detecting. Default is REHASH (see org.radargun.traits.TopologyHistory.HistoryType).  
> wait (**optional**) - Wait for the event to happen. Default is true.  

### wait-for-topology-settle
Waits for a period without any change in membership/topology history.
> cache-name (**optional**) - Name of the cache where we detect the events. Default is the default cache.  
> check-events (**optional**) - Type of events to check in this stage. Default are TOPOLOGY, REHASH, CACHE_STATUS (see org.radargun.traits.TopologyHistory.HistoryType).  
> check-membership (**optional**) - Wait for cluster membership to settle. Default is true (if the Clustered trait is supported).  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> period (**optional**) - How long period without any change are we looking for. Default is 10 seconds.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> timeout (**optional**) - How long should we wait until we give up with error, 0 means indefinitely. Default is 10 minutes.  

### write-skew-check
Stage checking the write skew detection in transactional caches.
> duration (**optional**) - Duration of the test. Default is 1 minute.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> test-null (**optional**) - Should write skew between null value and first value be tested? Default is false.  
> threads (**optional**) - Number of threads overwriting concurrently the entry. Default is 10.  

### xs-repl-check
Checks loaded data for validity. Useful for testing cross-site replication.
> backup-caches (**optional**) - Comma-separated list of all backup caches to be checked. Ignored if backup-value-generator is not specified.  
> backup-value-generator (**optional**) - Backup value generator. By default, only main (default) cache is checked. If specified, backup caches will be checked too.  
> cache-selector (**optional**) - Selects which caches will be loaded. Default is the default cache.  
> check-entry-count (**optional**) - Number of entries that will be checked in each step. Default is 1.  
> check-subparts-are-replicas (**optional**) - Check that number of non-zero subparts is equal to number of replicas. Default is false.  
> check-subparts-equal (**optional**) - Check whether the same subparts from each cache have the same size. Default is false.  
> check-subparts-sum-local (**optional**) - Check whether the sum of subparts sizes is the same as local size. Default is false.  
> check-threads (**optional**) - Number of thread per node which check data validity. Default is 1.  
> debug-null (**optional**) - If the GET request results in null response, call wrapper-specific functions to show debug info. Default is false.  
> deleted (**optional**) - If set to true, we are checking that the data are NOT in the cluster anymore. Default is false.  
> entry-size (**mandatory**) - Number of bytes carried in single entry.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> extra-entries (**optional**) - Entries that do not have the expected form but occur in the cluster. This string specifies a polynomial in number of slaves: 1,2,3 with 4 slaves would result in 1 + 2*4 + 3*4*4 = 57 extra entries.Defaults to 0.  
> fail-on-null (**optional**) - If entry is null, fail immediately. Default is false.  
> first-entry-offset (**optional**) - Index of key of the first entry.  
> first-entry-offset-slave-index (**optional**) - Index of key of the first entry. This number will be multiplied by slaveIndex. Default is 0. Has precedence over 'first-entry-offset'.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> ignore-sum (**optional**) - Usually the test checks that sum of local nodes = numOwners * numEntries + extraEntries.This option disables such behaviour. Default is false.  
> key-generator (**optional**) - Generator of keys (transforms key ID into key object). By default the generator is retrieved from slave state.  
> live-slaves-hint (**optional**) - Hint how many slaves are currently alive - if set to > 0 then the query for number of entries in this cache is postponed until the cache appears to be fully replicated. By default this is disabled.  
> log-checks-count (**optional**) - Number of queries after which a DEBUG log message is printed. Default is 10000.  
> memory-only (**optional**) - If the cache wrapper supports persistent storage and this is set to true, the check will be executed only against in-memory data. Default is false.  
> num-entries (**mandatory**) - Number of entries with key in form specified by the last used key generator, in the cache.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> size-only (**optional**) - If true, the entries are not retrieved, this stage only checks that the sum of entries from local nodes is correct. Default is false.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> step-entry-count (**optional**) - Number of entries stepped in each step. Default is 1.  
> value-generator (**optional**) - Generator of values. By default the generator is retrieved from slave state.  

