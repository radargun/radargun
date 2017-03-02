---
---

Stage list
----------
This document is related to RadarGun 2.1.x and contains list of all stages including their properties. XSD schema file containing information about stages can be found under `$RADARGUN_HOME/schema/radargun-2.1.xsd`.

### add-result

Adds custom result to given test

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* iteration [optional]: Which iteration should the result go to. Default is 0.
* result-name [mandatory]: Name of the result.
* test-name [mandatory]: Name of the test.
* value [mandatory]: Value used as aggregation value in the test.

### analyze-test

Analyzes results of already executed test.

* analyzis-type [mandatory]: How do we process the data. We can search for maximum, minimum or average.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* iteration-selection [optional]: Which iterations should be included in the analysis. By default we iterate over all iterations.
* operation [mandatory]: Operation that should be analyzed (e.g. BasicOperations.Get).
* result-type [optional]: What should be results of this analysis. Default is VALUE.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* statistics-type [mandatory]: What value do we we retrieve from the statistics.
* store-result-to [mandatory]: Name of the target property where the result should be stored.
* test-name [mandatory]: Name of the test whose result should be analyzed.
* thread-grouping [optional]: How should the thread statistics be aggregated. By default all statistics are merged.

### background-load-data-start

Allows to load data into a cache in the background, while other stages may take place. To force process termination, use BackgroundLoadDataStopStage.

* batch-size [optional]: Size of batch to be loaded into cache (using putAll). If &lt;= 0, put() operation is used sequentially.
* cache-selector [optional]: Selects which caches will be loaded. Default is the default cache.
* entry-size [optional]: Size of the value in bytes. Default is 1000.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* key-generator [optional]: Generator of keys (transforms key ID into key object). Default is 'string'.
* key-id-offset [optional]: Initial key ID used for numbering the keys. Default is 0.
* load-all-keys [optional]: This option forces local loading of all keys on all slaves in this group (not only numEntries/numNodes). Default is false.
* log-period [optional]: Number of loaded entries after which a log entry should be written. Default is 10000.
* max-load-attempts [optional]: During loading phase, if the insert fails, try it again. This is the maximum number of attempts. Default is 10.
* num-entries [optional]: Total number of key-value entries that should be loaded into cache. Default is 100.
* num-threads [optional]: The number of threads that should load the entries on one slave. Default is 10.
* remove [optional]: If set to true, the entries are removed instead of being inserted. Default is false.
* request-period [optional]: Target period of put operations - e.g. when this is set to 10 msthe benchmark will try to do one put operation every 10 ms. By default the requests are executed at maximum speed.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* seed [optional]: Seed used for initialization of random generators - with same seed (and other arguments), the stage guarantees same entries added to the cache. By default the seed is not set.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* transaction-size [optional]: Numbers of entries loaded in one transaction. Default is to not use transactions.
* use-async-batch-loading [optional]: Controls whether batch insertion is performed in asychronous way. Default is false (prefer synchronous operations).
* use-transactions [optional]: Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize &gt; 0. Default is IF_TRANSACTIONAL.
* value-generator [optional]: Generator of values. Default is byte-array.
* wait-on-error [optional]: When an attempt to load an entry fails, wait this period to reduce the chances of failing again. Default is one second.

### background-load-data-stop

Stops data loading process started by BackgroundLoadDataStartStage.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* timeout-duration [optional]: Maximum time to wait for loading threads to finish. By default, wait until the threads finish their job.

### background-statistics-start

Starts collection of statistics from background threads and cache size.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* name [optional]: Name of the background operations. Default is 'Default'.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* stats-iteration-duration [optional]: Delay between statistics snapshots. Default is 5 seconds.

### background-statistics-stop

Stop Statistics and return collected statistics to master.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* name [optional]: Name of the background operations. Default is 'Default'.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* test-name [optional]: Name of the test used for reports. Default is 'BackgroundStats'.

### background-stressors-check

Stage that checks the progress in background stressors and fails if something went wrong.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* name [optional]: Name of the background operations. By default, all instances are checked.
* resume-after-checked [optional]: Resume stressors after we have stopped them in order to let checkers check everything. Default is false.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* wait-for-progress [optional]: Waits until all stressors record new progress, or timeout (no-progress-timeout) elapses. Default is false.
* wait-until-checked [optional]: Stops stressors and waits until all confirmed operations are checked. Default is false.

### background-stressors-start

Starts background stressor threads.

* cache-name [optional]: Cache used for the background operations. Default is null (default).
* dead-slave-timeout [optional]: Period after which a slave is considered to be dead. Default is 90 s.
* delay-between-requests [optional]: Time between consecutive requests of one stressor thread. Default is 0.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* gets [optional]: Ratio of GET requests. Default is 2.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* key-id-offset [optional]: First key ID used for key generation. Default is 0.
* legacy-entry-size [optional]: Size of value used in the entry. Default is 1024 bytes.
* legacy-load-data-for-dead-slaves [optional]: List of slaves whose data should be loaded by other threads because these slaves are not alive. Default is empty.
* legacy-load-data-on-slaves [optional]: List of slaves where the data should be loaded (others immediately start executing requests). Default is all live slaves).
* legacy-load-only [optional]: If set to true, the stressor does not execute any requests after loading the data. Default is false.
* legacy-load-with-put-if-absent [optional]: Use conditional putIfAbsent instead of simple put for loading the keys. Default is false.
* legacy-no-loading [optional]: Do not execute the loading, start usual request right away.
* legacy-put-with-replace [optional]: Use replace operations instead of puts during the test. Default is false.
* legacy-wait-until-loaded [optional]: Specifies whether the stage should wait until the entries are loaded by stressor threads. Default is true.
* log-logic-check-delayed-remove-expected-value [optional]: Check whether the value that is being removed matches the expected value. In failure scenarios, this may cause incorrect test failures. Default is true.
* log-logic-check-notifications [optional]: Check that listeners have been fired for each operation on each node (at least once). Default is false.
* log-logic-checking-threads [optional]: Number of threads on each node that are checking whether all operations from stressor threads have been logged. Default is 10.
* log-logic-counter-update-period [optional]: Number of operations after which will the stressor or checker update in-cache operation counter. Default is 50.
* log-logic-debug-failures [optional]: Debug a key if a missing operation or notification is detected. Service needs to provide an implementation of Debuggable iterface.
* log-logic-enabled [optional]: Use values which trace all operation on these keys. Therefore, they're always growing. Default is false.
* log-logic-ignore-dead-checkers [optional]: When the log value is full, the stressor needs to wait until all checkers confirm that the records have been checked before discarding oldest records. With ignoreDeadCheckers=true the stressor does not wait for checkers on dead nodes. Default is false.
* log-logic-max-delayed-remove-attempts [optional]: Maximum number of attempts to perform delayed removes when using transactions (as removes are performed in a separate TX,which can fail independently of TX performing PUT operations). If the value is negative, number of attempts is unlimited. Default is -1.
* log-logic-max-transaction-attempts [optional]: Maximum number of attempts to perform transaction. If the value is negative, number of attempts is unlimited. Default is -1.
* log-logic-no-progress-timeout [optional]: Maximum time for which are the log value checkers allowed to show no new checked values, when waiting for checks to complete or stressors to confirm new progress. Default is 10 minutes.
* log-logic-value-max-size [optional]: Maximum number of records in one entry before the older ones have to be truncated. Default is 100.
* log-logic-write-apply-max-delay [optional]: Maximum allowed delay to detect operation confirmed by stressor. Default is no delay.
* name [optional]: Name of the background operations. Default is 'Default'.
* num-entries [optional]: Number of entries (key-value pairs) inserted into the cache. Default is 1024. Needs to be greater than or equal to the product of 'numThreads' and group size.
* num-threads [optional]: Number of stressor threads. Default is 10.
* puts [optional]: Ratio of PUT requests. Default is 1.
* removes [optional]: Ratio of REMOVE requests. Default is 0.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* shared-keys [optional]: By default each thread accesses only its private set of keys. This allows all threads all values. Atomic operations are required for this functionality. Default is false.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* transaction-size [optional]: Number of requests wrapped into single transaction. By default transactions are not used (explicitly).

