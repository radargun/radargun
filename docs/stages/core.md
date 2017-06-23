---
---

Core stages
-----------

#### urn:radargun:stages:core:3.0

### add-result
Adds custom result to given test
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> iteration (**optional**) - Which iteration should the result go to. Default is 0.  
> result-name (**mandatory**) - Name of the result.  
> test-name (**mandatory**) - Name of the test.  
> value (**mandatory**) - Value used as aggregation value in the test.  

### analyze-test
Analyzes results of already executed test.
> analyzis-type (**mandatory**) - How do we process the data. We can search for maximum, minimum or average.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> iteration-selection (**optional**) - Which iterations should be included in the analysis. By default we iterate over all iterations.  
> operation (**mandatory**) - Operation that should be analyzed (e.g. BasicOperations.Get).  
> result-type (**optional**) - What should be results of this analysis. Default is VALUE.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> statistics-type (**mandatory**) - What value do we we retrieve from the statistics.  
> store-result-to (**mandatory**) - Name of the target property where the result should be stored.  
> test-name (**mandatory**) - Name of the test whose result should be analyzed.  
> thread-grouping (**optional**) - How should the thread statistics be aggregated. By default all statistics are merged.  

### cluster-split-verify
Verifies that there weren't any changes to the cluster size during testing.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> verify (**optional**) - Set to true in 2nd definition of the stage in benchmark to verify that no splits occurred. Default is false.  

### command
Stage that allows you to execute generic command on the slave machine.
> args (**optional**) - Arguments to this command. Default are none  
> cancel (**optional**) - Cancel running command, loaded using the 'var' property. By default not cancelling anything.  
> cmd (**optional**) - Command that should be executed. No default, but must be provided unless 'var' is set.  
> err (**optional**) - Error output file. By default uses standard error.  
> err-append (**optional**) - Append error output to the file instead of overwriting. Default is to overwrite.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> exit-values (**optional**) - List of exit values that are allowed from the command. Default is {0}.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> non-parsed-args (**optional**) - Argument which won't be parsed. Useful to run piped commands, like sh -c "echo yes | some interactive script"  
> out (**optional**) - Output file. By default uses standard output.  
> out-append (**optional**) - Append output to the file instead of overwriting. Default is to overwrite.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> var (**optional**) - Store/load process into/from state variable. Use this in combination with 'waitForExit=false'. By default the process is not stored/loaded.  
> wait-for-exit (**optional**) - Wait until the command finishes. Default is true.  

### cpu-burn
Burns CPU time in several threads to simulate CPU intensive app.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> num-threads (**optional**) - Number of threads burning CPU.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> stop (**optional**) - If set to true, all threads are stopped and the num-threads attribute is ignored.  

### define
Use for setting certain value
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> value (**mandatory**) - Value of the variable.  
> var (**mandatory**) - Name of the variable that should be set.  

### j-groups-probe
Allows to invoke JGroups probe queries. For details on probe usage see org.jgroups.tests.Probe.
> address (**optional**) - Diagnostic address to send queries to. Default is 224.0.75.75.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> expected-response-count (**mandatory**) - Minimum number of responses to wait for. Default is -1 don't wait for responses.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> port (**optional**) - Diagnostic port. Default is 7500.  
> print-results-as-info (**optional**) - Print results of operation to log (INFO level). By default trace logging needs to be enabled.  
> queries (**mandatory**) - List of queries to be performed.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> timeout (**optional**) - Maximum time to wait for query responses. Default is 60 seconds. Valid only when used in conjunction with expectedResponseCount parameter.  

### j-profiler
Stage for invoking operations on JProfiler.
Remember to set up JVM args: "-agentpath:/path/to/libjprofilerti.so=offline,id=100,config=/path/to/configuration.xml"
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> operations (**mandatory**) - Operations that should be invoked on the Controller  
> reset-cpu-stats (**optional**) - If true, any previously accumulated CPU profiling data will be discarded. If false, CPU data willbe accumulated across pairs of invocations of START_CPU_RECORDING and STOP_CPU_RECORDING. Default is false.  
> reset-memory-stats (**optional**) - If true, any previously accumulated Memory profiling data will be discarded. If false, CPU data willbe accumulated across pairs of invocations of START_MEMORY_RECORDING and STOP_MEMORY_RECORDING. Default is false.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> snapshot-directory (**optional**) - Directory where the snapshot should be written (for SAVE_SNAPSHOT).  

