package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;
import org.cachebench.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Simulates the work with a distributed web sessions.
 *
 * @author Mircea.Markus@jboss.com
 */
public class WebSessionBenchmarkStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(WebSessionBenchmarkStage.class);

   private int opsCountStatusLog = 5000;

   public static final String SESSION_PREFIX = "SESSION_";
   public static final String ATTRIBUTE_PREFIX = "SESSION_";


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
   private static Random r = new Random();
   private long startTime;


   public DistStageAck executeOnSlave() {
      startTime = System.currentTimeMillis();
      DefaultDistStageAck result = new DefaultDistStageAck(slaveIndex, slaveState.getLocalAddress());
      this.cacheWrapper = slaveState.getCacheWrapper();
      if (cacheWrapper == null) {
         log.info("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      log.info("Starting WebSessionBenchmarkStage: " + this.toString());

      initSessions();

      try {
         List<SessionStresser> sessionStressers = executeOpsOnSession();
         processResults(sessionStressers, result);
      } catch (Exception e) {
         log.warn("Exception while initializing the test", e);
         result.setError(true);
         result.setRemoteException(e);
      }
      return result;
   }

   private void processResults(List<SessionStresser> sessionStressers, DefaultDistStageAck ack) {
      long duration = 0;
      int reads = 0;
      int writes = 0;
      int failures = 0;
      for (SessionStresser sessionStresser : sessionStressers) {
         duration += reportNanos ? sessionStresser.durationNanos : sessionStresser.durationNanos / 1000000;
         reads += sessionStresser.reads;
         writes += sessionStresser.writes;
         failures += sessionStresser.nrFailures;
      }

      Map<String, String> results = new LinkedHashMap<String, String>();
      results.put("DURATION", str(duration));
      double requestPerSec = (reads + writes) / (duration / 1000.0);
      results.put("REQ PER SEC", str(requestPerSec));
      results.put("READ_COUNT", str(reads));
      results.put("WRITE_COUNT", str(writes));
      ack.setPayload(results);
      log.info("Finsihed generating report. Nr of failed operations on this node is: " + failures +
            ". Test duration is: " + Utils.getDurationString(System.currentTimeMillis() - startTime));
   }

   private List<SessionStresser> executeOpsOnSession() throws Exception {
      List<SessionStresser> sessionStressers;
      sessionStressers = new ArrayList<SessionStresser>();
      for (int threadIndex = 0; threadIndex < numOfThreads; threadIndex++) {
         SessionStresser sessionStresser = new SessionStresser(threadIndex);
         sessionStressers.add(sessionStresser);
         sessionStresser.start();
      }
      for (SessionStresser sessionStresser : sessionStressers) {
         sessionStresser.join();
      }
      return sessionStressers;
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

   private void initSessions() {
      for (int threadIndex = 0; threadIndex < numOfThreads; threadIndex++) {
         for (int attrIndex = 0; attrIndex < numberOfAttributes; attrIndex++) {
            try {
               cacheWrapper.put(getSessionId(threadIndex), getAttributeName(attrIndex), new byte[sizeOfAnAttribute]);
            }
            catch (Throwable e) {
               log.warn("Error while initializing the session: ", e);
            }
         }
      }
   }

   private String getAttributeName(int attrIndex) {
      return ATTRIBUTE_PREFIX + '_' + attrIndex;
   }


   public class SessionStresser extends Thread {

      private int threadIndex;
      private String sessionId;
      private int nrFailures;
      private long durationNanos = 0;
      private long reads;
      private long writes;
      private long startTime;

      public SessionStresser(int threadIndex) {
         super("SessionStresser-" + threadIndex);
         this.threadIndex = threadIndex;
         sessionId = getSessionId(threadIndex);
      }

      @Override
      public void run() {
         startTime = System.currentTimeMillis();
         int readPercentage = 100 - writePercentage;
         Random r = new Random();
         int randomAction;
         int randomAttributeInt;
         for (int i = 0; i < numberOfRequestsPerThread; i++) {
            logProgress(i);
            randomAction = r.nextInt(100);
            randomAttributeInt = r.nextInt(numberOfAttributes - 1);
            String attribute = getAttributeName(randomAttributeInt);

            if (randomAction < readPercentage) {
               long start = System.nanoTime();
               try {
                  cacheWrapper.get(sessionId, attribute);
               } catch (Exception e) {
                  log.warn(e);
                  nrFailures++;
               }
               durationNanos += System.nanoTime() - start;
               reads++;
            } else {
               String payload = generateRandomString(sizeOfAnAttribute);
               long start = System.nanoTime();
               try {
                  cacheWrapper.put(sessionId, attribute, payload);
               } catch (Exception e) {
                  log.warn(e);
                  nrFailures++;
               }
               durationNanos += System.nanoTime() - start;
               writes++;
            }
         }
      }

      private void logProgress(int i) {
         if ((i + 1) % opsCountStatusLog == 0) {
            double elapsedTime = System.currentTimeMillis() - startTime;
            double estimatedTotal = ((double) numberOfRequestsPerThread / (double) i) * elapsedTime;
            double estimatedRemaining = estimatedTotal - elapsedTime;
            if (log.isTraceEnabled()) {
               log.trace("i=" + i + ", elapsedTime=" + elapsedTime);
            }
            log.info("Thread index '" + threadIndex + "' executed " + (i + 1) + " operations. Elapsed time: " +
                  Utils.getDurationString((long) elapsedTime) + ". Estimated remaining: " + Utils.getDurationString((long) estimatedRemaining) +
                  ". Estimated total: " + Utils.getDurationString((long) estimatedTotal));
         }
      }
   }

   private String str(Object o) {
      return String.valueOf(o);
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

   /**
    * This will make sure that each session runs in its own thread and no collisition will take place. See
    * https://sourceforge.net/apps/trac/cachebenchfwk/ticket/14
    */
   private String getSessionId(int threadIndex) {
      return SESSION_PREFIX + "_" + getSlaveIndex() + "_" + threadIndex;
   }

   private static String generateRandomString(int size) {
      // each char is 2 bytes
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < size / 2; i++) sb.append((char) (64 + r.nextInt(26)));
      return sb.toString();
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