### background-stressors-stop

Stop BackgroundStressors.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* name [optional]: Name of the background operations. Default is 'Default'.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.

### basic-operations-test

Test using BasicOperations

* amend-test [optional]: By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.
* cache-selector [optional]: Selects which caches will be used in the test. By default the selector is retrieved from slave state.
* commit-transactions [optional]: Specifies whether the transactions should be committed (true) or rolled back (false). Default is true
* contains-ratio [optional]: Ratio of CONTAINS requests. Default is 0.
* duration [optional]: Benchmark duration. This takes precedence over numRequests. By default switched off.
* entry-size [optional]: Size of the value in bytes. Default is 1000.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* get-and-put-ratio [optional]: Ratio of GET_AND_PUT requests. Default is 0.
* get-and-remove-ratio [optional]: Ratio of GET_AND_REMOVE requests. Default is 0.
* get-ratio [optional]: Ratio of GET requests. Default is 4.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* iteration-property [optional]: Property, which value will be used to identify individual iterations (e.g. num-threads).
* key-generator [optional]: Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.
* key-selector [mandatory]: Selects which key IDs are used in the test.
* log-period [optional]: Number of operations after which a log entry should be written. Default is 10000.
* merge-thread-stats [optional]: Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.
* num-requests [optional]: Total number of request to be made against this session: reads + writes. If duration is specified this value is ignored. Default is 50000.
* num-threads-per-node [optional]: The number of threads executing on each node. You have to set either this or 'total-threads'. No default.
* put-ratio [optional]: Ratio of PUT requests. Default is 1.
* remove-ratio [optional]: Ratio of REMOVE requests. Default is 0.
* repeat-condition [optional]: If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.
* request-period [optional]: Target period of requests - e.g. when this is set to 10 msthe benchmark will try to do one request every 10 ms. By default the requests are executed at maximum speed.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* statistics [optional]: Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).
* synchronous-requests [optional]: Local threads synchronize on starting each round of requests. Note that with requestPeriod &gt; 0, there is still the random ramp-up delay. Default is false.
* test-name [optional]: Name of the test as used for reporting. Default is 'Test'.
* timeout [optional]: Max duration of the test. Default is infinite.
* total-threads [optional]: Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.
* transaction-size [optional]: Number of requests in one transaction. Default is 1.
* use-transactions [optional]: Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize &gt; 0. Default is IF_TRANSACTIONAL.
* value-generator [optional]: Generator of values used in the test. By default the generator is retrieved from slave state.

### bulk-operations-test

Executes operations from BulkOperations trait.

* amend-test [optional]: By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.
* bulk-size [optional]: Number of keys inserted/retrieved within one operation. Applicable only when the cache wrapper supports bulk operations. Default is 10.
* cache-selector [optional]: Selects which caches will be used in the test. By default the selector is retrieved from slave state.
* commit-transactions [optional]: Specifies whether the transactions should be committed (true) or rolled back (false). Default is true
* duration [optional]: Benchmark duration. This takes precedence over numRequests. By default switched off.
* entry-size [optional]: Size of the value in bytes. Default is 1000.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* get-all-async-ratio [optional]: Ratio of GET_ALL_ASYNC requests. Default is 0.
* get-all-native-ratio [optional]: Ratio of GET_ALL_NATIVE requests. Default is 4.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* iteration-property [optional]: Property, which value will be used to identify individual iterations (e.g. num-threads).
* key-generator [optional]: Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.
* key-selector [mandatory]: Selects which key IDs are used in the test.
* log-period [optional]: Number of operations after which a log entry should be written. Default is 10000.
* merge-thread-stats [optional]: Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.
* num-requests [optional]: Total number of request to be made against this session: reads + writes. If duration is specified this value is ignored. Default is 50000.
* num-threads-per-node [optional]: The number of threads executing on each node. You have to set either this or 'total-threads'. No default.
* put-all-async-ratio [optional]: Ratio of PUT_ALL_ASYNC requests. Default is 0.
* put-all-native-ratio [optional]: Ratio of PUT_ALL_NATIVE requests. Default is 1.
* remove-all-async-ratio [optional]: Ratio of REMOVE_ALL_ASYNC requests. Default is 0.
* remove-all-native-ratio [optional]: Ratio of REMOVE_ALL_NATIVE requests. Default is 0.
* repeat-condition [optional]: If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.
* request-period [optional]: Target period of requests - e.g. when this is set to 10 msthe benchmark will try to do one request every 10 ms. By default the requests are executed at maximum speed.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* statistics [optional]: Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).
* synchronous-requests [optional]: Local threads synchronize on starting each round of requests. Note that with requestPeriod &gt; 0, there is still the random ramp-up delay. Default is false.
* test-name [optional]: Name of the test as used for reporting. Default is 'Test'.
* timeout [optional]: Max duration of the test. Default is infinite.
* total-threads [optional]: Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.
* transaction-size [optional]: Number of requests in one transaction. Default is 1.
* use-transactions [optional]: Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize &gt; 0. Default is IF_TRANSACTIONAL.
* value-generator [optional]: Generator of values used in the test. By default the generator is retrieved from slave state.

