---
---

Understanding results in RadarGun 3
-----------------------------------

### What's wrong about how RadarGun 2.x measures response times

The stress tests used in Radargun 2.x (the logic mostly in [TestStage](https://github.com/radargun/radargun/blob/branch_2.x/core/src/main/java/org/radargun/stages/test/TestStage.java) and its inheritors, and [Stressor](https://github.com/radargun/radargun/blob/branch_2.x/core/src/main/java/org/radargun/stages/test/Stressor.java)) share a flaw that appears in many benchmark tools. These tools try to simulate unlimited amount of users, executing requests (or sequences of requests - *conversations*) using fixed number of threads.

Currently, we set fixed number of threads and (when we set non-zero request `period`) each of them follows this pattern:

    long startTime = currentTime();
        for (long request = 1; ; ++request) {
            doRequest();
            long nextRequestTime = startTime + request * period;
            long sleepTime = nextRequestTime - currentTime();
            if (sleepTime > 0) {
            sleep(sleepTime);
        }
    }

This works as long as all the requests take shorter time than `period`, but this is not true in practice. As an example imagine that `period` = 10ms, regular request takes 2ms but a stalled request takes 35ms. When we're benchmarking for 100ms (for the sake of this example), we can execute e.g. 4x 2ms (at time 0, 10, 20 and 30), then at 40 the 35ms one, then another 4 at 75, 77, 79 and 81 and last one at 90. The average response time would be `(9 * 2 + 35) / 10 = 5.3ms`, but this is not correct.

The thread that is stalled for 35 ms is often holding a lock (or occupying worker thread - any kind of synchronization) on the service and therefore it can block the other requests.
If users requested our service uniformly (every 10ms), they would experience latencies 2, 2, 2, 2, 35, 27, 19, 11, 3 and 2ms. This gives us average response time `5 * 2 + 35 + 27 + 19 + 11 + 3 / 10 = 10.5ms` which is almost twice the previous result. And this is what users would actually experience.

Average response time is not the only property we measure, but others are skewed as well by our way of sampling the requests.

So, current stress tests don't report response times correctly. Do these provide precise value for throughput? Yes, we have actually been able to execute X requests in Y minutes, but that's all. It is not the maximum throughput possible; for determining maximum throughput we need to scale up the load = lower the period of requests or increase the number of threads. But finding out the maximum throughput takes several iterations. We can also see throughput when the server is overloaded, although the response times are in this situation even further from users experienced response times.

In the past we also used tests with no request period whatsoever (this configuration option is preserved), just having fixed number of threads executing requests as fast as possible. This is a degenerated case of the above; we even don't attempt to set certain load on the servers, the response time defines the load through backpressure. So you can compare two services at certain *concurrency*, but not at given load. Another glitch is that you can execute different types of requests, with certain probabilities. If the same thread executes different requests, one slow type limits the number of executed requests of another type.

When designing/configuring a benchmark, always ask yourselves: what do I want to measure? You can't measure just *performance* - that's not a *quantity*. Common things you'd like to see is:


* response time (average and distribution) under certain load
  ** meeting certain threshold may be more important than the actual values; your SLA can tell e.g. 90% requests < 1ms, 99% < 100ms
* maximum throughput: in any test, you need to increase the load until the service can't handle that
  ** not handling the load can either mean not meeting certain performance criteria (SLA), or just not getting the responses at all (usually you get an exceptional response such as timeout) - that means that you're not actually executing the desired load
* compare different services/versions under the same load: backpressure must not affect the test itself, otherwise you are not comparing apples to apples
* scale cluster size (or any resource that can be scaled)
  ** keep the load constant or scale load along with the resources, and see how response times change
* compare maximum throughput of different versions or with different number of responses: measure maximum throughput independently and compare the result values


### How to measure response time

One user does not prevent another user from issuing a request, so we have to do the same. As the users keep coming, we would need unlimited number of threads - though, that's not really practical. [This PR](https://github.com/radargun/radargun/pull/236) tries to solve it.

Each test requires at least two stages: *setup* stage, in which we set the target throughput and start executing the requests and spawn threads, and *test* stage when the test should be in *steady state* - we execute the requests with requested frequency and do not spawn any additional threads. We measure and record response times only during the *test*, not in *setup*. These pairs of stages can be chained (for tests that seek the maximum throughput or just want to acquire data at different load levels).

For each *conversation* (sequence of requests), the setup stage specifies request period and number of invocations in this period. This is a base for the schedule (actually implemented in the `SchedulingSelector`); the threads are blocked in the selector until the schedule assigns them to execute a conversation. When the threads get its task assigned, it checks if there are another threads (configurable number, but at least 1) waiting for next conversation. If this condition is satisfied, it executes its conversation and waits for next one in the selector. If it is not satisfied, the behavior differs for *setup* and for *test*:

During *setup*, the thread spawns another sibling, since no threads waiting for next conversation means that all threads are occupied and a scheduled conversation might be missed. Spawning a thread can take some time, but we don't care about missed operations during *setup*. The stage has configurable minimum duration and steady period: the setup is finished when there are no more threads created in last X seconds (defined by this steady period). There is also a cap on the number of threads that prevents RadarGun from running out of memory. This cap is reached when the requests stall too long and in this case the setup (and whole test) fails. Reaching maximum number of threads is in fact the only way for the setup to fail - service's inability to handle the target throughput results in spawning more and more threads.

During *test*, we don't want to spawn any more threads. Attempt to create another thread means that we can't reach the desired throughput; the setup has not created enough threads as it has not experienced all the possible situations (it was too short). Therefore, the test fails and you need to reconfigure the benchmark.

It can happen that the scheduled quantum of conversations is not retrieved from `SchedulingSelector` in target period, although there are threads waiting for next operations. This happens quite usually even if the CPU load on machine is low, in practice I have observed that about 2-3% of requests are not executed on time. We do not re-schedule those operations for later, just ignore them. In the report, you can then see that this lower number of requests - at this point it's up to the user to validate if the test is acceptable or not, in future we might issue more warnings/invalidate the test if we miss too many of them.

At this point, old logic was mostly preserved and moved to `legacy` package, with stages having `legacy` in their names, too. We might consider keeping the old names and having them in separate legacy XML namespace.

For more information about the subject I highly recommend watching [Gil Tene's presentation about latency](https://www.youtube.com/watch?v=9MKY4KypBzg). It also gives hints for understanding the percentiles chart that RadarGun reports (when using histogram-based statistics). Note that in this presentation Gil mentions *service time* and *response time*; in RadarGun we can measure only response time of the service implementation, if the request goes remotely, RadarGun can't know about that.

### The concept mapped to the code

As the test is spread over multiple stages, its state is preserved in `RunningTest`. This works as the thread-pool implementation;  it's the `RunningTest.ThreadPoolingSelector` checks the number of threads waiting for next conversation and possibly lets the `RunningTest` add a new thread.

Conversations are mapped to `Conversation`. Stages do not define the `ConversationSelector` directly anymore, instead they use `SchedulingSelector.Builder` to define the frequency of conversations. When defining stress tests on new traits, inherit from `TestSetupStage`. `TestStage` needs to be overridden only in cases that you want to gather other information than just statistics (invocations' response times). When chaining multiple tests (changing the frequency of operations), set `<test finish="false"/>` and then use `<finish-test/>` in the end.


