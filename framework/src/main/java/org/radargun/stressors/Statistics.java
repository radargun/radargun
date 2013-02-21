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
public class Statistics implements Serializable {

   public static final long NS_IN_SEC = 1000 * 1000 * 1000;
   public static final long NS_IN_MS = 1000 * 1000;
   protected boolean nodeUp = true;

   public double getOperationsPerSecond() {
      long respSum = getResponseTimeSum();
      if (respSum == 0) return 0;
      return (double) (NS_IN_SEC * getNumberOfRequests()) / (double) respSum;
   }

   public double getReadsPerSecond(boolean includeOverhead) {
      return getStats.getPerSecond(includeOverhead);
   }

   public double getWritesPerSecond(boolean includeOverhead) {
      return putStats.getPerSecond(includeOverhead);
   }

   public double getRemovesPerSecond(boolean includeOverhead) {
      return removeStats.getPerSecond(includeOverhead);
   }

   public double getTransactionsPerSecond() {
      return txStats.getPerSecond(false);
   }

   public long getNumReads() {
      return getStats.requests;
   }

   public long getNumWrites() {
      return putStats.requests;
   }

   public long getNumRemoves() {
      return removeStats.requests;
   }

   public long getNumTransactions() {
      return txStats.requests;
   }

   protected static class OperationStats implements Serializable {
      public long requests;
      public long responseTimeMax = Long.MIN_VALUE;
      public long responseTimeSum;
      public long txOverhead;
      public long errors;

      public OperationStats copy() {
         OperationStats copy = new OperationStats();
         copy.requests = requests;
         copy.responseTimeMax = responseTimeMax;
         copy.responseTimeSum = responseTimeSum;
         copy.txOverhead = txOverhead;
         copy.errors = errors;
         return copy;
      }

      public void merge(OperationStats other) {
         requests += other.requests;
         responseTimeMax = Math.max(responseTimeMax, other.responseTimeMax);
         responseTimeSum += other.responseTimeSum;
         txOverhead += other.txOverhead;
         errors += other.errors;
      }

      public double getPerSecond(boolean includeOverhead) {
         if (responseTimeSum == 0) return 0;
         return NS_IN_SEC * requests / (double) (responseTimeSum + (includeOverhead ? txOverhead : 0));
      }

      public String toString() {
         return String.format("requests=%d, responseTimeMax=%d, responseTimeSum=%d, errors=%d, txOverhead=%d",
                              requests, responseTimeMax, responseTimeSum, errors, txOverhead);
      }
   }

   protected OperationStats putStats = new OperationStats();
   protected OperationStats getStats = new OperationStats();
   protected OperationStats removeStats = new OperationStats();
   protected OperationStats txStats = new OperationStats();

   protected long requestsNullGet;

   protected long intervalBeginTime;
   protected long intervalEndTime;

   protected int cacheSize;

   public Statistics(boolean nodeUp) {
      this.nodeUp = nodeUp;
   }

   public Statistics() {
      intervalBeginTime = System.nanoTime();
      intervalEndTime = intervalBeginTime;
   }

   protected Statistics create() {
      return new Statistics();
   }

   @Override
   public String toString() {
      return String.format("Stats(nodeUp=%s, interval=%d-%d, cacheSize=%d, putStats=[%s], getStats=[%s, nullGet=%d], removeStats=[%s], transactionStats=[%s])",
                           nodeUp, intervalBeginTime, intervalEndTime, cacheSize, putStats, getStats, requestsNullGet, removeStats, txStats);
   }

   public boolean isNodeUp() {
      return nodeUp;
   }

   public void registerRequest(long responseTime, long txOverhead, Operation operation, boolean isNull) {
      OperationStats stats = getOperationStats(operation);
      stats.requests++;
      stats.responseTimeMax = Math.max(stats.responseTimeMax, responseTime);
      stats.responseTimeSum += responseTime;
      stats.txOverhead += txOverhead;
      if (isNull) {
         requestsNullGet++;
      }
   }

   private OperationStats getOperationStats(Operation operation) {
      switch (operation) {
         case GET:
            return getStats;
         case PUT:
            return putStats;
         case REMOVE:
            return removeStats;
         case TRANSACTION:
            return txStats;
         default:
            throw new IllegalArgumentException();
      }
   }

   public void registerError(long responseTime, long txOverhead, Operation operation) {
      OperationStats stats = getOperationStats(operation);
      stats.requests++;
      stats.responseTimeSum += responseTime;
      stats.responseTimeMax = Math.max(stats.responseTimeMax, responseTime);
      stats.txOverhead += txOverhead;
      stats.errors++;
   }

   public void reset(long time) {
      intervalBeginTime = time;
      intervalEndTime = intervalBeginTime;
      putStats = new OperationStats();
      getStats = new OperationStats();
      removeStats = new OperationStats();
      txStats = new OperationStats();
      requestsNullGet = 0;
   }

   public Statistics copy() {
      Statistics result = create();
      fillCopy(result);
      return result;
   }

   protected void fillCopy(Statistics result) {
      result.intervalBeginTime = intervalBeginTime;
      result.intervalEndTime = intervalEndTime;

      result.putStats = putStats.copy();
      result.getStats = getStats.copy();
      result.removeStats = removeStats.copy();
      result.txStats = txStats.copy();
      result.requestsNullGet = requestsNullGet;
   }

