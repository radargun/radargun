---
---

## Measuring response times with synchronous API

A benchmark is always a simplification of the interaction actual users would have with the system. Usually we try to simulate high number of users accessing the system under test (_SUT_) concurrently with rather limited resources: 80,000 users executing a request every minute can be simulated by 4 nodes (_clients_ or _drivers_) with 100 threads each executing a request every 5 milliseconds. When the interaction involves a sequence of operations (a _conversation_) the timing gets even more complex, we can speak of

* cycle time: the delay between starting request _n_ and _n+1_
* think time: the delay between getting response for request _n_ and starting request _n+1_

> RadarGun does not always strictly isolate the SUT and drivers, it's not possible when we're benchmarking embedded API. We can only try to make drivers as lightweight as possible.

In case of cycle time, the problematic part is what shall we do when the response time exceeds the cycle time - we find that we should have already fired another request, so we can start it immediately and calculate the response time as the difference between the time when we should have sent the request according to schedule and the time when we receive the response, or put our head to the sand and use just the actual request duration. We could also cancel the request completely, starting only at next available schedule. All variants are flawed - the SUT is affecting the way how we fire the requests.

Think time has this property even without a schedule; we cannot compare behaviour of two systems under the same load because we're not generating the same load. A common case of this is think time zero: executing as many requests as possible, limiting only the _concurrency_ by setting the total number of threads driving the load. Another glitch appears when you execute different types of requests, with certain probabilities. If the same thread executes different requests, one slow type limits the number of executed requests of another type - this is explained in [Understanding results]({{page.path_to_root}}measuring_performance/understanding_results.html).

In such setup we can see that we have been able to execute X requests in Y minutes, but that's all. Response times are skewed (we have likely not executed the request at the moment when the server was blocked). We don't observe maximum throughput; for determining maximum throughput we need to scale up the load = lower the period of requests or increase the number of threads. But finding out the maximum throughput takes several iterations. We can also see throughput when the server is overloaded, although the response times are in this situation even further from users experienced response times.

Such behaviour is inherent to any synchronous API, e.g. the one provided by [BasicOperations](https://github.com/radargun/radargun/blob/master/extensions/cache/src/main/java/org/radargun/traits/BasicOperations.java). Java applications tend to get stalled even for seconds (due to GC activity), application can also get blocked due to e.g. thread performing I/O while holding a lock. These cases may be rare, but if we want to execute 10,000 operations per second and we experience one second pause (possibly on another node), with synchronous API we would need 10,000 threads (we want to start the operations according to the schedule while we have not finished the previous ones). Such setup may not be realistic given our resources.

When designing/configuring a benchmark, always ask yourselves: what do I want to measure? You can't measure just _performance_ - that's not a _quantity_. Common things you'd like to see is:
* response time (average and distribution) under certain load
** meeting certain threshold may be more important than the actual values; your SLA can tell e.g. 90% requests < 1ms, 99% < 100ms
* maximum throughput: in any test, you need to increase the load until the service can't handle that
** not handling the load can either mean not meeting certain performance criteria (SLA), or just not getting the responses at all (usually you get an exceptional response such as timeout) - that means that you're not actually executing the desired load
* compare different services/versions under the same load: backpressure must not affect the test itself, otherwise you are not comparing apples to apples
* scale cluster size (or any resource that can be scaled)
** keep the load constant or scale load along with the resources, and see how response times change
* compare maximum throughput of different versions or with different number of responses: measure maximum throughput independently and compare the result values

## Asynchronous tests

If we want to avoid needing those thousands of threads waiting for a stalled responses, we need an asynchronous API, one that clearly separates starting a request and receiving the response. Since Java 8, the answer for this is the _CompletableFuture_. Therefore, we have added async Traits that almost immediately return CompletableFuture and guarantee not blocking the thread that executes it. This way we need only small threadpool that starts those request, and the rest is up to the actual application.

>Using async API is not a new invention: [Gatling](http://gatling.io/) based on the Akka actor framework also requires the requests to be non-blocking. Since its home domain are HTTP request which have a long history of asynchronous API, the situation is a bit easier than in the Caching domain where RadarGun has grown (JSR-107 - JCache does not define async operations, other RPC frameworks also go by sync-as-default).

We expect that an implementation will be asynchronous internally, too - delegating the task to a limited threadpool which just executes the synchronous operation would just shift the problem. At worst the implementation should use unlimited queues that will guarantee that RadarGun thread won't be blocked.

The `AsyncTest.numThreads` defines the size of the pool of threads starting the requests. Each test then defines a request period and number of invocations in this period. This is a base for the schedule (actually implemented in the `SchedulingSelector`); the threads are blocked in the selector until the schedule assigns them to start a conversation.

The scheduled quantum of conversations may not be retrieved from `SchedulingSelector` in target period, either because all the threads are occupied or these were not woken up on time. This happens quite usually even if the CPU load on machine is low, in practice I have observed that about 2-3% of requests are not executed on time. We do not re-schedule those operations for later, just ignore them. In the report, you can then see that this lower number of requests - at this point it's up to the user to validate if the test is acceptable or not, in future we might issue more warnings/invalidate the test if we miss too many of them.

For more information about the subject I highly recommend watching [Gil Tene's presentation about latency](https://www.youtube.com/watch?v=9MKY4KypBzg). It also gives hints for understanding the percentiles chart that RadarGun reports (when using histogram-based statistics). Note that in this presentation Gil mentions _service time_ and _response time_; in RadarGun we can measure only response time of the service implementation, if the request goes remotely, RadarGun can't know about that.