### check-cache-data

Stage for checking presence or absence of data entered in other stages.

* check-entry-count [optional]: Number of entries that will be checked in each step. Default is 1.
* check-subparts-are-replicas [optional]: Check that number of non-zero subparts is equal to number of replicas. Default is false.
* check-subparts-equal [optional]: Check whether the same subparts from each cache have the same size. Default is false.
* check-subparts-sum-local [optional]: Check whether the sum of subparts sizes is the same as local size. Default is false.
* check-threads [optional]: Number of thread per node which check data validity. Default is 1.
* debug-null [optional]: If the GET request results in null response, call wrapper-specific functions to show debug info. Default is false.
* deleted [optional]: If set to true, we are checking that the data are NOT in the cluster anymore. Default is false.
* entry-size [mandatory]: Number of bytes carried in single entry.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* extra-entries [optional]: Entries that do not have the expected form but occur in the cluster. This string specifies a polynomial in number of slaves: 1,2,3 with 4 slaves would result in 1 + 2<em>4 + 3</em>4*4 = 57 extra entries.Defaults to 0.
* fail-on-null [optional]: If entry is null, fail immediately. Default is false.
* first-entry-offset [optional]: Index of key of the first entry.
* first-entry-offset-slave-index [optional]: Index of key of the first entry. This number will be multiplied by slaveIndex. Default is 0. Has precedence over 'first-entry-offset'.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* ignore-sum [optional]: Usually the test checks that sum of local nodes = numOwners * numEntries + extraEntries.This option disables such behaviour. Default is false.
* key-generator [optional]: Generator of keys (transforms key ID into key object). By default the generator is retrieved from slave state.
* live-slaves-hint [optional]: Hint how many slaves are currently alive - if set to &gt; 0 then the query for number of entries in this cache is postponed until the cache appears to be fully replicated. By default this is disabled.
* log-checks-count [optional]: Number of queries after which a DEBUG log message is printed. Default is 10000.
* memory-only [optional]: If the cache wrapper supports persistent storage and this is set to true, the check will be executed only against in-memory data. Default is false.
* num-entries [mandatory]: Number of entries with key in form specified by the last used key generator, in the cache.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* size-only [optional]: If true, the entries are not retrieved, this stage only checks that the sum of entries from local nodes is correct. Default is false.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* step-entry-count [optional]: Number of entries stepped in each step. Default is 1.
* value-generator [optional]: Generator of values. By default the generator is retrieved from slave state.

### check-topology

Controls which topology events have (not) happened recently

* cache-name [optional]: Name of the cache. Default is the default cache.
* changed [optional]: The check controls if this event has happened (true) or not happened (false). Defaults to true.
* check-events [optional]: Type of events to check in this stage. Default are TOPOLOGY, REHASH, CACHE_STATUS (see org.radargun.traits.TopologyHistory.HistoryType).
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* period [optional]: The period in milliseconds which is checked. Default is infinite.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.

### clear-cache

Removes all data from the cache

* cache-name [optional]: Name of the cache to be cleared. Default is the default cache.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* local [optional]: Execute local variant of clear on each slave. Default is null - local clear is performed, only if it is provided by the service. True enforces local clear - if given service does not provide the feature, exception is thrown.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* use-transaction [optional]: Execute the clear inside explicit transaction.

### cluster-validation

Verifies that the cluster is formed by injecting an entry into the cache and then reading it from other nodes.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* partial-replication [optional]: If set to true, then the slave will consider that the cluster is formed when one slave replicated the control entry. Otherwise the replication will only be considered successful if all slaves replicated the control value. Default is false.
* replication-time-sleep [optional]: Delay between attempts to retrieve the control entry.
* replication-try-count [optional]: How many times we should try to retrieve the control entry.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.

### command

Stage that allows you to execute generic command on the slave machine.

* args [optional]: Arguments to this command. Default are none
* cmd [mandatory]: Command that should be executed.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* exit-values [optional]: List of exit values that are allowed from the command. Default is {0}.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.

### conditional-operations-test

Tests (atomic) conditional operations. Note that there is no put-if-absent-ratio- this operation is executed anytime the selected key does not have value.

* amend-test [optional]: By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.
* cache-selector [optional]: Selects which caches will be used in the test. By default the selector is retrieved from slave state.
* commit-transactions [optional]: Specifies whether the transactions should be committed (true) or rolled back (false). Default is true
* duration [optional]: Benchmark duration. This takes precedence over numRequests. By default switched off.
* entry-size [optional]: Size of the value in bytes. Default is 1000.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* get-and-replace-ratio [optional]: Ratio of GET_AND_REPLACE requests. Default is 1.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* iteration-property [optional]: Property, which value will be used to identify individual iterations (e.g. num-threads).
* key-generator [optional]: Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.
* key-selector [mandatory]: Selects which key IDs are used in the test.
* log-period [optional]: Number of operations after which a log entry should be written. Default is 10000.
* match-percentage [optional]: Percentage of requests in which the condition should be satisfied. Default is 50%.
* merge-thread-stats [optional]: Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.
* num-requests [optional]: Total number of request to be made against this session: reads + writes. If duration is specified this value is ignored. Default is 50000.
* num-threads-per-node [optional]: The number of threads executing on each node. You have to set either this or 'total-threads'. No default.
* remove-ratio [optional]: Ratio of REMOVE requests. Default is 1.
* repeat-condition [optional]: If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.
* replace-any-ratio [optional]: Ratio of REPLACE_ANY requests. Default is 1.
* replace-ratio [optional]: Ratio of REPLACE requests. Default is 1.
* request-period [optional]: Target period of requests - e.g. when this is set to 10 msthe benchmark will try to do one request every 10 ms. By default the requests are executed at maximum speed.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* statistics [optional]: Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).
* synchronous-requests [optional]: Local threads synchronize on starting each round of requests. Note that with requestPeriod &gt; 0, there is still the random ramp-up delay. Default is false.
* test-name [optional]: Name of the test as used for reporting. Default is 'Test'.
* timeout [optional]: Max duration of the test. Default is infinite.
* total-threads [optional]: Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.
* transaction-size [optional]: Number of requests in one transaction. Default is 1.
* use-transactions [optional]: Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize &gt; 0. Default is IF_TRANSACTIONAL.
* value-generator [optional]: Generator of values used in the test. By default the generator is retrieved from slave state.

### define

Use for setting certain value

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* value [mandatory]: Value of the variable.
* var [mandatory]: Name of the variable that should be set.

### distributed-task

Stage which executes a MapReduce Task against all keys in the cache.

