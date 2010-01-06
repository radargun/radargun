package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;
import org.cachebench.stressors.PutGetStressor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates the work with a distributed web sessions.
 *
 * @author Mircea.Markus@jboss.com
 */
public class WebSessionBenchmarkStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(WebSessionBenchmarkStage.class);

   private int opsCountStatusLog = 5000;

   public static final String SESSION_PREFIX = "SESSION";
   public static final String ATTRIBUTE_PREFIX = "ATTRIBUTE";


   /**
    * total number of request to be made against this session: reads + writes
    */
   private int numberOfRequestsPerThread = 50000;

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


   private CacheWrapper cacheWrapper;

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = new DefaultDistStageAck(slaveIndex, slaveState.getLocalAddress());
      this.cacheWrapper = slaveState.getCacheWrapper();
      if (cacheWrapper == null) {
         log.info("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      log.info("Starting WebSessionBenchmarkStage: " + this.toString());

      PutGetStressor putGetStressor = new PutGetStressor();
      putGetStressor.setBucketPrefix(SESSION_PREFIX + "_" + getSlaveIndex());
      putGetStressor.setKeyPrefix(ATTRIBUTE_PREFIX);
      putGetStressor.setNumberOfAttributes(numberOfAttributes);
      putGetStressor.setNumberOfRequestsPerThread(numberOfRequestsPerThread);
      putGetStressor.setNumOfThreads(numOfThreads);
      putGetStressor.setOpsCountStatusLog(opsCountStatusLog);
      putGetStressor.setSizeOfAnAttribute(sizeOfAnAttribute);
      putGetStressor.setWritePercentage(writePercentage);

      try {
         Map<String, String> results = putGetStressor.stress(cacheWrapper);
         result.setPayload(results);
         return result;
      } catch (Exception e) {
         log.warn("Exception while initializing the test", e);
         result.setError(true);
         result.setRemoteException(e);
      }
      return result;
   }

   public boolean processAckOnMaster(List<DistStageAck> acks) {
      logDurationInfo(acks);
      boolean success = true;
      Map<Integer, Map<String, Object>> results = new HashMap<Integer, Map<String, Object>>();
      masterState.put("results", results);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck wAck = (DefaultDistStageAck) ack;
         if (wAck.isError()) {
            success = false;
            log.warn("Received error ack: " + wAck, wAck.getRemoteException());
         } else {
            if (log.isTraceEnabled())
               log.trace(wAck);
         }
         Map<String, Object> benchResult = (Map<String, Object>) wAck.getPayload();
         if (benchResult != null) {
            results.put(ack.getSlaveIndex(), benchResult);
         } else {
            log.trace("No report received from slave: " + ack.getSlaveIndex());
         }
      }
      return success;
   }

   public void setNumberOfRequestsPerThread(int numberOfRequestsPerThread) {
      this.numberOfRequestsPerThread = numberOfRequestsPerThread;
   }

   public void setNumberOfAttributes(int numberOfAttributes) {
      this.numberOfAttributes = numberOfAttributes;
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

   @Override
   public String toString() {
      return "WebSessionBenchmarkStage{" +
            "opsCountStatusLog=" + opsCountStatusLog +
            ", numberOfRequestsPerThread=" + numberOfRequestsPerThread +
            ", numberOfAttributes=" + numberOfAttributes +
            ", sizeOfAnAttribute=" + sizeOfAnAttribute +
            ", writePercentage=" + writePercentage +
            ", numOfThreads=" + numOfThreads +
            ", reportNanos=" + reportNanos +
            ", cacheWrapper=" + cacheWrapper +
            super.toString();
   }
}
