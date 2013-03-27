package org.radargun.stages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.state.MasterState;
import org.radargun.stressors.CacheSpecificKeyGenStressor;
import org.radargun.stressors.StressTestStressor;
import org.radargun.stressors.StringKeyGenerator;

import static java.lang.Double.parseDouble;
import static org.radargun.utils.Utils.numberFormat;

/**
 * Simulates the work with a distributed web sessions.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Benchmark where several client threads access cache limited by time or number of requests.",
      deprecatedName = "WebSessionBenchmark")
public class StressTestStage extends AbstractDistStage {

   private static final String SIZE_INFO = "SIZE_INFO";

   public static final String SESSION_PREFIX = "SESSION";

   @Property(doc = "Number of operations after which a log entry should be written. Default is 5000.")
   protected int opsCountStatusLog = 5000;

   @Property(doc = "Total number of request to be made against this session: reads + writes. If duration " +
         "is specified this value is ignored. Default is 50000.", deprecatedName = "numberOfRequests")
   protected int numRequests = 50000;

   @Property(doc = "Number of key-value entries per each client thread which should be used. Default is 100.",
         deprecatedName = "numberOfAttributes")
   protected int numEntries = 100;

   @Property(doc = "Size of the value in bytes. Default is 1000.", deprecatedName = "sizeOfAnAttribute")
   protected int entrySize = 1000;

   @Property(doc = "Ratio of writes = PUT requests (percentage). Default is 20%")
   protected int writePercentage = 20;

   @Property(doc = "The frequency of removes (percentage). Default is 0%")
   private int removePercentage = 0;

   @Property(doc = "In case we test replace performance, the frequency of replaces that should fail (percentage). Default is 40%")
   private int replaceInvalidPercentage = 40;

   @Property(doc = "The number of threads that will work on this slave. Default is 10.", deprecatedName = "numOfThreads")
   protected int numThreads = 10;

   @Property(doc = "Full class name of the key generator. Default is org.radargun.stressors.StringKeyGenerator.")
   protected String keyGeneratorClass = StringKeyGenerator.class.getName();

   @Property(doc = "Specifies if the requests should be explicitely wrapped in transactions. By default" +
         "the cachewrapper is queried whether it does support the transactions, if it does," +
         "transactions are used, otherwise these are not.")
   protected Boolean useTransactions = null;

   @Property(doc = "Specifies whether the transactions should be committed (true) or rolled back (false). " +
         "Default is true")
   protected boolean commitTransactions = true;

   @Property(doc = "Number of requests in one transaction. Default is 1.")
   protected int transactionSize = 1;

   @Property(doc = "Number of keys inserted/retrieved within one operation. Applicable only when the cache wrapper" +
         "supports bulk operations. Default is 1 (no bulk operations).")
   private int bulkSize = 1;

   @Property(converter = TimeConverter.class, doc = "Benchmark duration. This takes precedence over numRequests. By default switched off.")
   protected long duration = -1;

   @Property(doc = "By default each client thread operates on his private set of keys. Setting this to true " +
         "introduces contention between the threads, the numThreads property says total amount of entries that are " +
         "used by all threads. Default is false.")
   protected boolean sharedKeys = false;

   @Property(doc = "The keys can be fixed for the whole test run period or we the set can change over time. Default is true = fixed.")
   protected boolean fixedKeys = true;

   @Property(doc = "If true, putIfAbsent and replace operations are used. Default is false.")
   protected boolean useAtomics = false;

   @Property(doc = "Specifies whether the key generator is produced by a cache wrapper and therefore is product-specific. Default is false.")
   protected boolean cacheSpecificKeyGenerator = false;

   protected CacheWrapper cacheWrapper;

   protected Map<String, Object> doWork() {
      log.info("Starting "+getClass().getSimpleName()+": " + this);
      StressTestStressor stressor = null;
      if (cacheSpecificKeyGenerator) {
         stressor = new CacheSpecificKeyGenStressor();
      } else {
         stressor = new StressTestStressor();
         stressor.setKeyGeneratorClass(keyGeneratorClass);
      }
      stressor.setNodeIndex(getSlaveIndex(), getActiveSlaveCount());
      stressor.setDurationMillis(duration);
      PropertyHelper.copyProperties(this, stressor);
      return stressor.stress(cacheWrapper);
   }
   
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = new DefaultDistStageAck(slaveIndex, slaveState.getLocalAddress());
      if (slaves != null && !slaves.contains(slaveIndex)) {
         log.info(String.format("The stage should not run on this slave (%d): slaves=%s", slaveIndex, slaves));
         return result;
      }
      this.cacheWrapper = slaveState.getCacheWrapper();
      if (cacheWrapper == null) {
         log.info("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      try {
         Map<String, Object> results = doWork();
         String sizeInfo = generateSizeInfo();
         log.info(sizeInfo);
         results.put(SIZE_INFO, sizeInfo);
         result.setPayload(results);
         return result;
      } catch (Exception e) {
         log.warn("Exception while initializing the test", e);
         result.setError(true);
         result.setRemoteException(e);
         return result;
      }
   }

   /**
    * Important: do not change the format of rhe log below as is is used by ./dist.sh to measure distribution load.
    */
   private String generateSizeInfo() {
      return "size info: " + cacheWrapper.getInfo() + ", clusterSize:" + super.getActiveSlaveCount() + ", nodeIndex:" + super.getSlaveIndex() + ", cacheSize: " + cacheWrapper.getLocalSize();
   }

   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      logDurationInfo(acks);
      boolean success = true;
      Map<Integer, Map<String, Object>> results = new HashMap<Integer, Map<String, Object>>();
      masterState.put(CsvReportGenerationStage.RESULTS, results);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck wAck = (DefaultDistStageAck) ack;
         if (wAck.isError()) {
            success = false;
            log.warn("Received error ack: " + wAck);
         } else {
            if (log.isTraceEnabled())
               log.trace(wAck);
         }
         Map<String, Object> benchResult = (Map<String, Object>) wAck.getPayload();
         if (benchResult != null) {
            results.put(ack.getSlaveIndex(), benchResult);
            Object reqPerSes = benchResult.get("REQ_PER_SEC");
            if (reqPerSes == null) {
               throw new IllegalStateException("Requests per second should be present!");
            }
            logForDistributionCounting(benchResult);
            log.info("Slave #" + ack.getSlaveIndex() + ": " + numberFormat(parseDouble(reqPerSes.toString())) + " requests per second.");
         } else {
            log.trace("No report received from slave: " + ack.getSlaveIndex());
         }
      }
      return success;
   }

   /**
    * Important: don't change the format of the log below as it is used by ./dist.sh in order to count the load
    * distribution in the cluster.
    */
   private void logForDistributionCounting(Map<String, Object> benchResult) {
      log.info("Received " +  benchResult.remove(SIZE_INFO));
   }
}