* callable [mandatory]: Fully qualified class name of the java.util.concurrent.Callable implementation to execute.
* callable-params [optional]: A String in the form of 'methodName:methodParameter;methodName1:methodParameter1' that allows invoking a method on the callable. The method must be public and take a String parameter. Default is none.
* execution-policy [optional]: The name of the execution policy. The default is default policy of the service.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* failover-policy [optional]: The name of the failover policy. The default is default policy of the service.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* node-address [optional]: The node address where the task will be executed. The default is null, and tasks will be executed against all nodes in the cluster.
* num-executions [optional]: The number of times to execute the Callable. The default is 1.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* total-bytes-key [optional]: The name of the key in the MasterState object that returns the total number of bytes processed by the Callable. The default is RandomDataStage.RANDOMDATA_TOTALBYTES_KEY.

### isolation-level-check

Stage for testing guaranties of isolation levels.

* duration [optional]: How long should this stage take. Default is 1 minute.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* expected-level [mandatory]: Expected isolation level (should match to cache configuration). Supported values are [READ_COMMITTED, REPEATABLE_READ].
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* readers [optional]: Number of concurrent threads that try to retrieve the value. Default is 10.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* transaction-size [optional]: Number of reads executed inside on transaction. Default is 30.
* writers [optional]: Number of concurrent threads that modify the value. Default is 2.

### iterate

Iterates through all entries.

* amend-test [optional]: By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.
* commit-transactions [optional]: Specifies whether the transactions should be committed (true) or rolled back (false). Default is true
* container-name [optional]: Name of the container (e.g. cache, DB table etc.) that should be iterated. Default is the default container.
* converter-class [optional]: Full class name of the converter. Default is no converter (Map.Entry is returned).
* converter-param [optional]: Parameter for the converter (used to resolve its properties). No defaults.
* duration [optional]: Benchmark duration. This takes precedence over numRequests. By default switched off.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* fail-on-failed-iteration [optional]: Fail the stage if some of the stressors has failed. Default is true.
* fail-on-not-total-size [optional]: Fail when the number of elements is different than total size. Default is true if filter is not defined and false otherwise.
* fail-on-uneven-elements [optional]: Fail when the number of elements iterated is not same. Default is true.
* filter-class [optional]: Full class name of the filter used to iterate through entries. Default is none (accept all).
* filter-param [optional]: Parameters for the filter (used to resolve its properties). No defaults.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* iteration-property [optional]: Property, which value will be used to identify individual iterations (e.g. num-threads).
* log-period [optional]: Number of operations after which a log entry should be written. Default is 10000.
* max-next-failures [optional]: Number of next() calls that are allowed to fail until we break the loop. Default is 100.
* merge-thread-stats [optional]: Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.
* num-requests [optional]: Total number of request to be made against this session: reads + writes. If duration is specified this value is ignored. Default is 50000.
* num-threads-per-node [optional]: The number of threads executing on each node. You have to set either this or 'total-threads'. No default.
* repeat-condition [optional]: If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.
* request-period [optional]: Target period of requests - e.g. when this is set to 10 msthe benchmark will try to do one request every 10 ms. By default the requests are executed at maximum speed.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* statistics [optional]: Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).
* synchronous-requests [optional]: Local threads synchronize on starting each round of requests. Note that with requestPeriod &gt; 0, there is still the random ramp-up delay. Default is false.
* test-name [optional]: Name of the test as used for reporting. Default is 'Test'.
* timeout [optional]: Max duration of the test. Default is infinite.
* total-threads [optional]: Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.
* transaction-size [optional]: Number of requests in one transaction. Default is 1.
* use-transactions [optional]: Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize &gt; 0. Default is IF_TRANSACTIONAL.

### j-profiler

Stage for invoking operations on JProfiler.
          Remember to set up JVM args: "-agentpath:/path/to/libjprofilerti.so=offline,id=100,config=/path/to/configuration.xml"

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* operations [mandatory]: Operations that should be invoked on the Controller
* reset-cpu-stats [optional]: If true, any previously accumulated CPU profiling data will be discarded. If false, CPU data willbe accumulated across pairs of invocations of START_CPU_RECORDING and STOP_CPU_RECORDING. Default is false.
* reset-memory-stats [optional]: If true, any previously accumulated Memory profiling data will be discarded. If false, CPU data willbe accumulated across pairs of invocations of START_MEMORY_RECORDING and STOP_MEMORY_RECORDING. Default is false.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* snapshot-directory [optional]: Directory where the snapshot should be written (for SAVE_SNAPSHOT).

### jmx-cluster-validation

Validates formation of the cluster remotely via JMX.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* jmx-connection-timeout [optional]: JMX connection timeout. Default is 3 seconds.
* plugin [optional]: Plugin used for class-loading JMX connector.
* prop1 [optional]: Generic property 1.
* prop2 [optional]: Generic property 1.
* prop3 [optional]: Generic property 1.
* slaves [optional]: Indices of slaves that should be up. Default is empty.
* wait-timeout [optional]: Cluster validation timeout. Default is 1 minute.

### jmx-cluster-validation-prepare

Collects configuration for JMXClusterValidationStage.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* jmx-connection-timeout [optional]: JMX Connection timeout. Default is 3 seconds.
* prop1 [optional]: Generic property 1.
* prop2 [optional]: Generic property 2.
* prop3 [optional]: Generic property 3.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* wait-timeout [optional]: Cluster validation timeout. Default is 1 minute.

### jmx-invocation

Allows to invoke JMX-exposed methods and attributes.

* continue-on-failure [optional]: Continue method invocations if an exception occurs. Default is false.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* expected-slave-result [optional]: Expected result value. If specified, results of method invocations are compared with this value.
* expected-total-result [optional]: Expected result, calculated as sum/concatenation (with ',' delimeter) of results from individual slaves.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* method-parameters [optional]: Method parameters. If specified, the number of parameters must match the number of parameter signatures supplied.
* method-signatures [optional]: Method parameter signatures.
* operation-type [optional]: Type of action to be performed. Invocation of specified method (INVOKE_METHOD) is performed by default. Optionally, query for a specified attribute (via method-parameters) can be performed (GET_ATTRIBUTE_VALUE) or setting a specified attribute (via method-parameters) can be performed(SET_ATTRIBUTE_VALUE).
* query [mandatory]: Method will be invoked on all ObjectInstances matching given query.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* target-name [mandatory]: Name of the method to be invoked / attribute to be queried for.

### jvm-monitor-start

Starts collecting statistics locally on each slave node.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* interface-name [optional]: Specifies the network interface where statistics are gathered. If not specified, then statistics are not collected.
* period [optional]: Period of statistics collection. The default is 1 second.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.

