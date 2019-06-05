---
---

Query stages
------------

#### urn:radargun:stages:query:3.0

### continuous-query
Benchmark operations performance with enabled/disabled continuous query.
> cache-name (**optional**) - Cache name with which continuous query should registered. Default is null, i.e. default cache.  
> class (**mandatory**) - Full class name of the object that should be queried. Mandatory.  
> conditions (**mandatory**) - Conditions used in the query  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> group-by (**optional**) - Use grouping, in form [attribute][,attribute]*. Default is without grouping.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> having (**optional**) - Conditions applied to groups when using group-by, can use aggregations.  
> limit (**optional**) - Maximum number of the results. Default is none.  
> merge-cq (**optional**) - If multiple queries are used, specifies, if statistics should be merged in one or each CQ should keep its own statistics. Default it false.  
> offset (**optional**) - Offset in the results. Default is none.  
> order-by (**optional**) - Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.  
> order-by-aggregated-columns (**optional**) - Sorting, possibly by aggregated columns.  
> projection (**optional**) - Use projection instead of returning full object. Default is without projection.  
> projection-aggregated (**optional**) - Projection, possibly with aggregations.  
> remove (**optional**) - Allows to remove continuous query. Default is false.  
> reset-stats (**optional**) - Allows to reset statistics at the begining of the stage. Default is false.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> test-name (**optional**) - Name of the test as used for reporting. Default is 'Test'.  

### query
Stage which executes a query.
> amend-test (**optional**) - By default, each stage creates a new test. If this property is set to true,results are amended to existing test (as iterations). Default is false.  
> check-same-result (**optional**) - Check whether all invocations got the same result, and fail if not. Default is false.  
> class (**mandatory**) - Full class name of the object that should be queried. Mandatory.  
> commit-transactions (**optional**) - Specifies whether the transactions should be committed (true) or rolled back (false). Default is true  
> conditions (**mandatory**) - Conditions used in the query  
> cycle-time (**optional**) - Intended time between each request. Default is 0. Change it to greater than 0 in order to don't suffer from Coordinated Omission  
> duration (**optional**) - Benchmark duration. You have to set either this or 'totalNumOperations'.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> exposed-attributes (**optional**) - Full names of the attribute queried from InternalsExposition. Expecting values parse-able as long values. Default are none.  
> group-by (**optional**) - Use grouping, in form [attribute][,attribute]*. Default is without grouping.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> having (**optional**) - Conditions applied to groups when using group-by, can use aggregations.  
> iteration-property (**optional**) - Property, which value will be used to identify individual iterations (e.g. num-threads).  
> limit (**optional**) - Maximum number of the results. Default is none.  
> log-transaction-exceptions (**optional**) - Whether an error from transaction commit/rollback should be logged as error. Default is true.  
> merge-thread-stats (**optional**) - Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.  
> num-operations (**optional**) - The total number of operations to perform during the test. You have to set either this or 'duration'.  
> num-queries (**optional**) - Number of queries generated. Makes sense only when the conditions contain random data. Default is 1.  
> num-threads-per-node (**optional**) - The number of threads executing on each node. You have to set either this or 'total-threads'. No default.  
> offset (**optional**) - Offset in the results. Default is none.  
> order-by (**optional**) - Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.  
> order-by-aggregated-columns (**optional**) - Sorting, possibly by aggregated columns.  
> projection (**optional**) - Use projection instead of returning full object. Default is without projection.  
> projection-aggregated (**optional**) - Projection, possibly with aggregations.  
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

### reindex
Runs Queryable.reindex()
> container (**optional**) - Container (e.g. cache or DB table) which should be reindex. Default is the default container.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> test (**optional**) - Test under which performance of reindexing should be recorded. Default is 'reindex'.  

