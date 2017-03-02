---
---

        <h3>
          <a id="user-content-note-this-document-is-related-to-radargun-1x" class="anchor" href="#note-this-document-is-related-to-radargun-1x" aria-hidden="true"></a>Note: this document is related to RadarGun 1.x</h3>

        <p>RadarGun 2.0 brings new way of collecting and reporting the statistics. Description of these and migration guide is TODO.</p>

        <hr>

        <h2>
          <a id="user-content-overview" class="anchor" href="#overview" aria-hidden="true"></a>Overview</h2>

        <p>In the CsvGenerationStage executed after StressTestStage RadarGun produces some CSV files with the results. Here we describe the meaning of the columns - some of them may not be present.</p>

        <p>Below are the general columns not related to any request type:</p>

        <ul>
          <li>SLAVE_INDEX: the node which produced this row</li>
          <li>ITERATION: In ClientStressTestStage or when using StressTest.statisticsPeriod &gt; 0 the test execution is divided into several iterations</li>
          <li>DURATION: Total amount of nanoseconds all requests took. The stressor threads do only minimal amount of work besides the requests themselves, therefore, this number should be near to the test duration * number of threads.</li>
          <li>FAILURES: Amount of requests that have failed</li>
          <li>THREADS: Amount of stressor threads participating in the test</li>
          <li>REQ_PER_SEC: If the node executed only these requests (including transactions), how many of them would it execute. Computed THREADS * TOTAL_REQUESTS / DURATION (scaled to requests per second).</li>
          <li>REQ_PER_SEC_NET: If the node executed only these requests (and without transactions), how many of them would it execute. This is computed in the same way as REQ_PER_SEC but we don't include the the time spend starting and committing/rolling back transactions into the DURATION part.</li>
        </ul>

        <p>There are several requests type that the StressTest may issue depending on the configuration - sometimes the request type includes the result value:</p>

        <ul>
          <li>READ, READ_NULL: Read returning existing value or null, respectively</li>
          <li>WRITE</li>
          <li>REMOVE</li>
          <li>REMOVE_VALID, REMOVE_INVALID: conditional remove which is expected to succeed or fail, respectively</li>
          <li>PUT_IF_ABSENT_IS_ABSENT, PUT_IF_ABSENT_NOT_ABSENT</li>
          <li>REPLACE_VALID, REPLACE_INVALID: conditional remove which is expected to succeed or fail, respectively</li>
          <li>GET_ALL: native implementation of bulk read</li>
          <li>GET_ALL_VIA_ASYNC: bulk read implemented by several asynchronous requests</li>
          <li>PUT_ALL: native implementation of bulk write</li>
          <li>PUT_ALL_VIA_ASYNC: bulk write implemented by several asynchronous requests</li>
          <li>REMOVE_ALL: native implementation of bulk remove</li>
          <li>REMOVE_ALL_VIA_ASYNC: bulk remove implemented by several asynchronous requests</li>
          <li>QUERY</li>
          <li>TRANSACTION: this "request type" gathers all the time spent starting and committing/rolling back transactions</li>
        </ul>

        <p>For each request type there are several values:</p>

        <ul>
          <li>_REQUEST__COUNT: total number of requests</li>
          <li>_REQUEST__ERRORS: number of failed requests</li>
          <li>_REQUEST__DURATION_NET: time spent executing the requests themselves, without transactional overhead, in nanoseconds</li>
          <li>_REQUEST__TX_OVERHEAD: time spent starting and ending transactions, in nanoseconds. </li>
          <li>_REQUEST_S_PER_SEC: THREADS * _REQUEST__COUNT / _REQUEST__DURATION_TX (scaled to requests per second). This value is imprecise for transactions which include multiple requests because currently the transactional overhead is added only to the last request in the transaction.</li>
          <li>_REQUEST_S_PER_SEC_NET: THREADS * _REQUEST__COUNT / _REQUEST__DURATION_NET (scaled to requests per second)</li>
          <li>_REQUEST__MEAN_NET/_REQUEST__AVG_NET: Average request duration in nanoseconds</li>
          <li>_REQUEST__MEAN_TX/_REQUEST__AVG_TX: Average request + transaction duration in nanoseconds</li>
          <li>_REQUEST__M2_NET/_REQUEST__M2_TX: sum of (x - mean)^2. Standard error is sqrt(M2/(COUNT - 1)). Used in further statistics aggregation.</li>
        </ul>

        <h3>
          <a id="user-content-example-1-non-transactional" class="anchor" href="#example-1-non-transactional" aria-hidden="true"></a>Example 1: Non-transactional</h3>

        <ul>
          <li>WRITE takes 10 ms</li>
        </ul>

        <p>Result: WRITES_PER_SEC_NET=100, WRITES_PER_SEC=100</p>

        <h3>
          <a id="user-content-example-2-transactional-single-request-per-transaction" class="anchor" href="#example-2-transactional-single-request-per-transaction" aria-hidden="true"></a>Example 2: Transactional, single request per transaction</h3>

        <ul>
          <li>START_TX takes 1 ms</li>
          <li>WRITE takes 2 ms</li>
          <li>COMMIT_TX takes 7 ms</li>
        </ul>

        <p>Result: WRITES_PER_SEC_NET=500, WRITES_PER_SEC=100 (the whole sequence took 10 ms), TRANSACTIONS_PER_SEC=100</p>

        <h3>
          <a id="user-content-example-3-transactional-multiple-requests-per-transaction" class="anchor" href="#example-3-transactional-multiple-requests-per-transaction" aria-hidden="true"></a>Example 3: Transactional, multiple requests per transaction</h3>

        <ul>
          <li>START_TX takes 1 ms</li>
          <li>READ takes 5 ms</li>
          <li>WRITE takes 2 ms</li>
          <li>COMMIT_TX takes 17 ms</li>
        </ul>

        <p>Here it is a bit more complicated. It's unclear how the transaction overhead should be accounted to the two requests - therefore, the last command in the transaction is chosen.
          Result: READS_PER_SEC_NET=200, READS_PER_SEC=200, WRITES_PER_SEC_NET=500, WRITES_PER_SEC=50, TRANSACTIONS_PER_SEC=40</p>

        <p>In standard stress test with multiple requests per transaction the requests are generated in random order, therefore, the _REQUEST_S_PER_SEC would differ from _REQUEST_S_PER_SEC_NET for all request types. This may be unfair for some requests, e.g. the READ may not alter the commit processing in any way but still, in some percentage of cases the transaction overhead will be accounted to the READs. Therefore, you should rather consider the TRANSACTIONS_PER_SEC as the only meaningful result, or the _REQUEST_S_PER_SEC_NET values.</p>