### jvm-monitor-stop

Stop collecting statistics on each slave node and return collected statistics to the master node.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.

### key-expiration-test

During execution, keys expire (entries are removed from the cache) and new keys are used.

* amend-test [optional]: By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.
* cache-selector [optional]: Selects which caches will be used in the test. By default the selector is retrieved from slave state.
* commit-transactions [optional]: Specifies whether the transactions should be committed (true) or rolled back (false). Default is true
* duration [optional]: Benchmark duration. This takes precedence over numRequests. By default switched off.
* entry-lifespan [optional]: With fixedKeys=false, maximum lifespan of an entry. Default is 1 hour.
* entry-size [optional]: Size of the value in bytes. Default is 1000.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* expect-lost-keys [optional]: Due to configuration (eviction, expiration), some keys may spuriously disappear. Do not issue a warning for this situation. Default is false.
* get-ratio [optional]: Ratio of GET requests. Default is 4.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* iteration-property [optional]: Property, which value will be used to identify individual iterations (e.g. num-threads).
* key-generator [optional]: Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.
* log-period [optional]: Number of operations after which a log entry should be written. Default is 10000.
* merge-thread-stats [optional]: Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.
* num-bytes-per-thread [optional]: Maximum number of bytes in entries' values stored in the cache by one stressor thread at one moment.
* num-entries-per-thread [optional]: Maximum number of entries stored in the cache by one stressor thread at one moment.
* num-requests [optional]: Total number of request to be made against this session: reads + writes. If duration is specified this value is ignored. Default is 50000.
* num-threads-per-node [optional]: The number of threads executing on each node. You have to set either this or 'total-threads'. No default.
* put-ratio [optional]: Ratio of PUT requests. Default is 1.
* repeat-condition [optional]: If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.
* request-period [optional]: Target period of requests - e.g. when this is set to 10 msthe benchmark will try to do one request every 10 ms. By default the requests are executed at maximum speed.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* statistics [optional]: Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).
* synchronous-requests [optional]: Local threads synchronize on starting each round of requests. Note that with requestPeriod &gt; 0, there is still the random ramp-up delay. Default is false.
* test-name [optional]: Name of the test as used for reporting. Default is 'Test'.
* timeout [optional]: Max duration of the test. Default is infinite.
* total-threads [optional]: Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.
* transaction-size [optional]: Number of requests in one transaction. Default is 1.
* use-transactions [optional]: Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize &gt; 0. Default is IF_TRANSACTIONAL.
* value-generator [optional]: Generator of values used in the test. By default the generator is retrieved from slave state.

### load-data

Loads data into the cache using specified generators.

* batch-size [optional]: Size of batch to be loaded into cache (using putAll). If &lt;= 0, put() operation is used sequentially.
* cache-selector [optional]: Selects which caches will be loaded. Default is the default cache.
* entry-size [optional]: Size of the value in bytes. Default is 1000.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* key-generator [optional]: Generator of keys (transforms key ID into key object). Default is 'string'.
* key-id-offset [optional]: Initial key ID used for numbering the keys. Default is 0.
* load-all-keys [optional]: This option forces local loading of all keys on all slaves in this group (not only numEntries/numNodes). Default is false.
* log-period [optional]: Number of loaded entries after which a log entry should be written. Default is 10000.
* max-load-attempts [optional]: During loading phase, if the insert fails, try it again. This is the maximum number of attempts. Default is 10.
* num-entries [optional]: Total number of key-value entries that should be loaded into cache. Default is 100.
* num-threads [optional]: The number of threads that should load the entries on one slave. Default is 10.
* remove [optional]: If set to true, the entries are removed instead of being inserted. Default is false.
* request-period [optional]: Target period of put operations - e.g. when this is set to 10 msthe benchmark will try to do one put operation every 10 ms. By default the requests are executed at maximum speed.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* seed [optional]: Seed used for initialization of random generators - with same seed (and other arguments), the stage guarantees same entries added to the cache. By default the seed is not set.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* transaction-size [optional]: Numbers of entries loaded in one transaction. Default is to not use transactions.
* use-async-batch-loading [optional]: Controls whether batch insertion is performed in asychronous way. Default is false (prefer synchronous operations).
* use-transactions [optional]: Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize &gt; 0. Default is IF_TRANSACTIONAL.
* value-generator [optional]: Generator of values. Default is byte-array.
* wait-on-error [optional]: When an attempt to load an entry fails, wait this period to reduce the chances of failing again. Default is one second.

### load-file

Loads the contents of a file into the cache.

* bucket [optional]: The name of the bucket where keys are written. The default is null.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* file-path [mandatory]: Full pathname to the file.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* print-write-statistics [optional]: If true, then the time for each put operation is written to the logs. The default is false.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* string-data [optional]: If true, then String objects are written to the cache. The default is false.
* value-size [optional]: The size of the values to put into the cache from the contents of the file. The default size is 1MB (1024 * 1024).

### map-reduce

Stage which executes a MapReduce Task against all keys in the cache.

* cache-name [optional]: Name of the cache where map-reduce task should beexecuted. Default is the default cache.
* collator-fqn [optional]: Fully qualified class name of the org.infinispan.distexec.mapreduce.Collator implementation to execute. The default is null.
* collator-params [optional]: A String in the form of 'methodName:methodParameter;methodName1:methodParameter1' that allows invoking a method on the Collator Object. The method must be public and take a String parameter. The default is null.
* combiner-fqn [optional]: Fully qualified class name of the org.infinispan.distexec.mapreduce.Reducer implementation to use as a combiner.
* combiner-params [optional]: A String in the form of 'methodName:methodParameter;methodName1:methodParameter1' that allows invoking a method on the Reducer Object used as a combiner. The method must be public and take a String parameter. The default is null.
* distribute-reduce-phase [optional]: Boolean value that determines if the Reduce phase of the MapReduceTask is distributed. The default is true.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* mapper-fqn [mandatory]: Fully qualified class name of the mapper implementation to execute.
* mapper-params [optional]: A String in the form of 'methodName:methodParameter;methodName1:methodParameter1' that allows invoking a method on the Mapper Object. The method must be public and take a String parameter. The default is null.
* num-executions [optional]: The number of times to execute the Map/Reduce task. The default is 10.
* print-result [optional]: Boolean value that determines if the final results of the MapReduceTask are written to the log of the first slave node. The default is false.
* reducer-fqn [mandatory]: Fully qualified class name of the org.infinispan.distexec.mapreduce.Reducer implementation to execute.
* reducer-params [optional]: A String in the form of 'methodName:methodParameter;methodName1:methodParameter1' that allows invoking a method on the Reducer Object. The method must be public and take a String parameter. The default is null.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* store-result-in-cache [optional]: Boolean value that determines if the final results of the MapReduceTask are stored in the cache. The collated object will be stored at key MAPREDUCE_RESULT_KEY. The result map will be stored in a cache named MAPREDUCE_RESULT_KEY. The default is false.
* timeout [optional]: A timeout value for the remote communication that happens during a Map/Reduce task. The default is zero which means to wait forever.
* total-bytes-key [optional]: The name of the key in the MasterState object that returns the total number of bytes processed by the Map/Reduce task. The default is RandomDataStage.RANDOMDATA_TOTALBYTES_KEY.
* unit [optional]: The java.util.concurrent.TimeUnit to use with the timeout property. The default is TimeUnit.MILLISECONDS.
* use-intermediate-shared-cache [optional]: Boolean value that determines if the intermediate results of the MapReduceTask are shared. The default is true.

