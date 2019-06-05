---
---

Rest stages
-----------

#### urn:radargun:stages:rest:3.0

### background-rest-operations-start
Stage for starting REST operations in the background
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> context-path (**optional**) - The context path for this REST stage. Defaults to empty string.  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to don't suffer from Coordinated Omission  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> get-ratio (**optional**) - Ratio of GET requests. Default is 1 (100%).  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
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

### background-rest-operations-stop
Stage for stopping REST operations running in the background
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> context-path (**optional**) - The context path for this REST stage. Defaults to empty string.  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to don't suffer from Coordinated Omission  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> get-ratio (**optional**) - Ratio of GET requests. Default is 1 (100%).  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
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
> test-name-to-stop (**optional**) - Name of the background operations to be stopped. Default is 'Test'.  
> think-time (**optional**) - Time between consecutive requests of one stressor thread. Default is 0.  
> timeout (**optional**) - Max duration of the test. Default is infinite.  
> total-threads (**optional**) - Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.  
> transaction-size (**optional**) - Number of requests in one transaction. Default is 1.  
> use-transactions (**optional**) - Specifies if the requests should be explicitly wrapped in transactions. Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.  

### rest-operations-test
Test using RESTOperations with specific URL
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> context-path (**optional**) - The context path for this REST stage. Defaults to empty string.  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to don't suffer from Coordinated Omission  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> get-ratio (**optional**) - Ratio of GET requests. Default is 1 (100%).  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
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

