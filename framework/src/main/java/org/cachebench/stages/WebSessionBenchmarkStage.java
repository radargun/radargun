package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates the work with a distributed web sessions.
 *
 * @author Mircea.Markus@jboss.com
 */
public class WebSessionBenchmarkStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(WebSessionBenchmarkStage.class);

   public static final String SESSION_PREFIX = "SESSION_";
   private static int LOG_AFTER_OPERATION_COUNT = 5000;


   /**
    * total number of request to be made against this session: reads + writes
    */
   private int numberOfRequests = 500000;

   /**
    * for each session there will be created fixed number of attributes. On those attributes all the GETs and PUTs are
    * performed (for PUT is overwrite)
    */
   private int numberOfAttributes = 100;

   /**
    * Each attribute will be a byte[] of this size
    */
   private int sizeOfAnAttribute = 10000;

   /**
    * Out of the total number of request, this define the frequency of writes (percentage)
    */
   private int writePercentage = 20;


   /**
    * the number of threads that will work on this slave
    */
   private int numOfThreads = 10;

   private boolean reportNanos = false;


   /*
    * Needed for running the test.
    */
   private ExecutorService ex;
   private CacheWrapper cacheWrapper;
   private String sessionId;
   private static Random r = new Random();


   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = new DefaultDistStageAck(slaveIndex, slaveState.getLocalAddress());
      this.cacheWrapper = slaveState.getCacheWrapper();
      if (cacheWrapper == null) {
         log.info("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }
      ex = Executors.newFixedThreadPool(numOfThreads);
      sessionId = SESSION_PREFIX + getSlaveIndex();
      log.info("SessionID is " + sessionId);

      try {
         initSession();
         stressSession(result);
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
      Map<Integer,Map<String, Object>> results = new HashMap<Integer, Map<String, Object>>();
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

   private void initSession() throws Exception {
      for (int i = 0; i < numberOfAttributes; i++) {
         try {
            cacheWrapper.put(generatePath(sessionId, i), getSessionEntry(i), new byte[sizeOfAnAttribute]);
         }
         catch (Throwable e) {
            log.warn("Error while initializing the session: ", e);
         }
      }
   }

   private void stressSession(DefaultDistStageAck ack) throws Exception {
      long reads = 0;
      long writes = 0;
      int readPercentage = 100 - writePercentage;
      Random r = new Random();
      int randomAction;
      int randomAttribute;
      AtomicLong durationNanos = new AtomicLong(0);
      for (int i = 0; i < numberOfRequests; i++) {
         logRunCount(i);
         randomAction = r.nextInt(100);
         randomAttribute = r.nextInt(numberOfAttributes - 1);

         String sessionEntry = getSessionEntry(randomAttribute);
         List<String> path = generatePath(sessionId, randomAttribute);

         if (randomAction < readPercentage) {
            // read
            ex.execute(new ReadTask(path, durationNanos, sessionEntry));
            reads++;
         } else {
            // write
            ex.execute(new WriteTask(path, durationNanos, sessionEntry));
            writes++;
         }
      }

      ex.shutdown();
      ex.awaitTermination(60 * 10, TimeUnit.SECONDS);


      long duration = this.reportNanos ? durationNanos.get() : durationNanos.get() / 1000000;
      Map<String, String> results = new LinkedHashMap<String, String>();
      results.put("DURATION", str(duration));
      double requestPerSec = (reads + writes) / (duration / 1000.0);
      results.put("REQ PER SEC", str(requestPerSec));
      results.put("READ_COUNT", str(reads));
      results.put("WRITE_COUNT", str(writes));
      ack.setPayload(results);
   }

   private String str(Object o) {
      return String.valueOf(o);
   }

   private void logRunCount(int i) {
      if (((i + 1) % LOG_AFTER_OPERATION_COUNT == 0) || (i == 0)) {
         log.info("SessionSimulatorTest performed " + (i == 0 ? 0 : (i + 1)) + " operations");
      }
   }

   public void setNumberOfRequests(int numberOfRequests) {
      this.numberOfRequests = numberOfRequests;
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

   private abstract static class Task implements Runnable {
      List<String> path;
      AtomicLong durationNanos;
      Object sessionEntry;

      protected Task(List<String> path, AtomicLong durationNanos, Object sessionEntry) {
         this.path = path;
         this.durationNanos = durationNanos;
         this.sessionEntry = sessionEntry;
      }
   }


   private class WriteTask extends Task {
      protected WriteTask(List<String> path, AtomicLong durationNanos, Object sessionEntry) {
         super(path, durationNanos, sessionEntry);
      }

      public void run() {
         try {
            String payload = generateRandomString(sizeOfAnAttribute);
            long start = System.nanoTime();
            cacheWrapper.put(path, sessionEntry, payload);
            durationNanos.getAndAdd(System.nanoTime() - start);
         }
         catch (Throwable e) {
            log.warn("Error appeared whilst writing to cache:" + e.getMessage(), e);
         }
      }
   }

   private class ReadTask extends Task {
      protected ReadTask(List<String> path, AtomicLong durationNanos, Object sessionEntry) {
         super(path, durationNanos, sessionEntry);
      }

      public void run() {
         try {
            long start = System.nanoTime();
            cacheWrapper.get(path, sessionEntry);
            durationNanos.getAndAdd(System.nanoTime() - start);
         }
         catch (Throwable e) {
            log.warn("Error appeared whilst reading from cache:" + e.getMessage(), e);
         }
      }
   }


   /**
    * @return a well-spaced path for a key-value pair
    */
   private List<String> generatePath(String base, int sequence) {
      // use bucket sizes of 100 and a depth of 3.
      int intermediate = sequence % 100;
      return Arrays.asList(base, "Intermediate-" + intermediate, "Slave " + sequence);
   }

   private static String generateRandomString(int size) {
      // each char is 2 bytes
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < size / 2; i++) sb.append((char) (64 + r.nextInt(26)));
      return sb.toString();
   }


   private String getSessionEntry(int i) {
      return sessionId + i;
   }

   @Override
   public String toString() {
      return "WebSessionBenchmarkStage{" +
            "numberOfRequests=" + numberOfRequests +
            ", numberOfAttributes=" + numberOfAttributes +
            ", sizeOfAnAttribute=" + sizeOfAnAttribute +
            ", writePercentage=" + writePercentage +
            ", numOfThreads=" + numOfThreads +
            ", reportNanos=" + reportNanos +
            ", ex=" + ex +
            ", cacheWrapper=" + cacheWrapper +
            ", sessionId='" + sessionId + '\'' +
            "} " + super.toString();
   }
}