   /**
    *
    * Merge otherStats to this. leaves otherStats unchanged.
    *
    * @param otherStats
    */
   public void merge(Statistics otherStats) {
      intervalBeginTime = Math.min(otherStats.intervalBeginTime, intervalBeginTime);
      intervalEndTime = Math.max(otherStats.intervalEndTime, intervalEndTime);
      putStats.merge(otherStats.putStats);
      getStats.merge(otherStats.getStats);
      removeStats.merge(otherStats.removeStats);
      txStats.merge(otherStats.txStats);
      requestsNullGet += otherStats.requestsNullGet;
   }

   public static Statistics merge(Collection<Statistics> set) {
      if (set.size() == 0) {
         return null;
      }
      Iterator<Statistics> elems = set.iterator();
      Statistics res = elems.next().copy();
      while (elems.hasNext()) {
         res.merge(elems.next());
      }
      return res;
   }

   public long getNumErrors() {
      return putStats.errors + getStats.errors + removeStats.errors;
   }

   public long getNumberOfRequests() {
      return putStats.requests + getStats.requests + removeStats.requests;
   }

   public long getResponseTimeSum() {
      return putStats.responseTimeSum + getStats.responseTimeSum + removeStats.responseTimeSum;
   }

   public long getTxOverheadSum() {
      return putStats.txOverhead + getStats.txOverhead + removeStats.txOverhead;
   }

   public double getAvgResponseTime() {
      if (getNumberOfRequests() == 0) {
         return Double.NaN;
      } else {
         return ((double) getResponseTimeSum()) / ((double) getNumberOfRequests());
      }
   }

   /* In nanoseconds */
   public long getDuration() {
      return (intervalEndTime - intervalBeginTime) * NS_IN_MS;
   }

   /* In milliseconds since epoch */
   public long getIntervalBeginTime() {
      return intervalBeginTime;
   }

   /* In milliseconds since epoch */
   public long getIntervalEndTime() {
      return intervalEndTime;
   }

   public double getThroughput() {
      if (getDuration() == 0) {
         return Double.NaN;
      } else {
         return ((double) getNumberOfRequests()) * ((double) NS_IN_SEC) / ((double) getDuration());
      }
   }

   public int getCacheSize() {
      return cacheSize;
   }

   public static double getCacheSizeMaxRelativeDeviation(List<Statistics> stats) {
      if (stats.isEmpty() || stats.size() == 1) {
         return 0;
      }
      double sum = 0;
      int cnt = 0;
      for (Statistics s : stats) {
         if (s.isNodeUp() && s.cacheSize != -1) {
            sum += (double) s.cacheSize;
            cnt++;
         }
      }
      double avg = sum / ((double) cnt);
      double maxDev = -1;
      for (Statistics s : stats) {
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

   public static long getIntervalBeginMin(List<Statistics> stats) {
      long ret = Long.MAX_VALUE;
      for (Statistics s : stats) {
         if (s.intervalBeginTime < ret) {
            ret = s.intervalBeginTime;
         }
      }
      return ret;
   }

   public static long getIntervalBeginMax(List<Statistics> stats) {
      long ret = Long.MIN_VALUE;
      for (Statistics s : stats) {
         if (s.intervalBeginTime > ret) {
            ret = s.intervalBeginTime;
         }
      }
      return ret;
   }

   public static long getIntervalEndMin(List<Statistics> stats) {
      long ret = Long.MAX_VALUE;
      for (Statistics s : stats) {
         if (s.intervalEndTime < ret) {
            ret = s.intervalEndTime;
         }
      }
      return ret;
   }

   public static long getIntervalEndMax(List<Statistics> stats) {
      long ret = Long.MIN_VALUE;
      for (Statistics s : stats) {
         if (s.intervalEndTime > ret) {
            ret = s.intervalEndTime;
         }
      }
      return ret;
   }

   public static double getTotalThroughput(List<Statistics> stats) {
      double ret = 0;
      for (Statistics s : stats) {
         ret += s.getThroughput();
      }
      return ret;
   }

   public static double getAvgThroughput(List<Statistics> stats) {
      return getTotalThroughput(stats) / ((double) stats.size());
   }

   public static double getAvgRespTime(List<Statistics> stats) {
      long responseTimeSum = 0;
      long numRequests = 0;
      for (Statistics s : stats) {
         responseTimeSum += s.getResponseTimeSum();
         numRequests += s.getNumberOfRequests();
      }
      return ((double) responseTimeSum) / ((double) numRequests);
   }

   public static long getMaxRespTime(List<Statistics> stats) {
      long ret = Long.MIN_VALUE;
      for (Statistics s : stats) {
         ret = Math.max(ret, s.getStats.responseTimeMax);
         ret = Math.max(ret, s.putStats.responseTimeMax);
         ret = Math.max(ret, s.removeStats.responseTimeMax);
      }
      return ret;
   }

   public static long getTotalCacheSize(List<Statistics> stats) {
      long ret = 0;
      for (Statistics s : stats) {
         ret += s.getCacheSize();
      }
      return ret;
   }

   public static long getTotalErrors(List<Statistics> stats) {
      long ret = 0;
      for (Statistics s : stats) {
         ret += s.getNumErrors();
      }
      return ret;
   }

   public static long getTotalNullRequests(List<Statistics> stats) {
      long ret = 0;
      for (Statistics s : stats) {
         ret += s.getRequestsNullGet();
      }
      return ret;
   }
}