### jmx-invocation
Allows to invoke JMX-exposed methods and attributes.
> continue-on-failure (**optional**) - Continue method invocations if an exception occurs. Default is false.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> expected-slave-result (**optional**) - Expected result value. If specified, results of method invocations are compared with this value.  
> expected-total-result (**optional**) - Expected result, calculated as sum/concatenation (with ',' delimeter) of results from individual slaves.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> method-parameters (**optional**) - Method parameters. If specified, the number of parameters must match the number of parameter signatures supplied.  
> method-signatures (**optional**) - Method parameter signatures.  
> operation-type (**optional**) - Type of action to be performed. Invocation of specified method (INVOKE_METHOD) is performed by default. Optionally, query for a specified attribute (via method-parameters) can be performed (GET_ATTRIBUTE_VALUE) or setting a specified attribute (via method-parameters) can be performed(SET_ATTRIBUTE_VALUE).  
> query (**mandatory**) - Method will be invoked on all ObjectInstances matching given query.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> target-name (**mandatory**) - Name of the method to be invoked / attribute to be queried for.  

### jvm-monitor-start
Starts collecting statistics locally on master and each slave node.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> interface-name (**optional**) - Specifies the network interface where statistics are gathered. If not specified, then statistics are not collected.  
> period (**optional**) - Period of statistics collection. The default is 1 second.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### jvm-monitor-stop
Stop collecting statistics on each slave node and return collected statistics to the master node.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### monitor-start
Starts collecting statistics locally on master and each slave node.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> interface-name (**optional**) - Specifies the network interface where statistics are gathered. If not specified, then statistics are not collected.  
> period (**optional**) - Period of statistics collection. The default is 1 second.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### monitor-stop
Stop collecting statistics on each slave node and return collected statistics to the master node.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### parallel-start-stop
The stage start and stops some nodes concurrently (without waiting for each other).
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> graceful (**optional**) - If set to false, the node crash should be simulated. By default node should be shutdown gracefully.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> may-fail-on (**optional**) - Set of slaves where the start may fail but this will not cause an error. Default is none.  
> reachable (**optional**) - Applicable only for cache wrappers with Partitionable feature. Set of slaves that should be reachable from the new node. Default is all slaves.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> start (**optional**) - Set of slaves which should be started in this stage. Default is empty.  
> start-delay (**optional**) - Delay before the slaves are started. Default is 0.  
> stop (**optional**) - Set of slaves which should be stopped in this stage. Default is empty.  
> stop-delay (**optional**) - Delay before the slaves are stopped. Default is 0.  
> stop-roles (**optional**) - Set of roles which should be stopped in this stage. Default is empty.  

### periodic-heap-dump
Periodically generates heap dumps.
> dir (**mandatory**) - Location on disk where the heap dumps should be stored.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> initial-delay (**optional**) - Delay before the first heap dump. Default is 0.  
> period (**optional**) - How often should be the heap dumps created. Default is every 30 minutes.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> stop (**optional**) - Set this flag to true in order to terminate the heap dumper. Default is false.  

### repeat-begin
DO NOT USE DIRECTLY. This stage is added at the beginning of each repeat.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> from (**optional**) - Initial counter value. Default is 0.  
> inc (**optional**) - Counter increment. Default is 1.  
> name (**optional**) - Repeat name. Default is none.  
> times (**optional**) - Sets from=0, to=times-1. Default is none.  
> to (**optional**) - Maximum counter value. Default is none.  

### repeat-continue
DO NOT USE DIRECTLY. This stage is added at the end of each repeat.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> from (**optional**) - Initial counter value. Default is 0.  
> inc (**optional**) - Counter increment. Default is 1.  
> name (**optional**) - Repeat name. Default is none.  
> times (**optional**) - Sets from=0, to=times-1. Default is none.  
> to (**optional**) - Maximum counter value. Default is none.  

### repeat-end
DO NOT USE DIRECTLY. This stage is added at the end of each repeat.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> from (**optional**) - Initial counter value. Default is 0.  
> inc (**optional**) - Counter increment. Default is 1.  
> name (**optional**) - Repeat name. Default is none.  
> times (**optional**) - Sets from=0, to=times-1. Default is none.  
> to (**optional**) - Maximum counter value. Default is none.  

### scenario-cleanup
DO NOT USE DIRECTLY. This stage is automatically inserted after the last stage in each scenario. You can alter the properties in &lt;cleanup/&gt element.
> check-memory (**optional**) - Specifies whether the check for amount of free memory should be performed. Default is true.  
> check-threads (**optional**) - Specifies whether the check for unfinished threads should be performed. Default is true.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> heap-dump-dir (**optional**) - Directory where the heap dump will be produced if the memory threshold is hit or some threads have not finished. By default the dump will not be produced.  
> memory-release-timeout (**optional**) - Timeout for releasing memory through garbage collections. Default is 30 seconds.  
> memory-threshold (**optional**) - If the available (free) memory after service destroy and System.gc() is below percentage specified in this property the benchmark will fail. Default is 95.  
> stop-timeout (**optional**) - Timeout for stopped threads to join. Default is 10 seconds.  
> stop-unfinished-threads (**optional**) - Calls Thread.stop() on threads that have not finished. Works only if checkThreads=true. Default is true.  

