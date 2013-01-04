package org.radargun.stressors;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
* // TODO: Document this
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
* @since 1/3/13
*/
public class BackgroundStatistics implements Serializable {

   protected boolean nodeUp = true;
   protected boolean snapshot = false;

   protected static class OperationStats implements Serializable {
      public long requests;
      public long responseTimeMax = Long.MIN_VALUE;
      public long responseTimeSum;
      public long errors;

      public OperationStats copy() {
         OperationStats copy = new OperationStats();
         copy.requests = requests;
         copy.responseTimeMax = responseTimeMax;
         copy.responseTimeSum = responseTimeSum;
         copy.errors = errors;
         return copy;
      }

      public void merge(OperationStats other) {
         requests += other.requests;
         responseTimeMax = Math.max(responseTimeMax, other.responseTimeMax);
         responseTimeSum += other.responseTimeSum;
         errors += other.errors;
      }
   }

   protected OperationStats putStats = new OperationStats();
   protected OperationStats getStats = new OperationStats();
   protected OperationStats removeStats = new OperationStats();

   protected long requestsNullGet;

   protected long intervalBeginTime;
   protected long intervalEndTime;

   protected int cacheSize;

   public BackgroundStatistics(boolean nodeUp) {
      this.nodeUp = nodeUp;
   }

   public BackgroundStatistics() {
      super();
      intervalBeginTime = System.currentTimeMillis();
      intervalEndTime = intervalBeginTime;
   }

   public boolean isNodeUp() {
      return nodeUp;
   }

   public synchronized void registerRequest(long responseTime, BackgroundOpsManager.Operation operation, boolean isNull) {
      ensureNotSnapshot();
      OperationStats stats = getOperationStats(operation);
      if (isNull) {
         requestsNullGet++;
      }
   }

   private OperationStats getOperationStats(BackgroundOpsManager.Operation operation) {
      switch (operation) {
         case GET:
            return getStats;
         case PUT:
            return putStats;
         case REMOVE:
            return removeStats;
         default:
            throw new IllegalArgumentException();
      }
   }

   public synchronized void registerError(long responseTime, BackgroundOpsManager.Operation operation) {
      OperationStats stats = getOperationStats(operation);
      stats.requests++;
      stats.responseTimeSum += responseTime;
      stats.responseTimeMax = Math.max(stats.responseTimeMax, responseTime);
      stats.errors++;
   }

   public synchronized void reset(long time) {
      ensureNotSnapshot();
      intervalBeginTime = time;
      intervalEndTime = intervalBeginTime;
      putStats = new OperationStats();
      getStats = new OperationStats();
      removeStats = new OperationStats();
      requestsNullGet = 0;
   }

   public synchronized BackgroundStatistics snapshot(boolean reset, long time) {
      ensureNotSnapshot();
      BackgroundStatistics result = new BackgroundStatistics();

      fillCopy(result);

      result.intervalEndTime = time;
      result.snapshot = true;
      result.nodeUp = nodeUp;
      if (reset) {
         reset(time);
      }
      return result;
   }

   public synchronized BackgroundStatistics copy() {
      BackgroundStatistics result = new BackgroundStatistics();
      fillCopy(result);
      return result;
   }

   protected void fillCopy(BackgroundStatistics result) {
      result.snapshot = snapshot;

      result.intervalBeginTime = intervalBeginTime;
      result.intervalEndTime = intervalEndTime;

      result.putStats = putStats.copy();
      result.getStats = getStats.copy();
      result.removeStats = removeStats.copy();
      result.requestsNullGet = requestsNullGet;
   }

   /**
    *
    * Merge otherStats to this. leaves otherStats unchanged.
    *
    * @param otherStats
    */
   public synchronized void merge(BackgroundStatistics otherStats) {
      ensureSnapshot();
      otherStats.ensureSnapshot();
      intervalBeginTime = Math.min(otherStats.intervalBeginTime, intervalBeginTime);
      intervalEndTime = Math.max(otherStats.intervalEndTime, intervalEndTime);
      putStats.merge(otherStats.putStats);
      getStats.merge(otherStats.getStats);
      removeStats.merge(otherStats.removeStats);
      requestsNullGet += otherStats.requestsNullGet;
   }

   public synchronized static BackgroundStatistics merge(Collection<BackgroundStatistics> set) {
      if (set.size() == 0) {
         return null;
      }
      Iterator<BackgroundStatistics> elems = set.iterator();
      BackgroundStatistics res = elems.next().copy();
      while (elems.hasNext()) {
         res.merge(elems.next());
      }
      return res;
   }

   public boolean isSnapshot() {
      return snapshot;
   }

   protected void ensureSnapshot() {
      if (!snapshot) {
         throw new RuntimeException("this operation can be performed only on snapshot");
      }
   }

   protected void ensureNotSnapshot() {
      if (snapshot) {
         throw new RuntimeException("this operation cannot be performed on snapshot");
      }
   }

   public long getNumErrors() {
      return putStats.errors + getStats.errors + removeStats.errors;
   }

