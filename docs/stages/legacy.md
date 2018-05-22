---
---

Legacy stages
-------------

#### urn:radargun:stages:legacy:3.0

### basic-operations-test
Test using BasicOperations
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> contains-ratio (**optional**) - Ratio of CONTAINS requests. Default is 0.  
> cycle-time (**optional**) - Intended time between each request. Default is 0.  
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
> cycle-time (**optional**) - Intended time between each request. Default is 0.  
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

### conditional-operations-test
Tests (atomic) conditional operations. Note that there is no put-if-absent-ratio- this operation is executed anytime the selected key does not have value.
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> cycle-time (**optional**) - Intended time between each request. Default is 0.  
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

### key-expiration-test
During execution, keys expire (entries are removed from the cache) and new keys are used.
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> cycle-time (**optional**) - Intended time between each request. Default is 0.  
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

### streaming-operations-test
Streaming operations test stage
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> buffer-size (**optional**) - Streaming operations buffer size in bytes, default is 100  
> cache-selector (**optional**) - Selects which caches will be used in the test. By default the selector is retrieved from slave state.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> cycle-time (**optional**) - Intended time between each request. Default is 0.  
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
> cycle-time (**optional**) - Intended time between each request. Default is 0.  
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