### monitor-start

Starts collecting statistics locally on each slave node.

* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* interface-name [optional]: Specifies the network interface where statistics are gathered. If not specified, then statistics are not collected.
* period [optional]: Period of statistics collection. The default is 1 second.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.


### monitor-stop

Stop collecting statistics on each slave node and return collected statistics to the master node.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.


### parallel-start-stop

The stage start and stops some nodes concurrently (without waiting for each other).


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* graceful [optional]: If set to false, the node crash should be simulated. By default node should be shutdown gracefully.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* may-fail-on [optional]: Set of slaves where the start may fail but this will not cause an error. Default is none.
* reachable [optional]: Applicable only for cache wrappers with Partitionable feature. Set of slaves that should be reachable from the new node. Default is all slaves.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* start [optional]: Set of slaves which should be started in this stage. Default is empty.
* start-delay [optional]: Delay before the slaves are started. Default is 0.
* stop [optional]: Set of slaves which should be stopped in this stage. Default is empty.
* stop-delay [optional]: Delay before the slaves are stopped. Default is 0.
* stop-roles [optional]: Set of roles which should be stopped in this stage. Default is empty.


### periodic-heap-dump

Periodically generates heap dumps.


* dir [mandatory]: Location on disk where the heap dumps should be stored.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* initial-delay [optional]: Delay before the first heap dump. Default is 0.
* period [optional]: How often should be the heap dumps created. Default is every 30 minutes.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* stop [optional]: Set this flag to true in order to terminate the heap dumper. Default is false.


### query

Stage which executes a Query using Infinispan-query API against all keys in the cache.


* amend-test [optional]: By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.
* check-same-result [optional]: Check whether all slaves got the same result, and fail if not. Default is false.
* commit-transactions [optional]: Specifies whether the transactions should be committed (true) or rolled back (false). Default is true
* conditions [mandatory]: Conditions used in the query
* duration [optional]: Benchmark duration. This takes precedence over numRequests. By default switched off.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* iteration-property [optional]: Property, which value will be used to identify individual iterations (e.g. num-threads).
* limit [optional]: Maximum number of the results. Default is none.
* log-period [optional]: Number of operations after which a log entry should be written. Default is 10000.
* merge-thread-stats [optional]: Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.
* num-requests [optional]: Total number of request to be made against this session: reads + writes. If duration is specified this value is ignored. Default is 50000.
* num-threads-per-node [optional]: The number of threads executing on each node. You have to set either this or 'total-threads'. No default.
* offset [optional]: Offset in the results. Default is none.
* order-by [optional]: Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.
* projection [optional]: Use projection instead of returning full object. Default is without projection.
* query-object-class [mandatory]: Full class name of the object that should be queried. Mandatory.
* repeat-condition [optional]: If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.
* request-period [optional]: Target period of requests - e.g. when this is set to 10 msthe benchmark will try to do one request every 10 ms. By default the requests are executed at maximum speed.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* statistics [optional]: Type of gathered statistics. Default are the 'default' statistics (fixed size memory footprint for each operation).
* synchronous-requests [optional]: Local threads synchronize on starting each round of requests. Note that with requestPeriod &gt; 0, there is still the random ramp-up delay. Default is false.
* test-name [optional]: Name of the test as used for reporting. Default is 'Test'.
* timeout [optional]: Max duration of the test. Default is infinite.
* total-threads [optional]: Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.
* transaction-size [optional]: Number of requests in one transaction. Default is 1.
* use-transactions [optional]: Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize &gt; 0. Default is IF_TRANSACTIONAL.


### random-data

Generates random data to fill the cache.


* bucket [optional]: The name of the bucket where keys are written. The default is null.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* limit-word-count [optional]: If true, then the random word generator selects a word from a pre-defined list. The default is false.
* max-sleep-interval [optional]: The maximum number of seconds to sleep before retrying a failed put command. The default is 5.
* max-word-count [optional]: The maximum number of words to generate in the pre-defined list of words used with limitWordCount.The default is 100.
* max-word-length [optional]: The maximum number of characters allowed in a word. The default is 20.
* print-write-statistics [optional]: If true, then the time for each put operation is written to the logs. The default is false.
* put-retry-count [optional]: The number of times to retry a put if it fails. Default is 10.
* ram-percentage [optional]: A double that represents the percentage of the total Java heap used to determine the amount of data to put into the cache. Either valueCount or ramPercentageDataSize should be specified, but not both.
* random-seed [optional]: The seed to use for the java.util.Random object. The default is the return value of Calendar.getInstance().getWeekYear().
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* share-words [optional]: If false, then each node in the cluster generates a list of maxWordCount words. If true, then each node in the cluster shares the same list of words. The default is false.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* string-data [optional]: If true, then String objects with printable characters are written to the cache.The default is false
* target-memory-use [optional]: The number of bytes to write to the cache when the valueByteOverhead, stringData, and valueSize are taken into account. The code assumes this is an even multiple of valueSize plus valueByteOverhead. If stringData is true, then the code assumes this is an even multiple of (2 * valueSize) plus valueByteOverhead.
* value-byte-overhead [optional]: The bytes used over the size of the key and value when putting to the cache. By default the stage retrieves the value from cache wrapper automatically.
* value-count [optional]: The number of values of valueSize to write to the cache. Either valueCount or ramPercentageDataSize should be specified, but not both.
* value-size [optional]: The size of the values to put into the cache. The default size is 1MB (1024 * 1024).


### register-listeners