   public synchronized long getNumberOfRequests() {
      return putStats.requests + getStats.requests + removeStats.requests;
   }

   private long getResponseTimeSum() {
      return putStats.responseTimeSum + getStats.responseTimeSum + removeStats.responseTimeSum;
   }

   public synchronized double getAvgResponseTime() {
      if (getNumberOfRequests() == 0) {
         return Double.NaN;
      } else {
         return ((double) getResponseTimeSum()) / ((double) getNumberOfRequests());
      }
   }

   public synchronized long getDuration() {
      return intervalEndTime - intervalBeginTime;
   }

   public synchronized long getIntervalBeginTime() {
      return intervalBeginTime;
   }

   public synchronized long getIntervalEndTime() {
      return intervalEndTime;
   }

   public synchronized double getThroughput() {
      if (getDuration() == 0) {
         return Double.NaN;
      } else {
         return ((double) getNumberOfRequests()) * ((double) 1000) / ((double) getDuration());
      }
   }

   @Override
   public String toString() {
      return "Stats(reqs=" + getNumberOfRequests() + ")";
   }

   public int getCacheSize() {
      return cacheSize;
   }

   public static double getCacheSizeMaxRelativeDeviation(List<BackgroundStatistics> stats) {
      if (stats.isEmpty() || stats.size() == 1) {
         return 0;
      }
      double sum = 0;
      int cnt = 0;
      for (BackgroundStatistics s : stats) {
         if (s.isNodeUp() && s.cacheSize != -1) {
            sum += (double) s.cacheSize;
            cnt++;
         }
      }
      double avg = sum / ((double) cnt);
      double maxDev = -1;
      for (BackgroundStatistics s : stats) {
         if (s.isNodeUp() && s.cacheSize != -1) {
            double dev = Math.abs(avg - ((double) s.cacheSize));
            if (dev > maxDev) {
               maxDev = dev;
            }
         }
      }
      return (maxDev / avg) * 100d;
   }

   public long getRequestsNullGet() {
      return requestsNullGet;
   }

   public static long getIntervalBeginMin(List<BackgroundStatistics> stats) {
      long ret = Long.MAX_VALUE;
      for (BackgroundStatistics s : stats) {
         if (s.intervalBeginTime < ret) {
            ret = s.intervalBeginTime;
         }
      }
      return ret;
   }

   public static long getIntervalBeginMax(List<BackgroundStatistics> stats) {
      long ret = Long.MIN_VALUE;
      for (BackgroundStatistics s : stats) {
         if (s.intervalBeginTime > ret) {
            ret = s.intervalBeginTime;
         }
      }
      return ret;
   }

   public static long getIntervalEndMin(List<BackgroundStatistics> stats) {
      long ret = Long.MAX_VALUE;
      for (BackgroundStatistics s : stats) {
         if (s.intervalEndTime < ret) {
            ret = s.intervalEndTime;
         }
      }
      return ret;
   }

   public static long getIntervalEndMax(List<BackgroundStatistics> stats) {
      long ret = Long.MIN_VALUE;
      for (BackgroundStatistics s : stats) {
         if (s.intervalEndTime > ret) {
            ret = s.intervalEndTime;
         }
      }
      return ret;
   }

   public static double getTotalThroughput(List<BackgroundStatistics> stats) {
      double ret = 0;
      for (BackgroundStatistics s : stats) {
         ret += s.getThroughput();
      }
      return ret;
   }

   public static double getAvgThroughput(List<BackgroundStatistics> stats) {
      return getTotalThroughput(stats) / ((double) stats.size());
   }

   public static double getAvgRespTime(List<BackgroundStatistics> stats) {
      long responseTimeSum = 0;
      long numRequests = 0;
      for (BackgroundStatistics s : stats) {
         responseTimeSum += s.getResponseTimeSum();
         numRequests += s.getNumberOfRequests();
      }
      return ((double) responseTimeSum) / ((double) numRequests);
   }

   public static long getMaxRespTime(List<BackgroundStatistics> stats) {
      long ret = Long.MIN_VALUE;
      for (BackgroundStatistics s : stats) {
         ret = Math.max(ret, s.getStats.responseTimeMax);
         ret = Math.max(ret, s.putStats.responseTimeMax);
         ret = Math.max(ret, s.removeStats.responseTimeMax);
      }
      return ret;
   }

   public static long getTotalCacheSize(List<BackgroundStatistics> stats) {
      long ret = 0;
      for (BackgroundStatistics s : stats) {
         ret += s.getCacheSize();
      }
      return ret;
   }

   public static long getTotalErrors(List<BackgroundStatistics> stats) {
      long ret = 0;
      for (BackgroundStatistics s : stats) {
         ret += s.getNumErrors();
      }
      return ret;
   }

   public static long getTotalNullRequests(List<BackgroundStatistics> stats) {
      long ret = 0;
      for (BackgroundStatistics s : stats) {
         ret += s.getRequestsNullGet();
      }
      return ret;
   }
}
