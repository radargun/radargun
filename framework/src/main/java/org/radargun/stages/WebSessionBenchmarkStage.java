package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.state.MasterState;
import org.radargun.stressors.PutGetStressor;
import org.radargun.stressors.StringKeyGenerator;
import org.radargun.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Double.parseDouble;
import static org.radargun.utils.Utils.numberFormat;

/**
 * Simulates the work with a distributed web sessions.
 *
 * @author Mircea.Markus@jboss.com
 */
public class WebSessionBenchmarkStage extends AbstractDistStage {

   private static final String SIZE_INFO = "SIZE_INFO";
   private int opsCountStatusLog = 5000;

   public static final String SESSION_PREFIX = "SESSION";

   /**
    * total number of request to be made against this session: reads + writes
    */
   private int numberOfRequests = 50000;

   /**
    * for each session there will be created fixed number of attributes. On those attributes all the GETs and PUTs are
    * performed (for PUT is overwrite)
    */
   private int numberOfAttributes = 100;

   /**
    * Each attribute will be a byte[] of this size
    */
   private int sizeOfAnAttribute = 1000;

   /**
    * Out of the total number of request, this define the frequency of writes (percentage)
    */
   private int writePercentage = 20;


   /**
    * the number of threads that will work on this slave
    */
   private int numOfThreads = 10;

   private boolean reportNanos = false;

   private String keyGeneratorClass = StringKeyGenerator.class.getName();


   protected CacheWrapper cacheWrapper;

   private boolean useTransactions = false;

   private boolean commitTransactions = true;

   private int transactionSize = 1;

   private long durationMillis = -1;

   protected Map<String, String> doWork() {
      log.info("Starting "+getClass().getSimpleName()+": " + this);
      PutGetStressor putGetStressor = new PutGetStressor();
      putGetStressor.setNodeIndex(getSlaveIndex());
      putGetStressor.setNumberOfAttributes(numberOfAttributes);
      putGetStressor.setNumberOfRequests(numberOfRequests);
      putGetStressor.setNumOfThreads(numOfThreads);
      putGetStressor.setOpsCountStatusLog(opsCountStatusLog);
      putGetStressor.setSizeOfAnAttribute(sizeOfAnAttribute);
      putGetStressor.setWritePercentage(writePercentage);
      putGetStressor.setKeyGeneratorClass(keyGeneratorClass);
      putGetStressor.setUseTransactions(useTransactions);
      putGetStressor.setCommitTransactions(commitTransactions);
      putGetStressor.setTransactionSize(transactionSize);
      putGetStressor.setDurationMillis(durationMillis);
      return putGetStressor.stress(cacheWrapper);
   }
   
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = new DefaultDistStageAck(slaveIndex, slaveState.getLocalAddress());
      this.cacheWrapper = slaveState.getCacheWrapper();
      if (cacheWrapper == null) {
         log.info("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      try {
         Map<String, String> results = doWork();
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
      masterState.put("results", results);
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
   public void setNumberOfRequests(int numberOfRequests) {
      this.numberOfRequests = numberOfRequests;
   }

   public void setNumberOfAttributes(int numberOfAttributes) {
      this.numberOfAttributes = numberOfAttributes;
   }

   public int getOpsCountStatusLog() {
      return opsCountStatusLog;
   }

   public int getNumberOfRequests() {
      return numberOfRequests;
   }

   public int getNumberOfAttributes() {
      return numberOfAttributes;
   }

   public int getSizeOfAnAttribute() {
      return sizeOfAnAttribute;
   }

   public int getWritePercentage() {
      return writePercentage;
   }

   public int getNumOfThreads() {
      return numOfThreads;
   }

   public boolean isReportNanos() {
      return reportNanos;
   }

   public void setSizeOfAnAttribute(int sizeOfAnAttribute) {
      this.sizeOfAnAttribute = sizeOfAnAttribute;
   }

   public void setNumOfThreads(int numOfThreads) {
      this.numOfThreads = numOfThreads;
   }

   public void setReportNanos(boolean reportNanos) {
      this.reportNanos = reportNanos;
   }

   public void setWritePercentage(int writePercentage) {
      this.writePercentage = writePercentage;
   }

   public void setOpsCountStatusLog(int opsCountStatusLog) {
      this.opsCountStatusLog = opsCountStatusLog;
   }

   public String getKeyGeneratorClass() {
      return keyGeneratorClass;
   }

   public void setKeyGeneratorClass(String keyGeneratorClass) {
      this.keyGeneratorClass = keyGeneratorClass;
   }

   public int getTransactionSize() {
      return transactionSize;
   }

   public void setTransactionSize(int transactionSize) {
      this.transactionSize = transactionSize;
   }

   public boolean isUseTransactions() {
      return useTransactions;
   }

   public void setUseTransactions(boolean useTransactions) {
      this.useTransactions = useTransactions;
   }

   public boolean isCommitTransactions() {
      return commitTransactions;
   }

   public void setCommitTransactions(boolean commitTransactions) {
      this.commitTransactions = commitTransactions;
   }

   public long getDurationMillis() {
      return durationMillis;
   }

   public void setDuration(String duration) {
      this.durationMillis = Utils.string2Millis(duration);
   }

   @Override
   public String toString() {
      return "WebSessionBenchmarkStage {" +
            "opsCountStatusLog=" + opsCountStatusLog +
            ", numberOfRequests=" + numberOfRequests +
            ", numberOfAttributes=" + numberOfAttributes +
            ", sizeOfAnAttribute=" + sizeOfAnAttribute +
            ", writePercentage=" + writePercentage +
            ", numOfThreads=" + numOfThreads +
            ", reportNanos=" + reportNanos +
            ", cacheWrapper=" + cacheWrapper +
            ", useTransactions=" + useTransactions +
            ", commitTransactions=" + commitTransactions +
            ", transactionSize=" + transactionSize +
            ", durationMillis=" + durationMillis+
            ", " + super.toString();
   }
}