### scenario-destroy
DO NOT USE DIRECTLY. This stage is automatically inserted after the last stage in each scenario.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> graceful-stop-timeout (**optional**) - Timeout for the Lifecycle.stop() execution - if the stop() does not return within this timeout, Killable.kill() is called (if it is supported). Default is 30 seconds.  

### scenario-init
DO NOT USE DIRECTLY. This stage is automatically inserted before the beginning of scenario.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> heap-dump-dir (**optional**) - Directory where the heap dump will be produced. Contrary to scenario-cleanup, if this directory is set, the heap dump is written always. By default the dump will not be produced.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### service-start
Starts services on specified slaves
> cluster-formation-timeout (**optional**) - Time allowed the cluster to reach `expectNumSlaves` members. Default is 3 minutes.  
> delay-after-first-slave-starts (**optional**) - Delay (staggering) after first slave's start is initiated. Default is 5s.  
> delay-between-starting-slaves (**optional**) - Delay between initiating start of i-th and (i+1)-th slave. Default is 500 ms.  
> dump-config (**optional**) - Collect configuration files and properties for the service, and pass those to reporters. Default is true.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> expect-num-slaves (**optional**) - The number of members that should be up after all services are started. Applicable only with validateCluster=true. Default is all members in the group where this stage will be executed. (If no groups are configured, then this is equal to all members of the cluster.) If multiple groups arespecified in the benchmark, then the size of each group will considered separately.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> may-fail-on (**optional**) - Set of slaves where the start may fail but this will not cause an error. Default is none.  
> reachable (**optional**) - Set of slaves that should be reachable to the newly spawned slaves (see Partitionable feature for details). Default is all slaves.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> stagger-slave-startup (**optional**) - If set to true, the slaves will not be started in one moment but the startup will be delayed. Default is true.  
> validate-cluster (**optional**) - Specifies whether the cluster formation should be checked after cache wrapper startup. Default is true.  

### service-stop
Stops or kills (simulates node crash) one or more nodes.
> async (**optional**) - If set to true the benchmark will not wait until the node is stopped. Default is false.  
> delay-execution (**optional**) - If this value is positive the stage will spawn a thread which will stop the node after the delay. The stage will not wait for anything. By default the stop is immediate and synchronous.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> graceful (**optional**) - If set to false, the node crash should be simulated. By default node should be shutdown gracefully.  
> graceful-stop-timeout (**optional**) - Timeout for the Lifecycle.stop() execution - if the stop() does not return within this timeout, Killable.kill() is called (if it is supported). Default is 2 minutes.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  
> wait-for-delayed (**optional**) - If set, the stage will not stop any node but will wait until the delayed execution is finished. Default is false.  

### set-log-level
Debugging stage: changes log priorities
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> package (**mandatory**) - The package or class which should be affected.  
> pop (**optional**) - If set to true, instead of setting the priority directly just undo the last priority change. Default is false.  
> priority (**optional**) - The new priority that should be used. No defaults.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### set-partitions
Partitions the cluster into several parts that cannot communicate.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> partitions (**mandatory**) - Set of sets of partitions, e.g. [0,1],[2] makes two partitions, one with slaves 0 and 1 and second with slave 2 alone.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

### sleep
Sleeps specified number of milliseconds.
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> time (**mandatory**) - Sleep duration.  

### stack-trace-watchdog
Debug usage only. Periodically check for all thread stack traces and print them out.
> async-logging (**optional**) - If set to true the watchdog will not use standard logging for output but will push the output to queue consumed (logged) by another thread. Default is false.  
> exit-on-failure (**optional**) - If true, then the benchmark stops when the stage returns an error. If false, then the stages in the current scenario are skipped, and the next scenario starts executing. Default is false.  
> groups (**optional**) - Specifies in which groups this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all groups.  
> mask (**optional**) - If set, only those threads which have this mask in the name will be checked. Default is not set.  
> only-stuck (**optional**) - By default the check will print out only those threads which appear to be stuck. If this is set to false all threads will be printed out. Default is true.  
> period (**optional**) - The delay between consecutive checks. Default is 10 seconds.  
> roles (**optional**) - Specifies on which slaves this stage should actively run, by their roles. The result set is intersection of specified slaves, groups and roles. Supported roles are [COORDINATOR]. Default is all roles.  
> short-stack (**optional**) - Threads with stack lower or equal to this value are never printed (because usually such threads are parked in thread pools). Default is 10.  
> slaves (**optional**) - Specifies on which slaves this stage should actively run. The result set is intersection of specified slaves, groups and roles. Default is all slaves.  