Benchmark operations performance where cluster listenersTrait are enabled or disabled.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* register-listeners [optional]: Before stress stage, cluster listeners would be enabled. This is flag to turn them on. Default is false.
* reset-stats [optional]: Allows to reset statistics at the begining of the stage. Default is false.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* sleep-time [optional]: Use sleep time to simulate some work on listener. Default is -1(do not sleep) ms.
* sync [optional]: Setup if cache listener is synchronous/asynchronous. Default is true
* test-name [optional]: Name of the test as used for reporting. Default is 'Test'.
* unregister-listeners [optional]: Before stress stage, cluster listeners would be disabled. This is flag to turn them off. Default is false.


### reindex

Runs Queryable.reindex()


* container [optional]: Container (e.g. cache or DB table) which should be reindex. Default is the default container.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* test [optional]: Test under which performance of reindexing should be recorded. Default is 'reindex'.


### repeat-begin

DO NOT USE DIRECTLY. This stage is added at the beginning of each repeat.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* from [optional]: Initial counter value. Default is 0.
* inc [optional]: Counter increment. Default is 1.
* name [optional]: Repeat name. Default is none.
* times [optional]: Sets from=0, to=times-1. Default is none.
* to [optional]: Maximum counter value. Default is none.


### repeat-continue

DO NOT USE DIRECTLY. This stage is added at the end of each repeat.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* from [optional]: Initial counter value. Default is 0.
* inc [optional]: Counter increment. Default is 1.
* name [optional]: Repeat name. Default is none.
* times [optional]: Sets from=0, to=times-1. Default is none.
* to [optional]: Maximum counter value. Default is none.


### repeat-end

DO NOT USE DIRECTLY. This stage is added at the end of each repeat.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* from [optional]: Initial counter value. Default is 0.
* inc [optional]: Counter increment. Default is 1.
* name [optional]: Repeat name. Default is none.
* times [optional]: Sets from=0, to=times-1. Default is none.
* to [optional]: Maximum counter value. Default is none.


### scenario-cleanup

DO NOT USE DIRECTLY. This stage is automatically inserted after the last stage in each scenario. You can alter the properties in &lt;cleanup/&amp;gt element.


* check-memory [optional]: Specifies whether the check for amount of free memory should be performed. Default is true.
* check-threads [optional]: Specifies whether the check for unfinished threads should be performed. Default is true.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* heap-dump-dir [optional]: Directory where the heap dump will be produced if the memory threshold is hit or some threads have not finished. By default the dump will not be produced.
* memory-release-timeout [optional]: Timeout for releasing memory through garbage collections. Default is 30 seconds.
* memory-threshold [optional]: If the available (free) memory after service destroy and System.gc() is below percentage specified in this property the benchmark will fail. Default is 95.
* stop-timeout [optional]: Timeout for stopped threads to join. Default is 10 seconds.
* stop-unfinished-threads [optional]: Calls Thread.stop() on threads that have not finished. Works only if checkThreads=true. Default is true.


### scenario-destroy

DO NOT USE DIRECTLY. This stage is automatically inserted after the last stage in each scenario.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* graceful-stop-timeout [optional]: Timeout for the Lifecycle.stop() execution - if the stop() does not return within this timeout, Killable.kill() is called (if it is supported). Default is 30 seconds.


### scenario-init

DO NOT USE DIRECTLY. This stage is automatically inserted before the beginning of scenario.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* heap-dump-dir [optional]: Directory where the heap dump will be produced. Contrary to scenario-cleanup, if this directory is set, the heap dump is written always. By default the dump will not be produced.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.


### service-start

Starts services on specified slaves


* cluster-formation-timeout [optional]: Time allowed the cluster to reach `expectNumSlaves` members. Default is 3 minutes.
* delay-after-first-slave-starts [optional]: Delay (staggering) after first slave's start is initiated. Default is 5s.
* delay-between-starting-slaves [optional]: Delay between initiating start of i-th and (i+1)-th slave. Default is 500 ms.
* dump-config [optional]: Collect configuration files and properties for the service, and pass those to reporters. Default is true.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* expect-num-slaves [optional]: The number of slaves that should be up after all slaves are started. Applicable only with validateCluster=true. Default is all slaves in the cluster where this stage will be executed (in the same site in case of multi-site configuration).
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* may-fail-on [optional]: Set of slaves where the start may fail but this will not cause an error. Default is none.
* reachable [optional]: Set of slaves that should be reachable to the newly spawned slaves (see Partitionable feature for details). Default is all slaves.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* stagger-slave-startup [optional]: If set to true, the slaves will not be started in one moment but the startup will be delayed. Default is true.
* validate-cluster [optional]: Specifies whether the cluster formation should be checked after cache wrapper startup. Default is true.


### service-stop

Stops or kills (simulates node crash) one or more nodes.


* async [optional]: If set to true the benchmark will not wait until the node is stopped. Default is false.
* delay-execution [optional]: If this value is positive the stage will spawn a thread which will stop the node after the delay. The stage will not wait for anything. By default the stop is immediate and synchronous.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* graceful [optional]: If set to false, the node crash should be simulated. By default node should be shutdown gracefully.
* graceful-stop-timeout [optional]: Timeout for the Lifecycle.stop() execution - if the stop() does not return within this timeout, Killable.kill() is called (if it is supported). Default is 2 minutes.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* wait-for-delayed [optional]: If set, the stage will not stop any node but will wait until the delayed execution is finished. Default is false.


### set-log-level

Debugging stage: changes log priorities


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* package [mandatory]: The package or class which should be affected.
* pop [optional]: If set to true, instead of setting the priority directly just undo the last priority change. Default is false.
* priority [optional]: The new priority that should be used. No defaults.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.


### set-partitions

Partitions the cluster into several parts that cannot communicate.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* partitions [mandatory]: Set of sets of partitions, e.g. [0,1],[2] makes two partitions, one with slaves 0 and 1 and second with slave 2 alone.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.


### single-tx-check

Paired with SingleTXLoadStage. Checks that the previous stage had the expected result


* commit-slave [optional]: Indices of slaves which should have committed the transaction (others rolled back). Default is all committed.
* commit-thread [optional]: Indices of threads which should have committed the transaction (others rolled back). Default is all committed.
* deleted [optional]: If this is set to true, REMOVE operation should have been executed. Default is false.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* transaction-size [optional]: Expected size of the transcation.


### single-tx-load

Performs single transaction with requests on key0 .. keyN in multiple threads on multiple slaves.


* commit-slave [optional]: Indices of slaves which should commit the transaction (others will rollback). Default is all commit.
* commit-thread [optional]: Indices of threads which should commit the transaction (others will rollback). Default is all commit.
* delete [optional]: The threads by default do the PUT request, if this is set to true they will do REMOVE. Default is false.
* duration [optional]: The enforced duration of the transaction. If &gt; 0 the threads will sleep for duration/transactionSize after each request. Default is 0.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* threads [optional]: Number of threads that should execute the transaction. Default is 1.
* transaction-size [optional]: Number of request in the transaction. Default is 20.


### sleep

Sleeps specified number of milliseconds.


* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* time [mandatory]: Sleep duration.


### stack-trace-watchdog

Debug usage only. Periodically check for all thread stack traces and print them out.


* async-logging [optional]: If set to true the watchdog will not use standard logging for output but will push the output to queue consumed (logged) by another thread. Default is false.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* mask [optional]: If set, only those threads which have this mask in the name will be checked. Default is not set.
* only-stuck [optional]: By default the check will print out only those threads which appear to be stuck. If this is set to false all threads will be printed out. Default is true.
* period [optional]: The delay between consecutive checks. Default is 10 seconds.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* short-stack [optional]: Threads with stack lower or equal to this value are never printed (because usually such threads are parked in thread pools). Default is 10.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.


### tpcc-benchmark

Simulate the activities found in complex OLTP application environments.


* arrival-rate [optional]: Average arrival rate of the transactions to the system. Default is 0.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* num-threads [optional]: Number of threads that will work on this slave. Default is 10.
* order-status-weight [optional]: Percentage of Order Status transactions. Default is 5 %.
* payment-weight [optional]: Percentage of Payment transactions. Default is 45 %.
* per-thread-simul-time [optional]: Total time (in seconds) of simulation for each stressor thread. Default is 180.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.


### tpcc-population

This stage shuld be run before the TpccBenchmarkStage.


* c-id-mask [optional]: Mask used to generate non-uniformly distributed random customer numbers. Default is 1023.
* c-last-mask [optional]: Mask used to generate non-uniformly distributed random customer last names. Default is 255.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* num-warehouses [optional]: Number of Warehouses. Default is 1.
* ol-id-mask [optional]: Mask used to generate non-uniformly distributed random item numbers. Default is 8191.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.


### wait-for-topology-event

Waits until some event occurs. Note that the initial rehash is not recorded in this manner, therefore waiting for that will result in timeout.


* cache-name [optional]: Name of the cache where we detect the events. Default is the default cache.
* condition [optional]: Condition we are waiting for. Default is END (see org.radargun.traits.TopologyHistory.Event.EventType).
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* max-members [optional]: The maximum number of slaves that participated in this event. Default is indefinite.
* min-members [optional]: The minimum number of slaves that participated in this event. Default is 0.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* set [optional]: Set last state before finishing. Default is true.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* timeout [optional]: How long should we wait until we give up with error, 0 means indefinitely. Default is 10 minutes.
* type [optional]: Type of event we are detecting. Default is REHASH (see org.radargun.traits.TopologyHistory.HistoryType).
* wait [optional]: Wait for the event to happen. Default is true.


### wait-for-topology-settle

Waits for a period without any change in membership/topology history.


* cache-name [optional]: Name of the cache where we detect the events. Default is the default cache.
* check-events [optional]: Type of events to check in this stage. Default are TOPOLOGY, REHASH, CACHE_STATUS (see org.radargun.traits.TopologyHistory.HistoryType).
* check-membership [optional]: Wait for cluster membership to settle. Default is true (if the Clustered trait is supported).
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* period [optional]: How long period without any change are we looking for. Default is 10 seconds.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* timeout [optional]: How long should we wait until we give up with error, 0 means indefinitely. Default is 10 minutes.


### write-skew-check

Stage checking the write skew detection in transactional caches.


* duration [optional]: Duration of the test. Default is 1 minute.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* test-null [optional]: Should write skew between null value and first value be tested? Default is false.
* threads [optional]: Number of threads overwriting concurrently the entry. Default is 10.


### xs-repl-check

Checks loaded data for validity. Useful for testing cross-site replication.


* backup-caches [optional]: Comma-separated list of all backup caches to be checked. Ignored if backup-value-generator is not specified.
* backup-value-generator [optional]: Backup value generator. By default, only main (default) cache is checked. If specified, backup caches will be checked too.
* check-entry-count [optional]: Number of entries that will be checked in each step. Default is 1.
* check-subparts-are-replicas [optional]: Check that number of non-zero subparts is equal to number of replicas. Default is false.
* check-subparts-equal [optional]: Check whether the same subparts from each cache have the same size. Default is false.
* check-subparts-sum-local [optional]: Check whether the sum of subparts sizes is the same as local size. Default is false.
* check-threads [optional]: Number of thread per node which check data validity. Default is 1.
* debug-null [optional]: If the GET request results in null response, call wrapper-specific functions to show debug info. Default is false.
* deleted [optional]: If set to true, we are checking that the data are NOT in the cluster anymore. Default is false.
* entry-size [mandatory]: Number of bytes carried in single entry.
* exit-on-failure [optional]: If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.
* extra-entries [optional]: Entries that do not have the expected form but occur in the cluster. This string specifies a polynomial in number of slaves: 1,2,3 with 4 slaves would result in 1 + 2<em>4 + 3</em>4*4 = 57 extra entries.Defaults to 0.
* fail-on-null [optional]: If entry is null, fail immediately. Default is false.
* first-entry-offset [optional]: Index of key of the first entry.
* first-entry-offset-slave-index [optional]: Index of key of the first entry. This number will be multiplied by slaveIndex. Default is 0. Has precedence over 'first-entry-offset'.
* groups [optional]: Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.
* ignore-sum [optional]: Usually the test checks that sum of local nodes = numOwners * numEntries + extraEntries.This option disables such behaviour. Default is false.
* key-generator [optional]: Generator of keys (transforms key ID into key object). By default the generator is retrieved from slave state.
* live-slaves-hint [optional]: Hint how many slaves are currently alive - if set to &gt; 0 then the query for number of entries in this cache is postponed until the cache appears to be fully replicated. By default this is disabled.
* log-checks-count [optional]: Number of queries after which a DEBUG log message is printed. Default is 10000.
* memory-only [optional]: If the cache wrapper supports persistent storage and this is set to true, the check will be executed only against in-memory data. Default is false.
* num-entries [mandatory]: Number of entries with key in form specified by the last used key generator, in the cache.
* roles [optional]: Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.
* size-only [optional]: If true, the entries are not retrieved, this stage only checks that the sum of entries from local nodes is correct. Default is false.
* slaves [optional]: Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.
* step-entry-count [optional]: Number of entries stepped in each step. Default is 1.
* value-generator [optional]: Generator of values. By default the generator is retrieved from slave state.


