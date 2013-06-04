package org.radargun.stressors;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* // TODO: Document this
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
* @since 1/3/13
*/
public class SimpleStatistics implements Statistics {

   protected boolean nodeUp = true;

   public double getOperationsPerSecond() {
      long respSum = getResponseTimeSum();
      if (respSum == 0) return 0;
      return (double) (NS_IN_SEC * getNumberOfRequests()) / (double) respSum;
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

   protected OperationStats[] operationStats;

   protected long intervalBeginTime;
   protected long intervalEndTime;

   protected int cacheSize;

   public SimpleStatistics(boolean nodeUp) {
      this.nodeUp = nodeUp;
      initOperationStats();
   }

   public SimpleStatistics() {
      intervalBeginTime = System.nanoTime();
      intervalEndTime = intervalBeginTime;
      initOperationStats();
   }

   private void initOperationStats() {
      int opCount = Operation.values().length;
      operationStats = new OperationStats[opCount];
      for (int i = 0; i < opCount; ++i) {
         operationStats[i] = new OperationStats();
      }
   }

   protected SimpleStatistics create() {
      return new SimpleStatistics();
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(
            String.format("Stats(nodeUp=%s, interval=%d-%d, cacheSize=%d",
                          nodeUp, intervalBeginTime, intervalEndTime, cacheSize));
      Operation[] operations = Operation.values();
      for (int i = 0; i < operations.length; ++i) {
         sb.append(", ").append(operations[i].name()).append("=[").append(this.operationStats[i]).append("]");
         ++i;
      }
      return sb.toString();
   }

   public boolean isNodeUp() {
      return nodeUp;
   }

   @Override
   public void registerRequest(long responseTime, long txOverhead, Operation operation) {
      OperationStats stats = getOperationStats(operation);
      stats.requests++;
      stats.responseTimeMax = Math.max(stats.responseTimeMax, responseTime);
      stats.responseTimeSum += responseTime;
      stats.txOverhead += txOverhead;
   }

   private OperationStats getOperationStats(Operation operation) {
      return operationStats[operation.ordinal()];
   }

   @Override
   public void registerError(long responseTime, long txOverhead, Operation operation) {
      OperationStats stats = getOperationStats(operation);
      stats.requests++;
      stats.responseTimeSum += responseTime;
      stats.responseTimeMax = Math.max(stats.responseTimeMax, responseTime);
      stats.txOverhead += txOverhead;
      stats.errors++;
   }

   @Override
   public void reset(long time) {
      intervalBeginTime = time;
      intervalEndTime = intervalBeginTime;
      for (int i = 0; i < operationStats.length; ++i) {
         operationStats[i] = new OperationStats();
      }
   }

   @Override
   public SimpleStatistics copy() {
      SimpleStatistics result = create();
      fillCopy(result);
      return result;
   }

   protected void fillCopy(SimpleStatistics result) {
      result.intervalBeginTime = intervalBeginTime;
      result.intervalEndTime = intervalEndTime;

      result.operationStats = new OperationStats[operationStats.length];
      for (int i = 0; i < operationStats.length; ++i) {
         result.operationStats[i] = operationStats[i].copy();
      }
   }

   /**
    *
    * Merge otherStats to this. leaves otherStats unchanged.
    *
    * @param otherStats
    */
   @Override
   public void merge(Statistics otherStats) {
      if (!(otherStats instanceof SimpleStatistics)) throw new IllegalArgumentException();
      SimpleStatistics stats = (SimpleStatistics) otherStats;
      intervalBeginTime = Math.min(stats.intervalBeginTime, intervalBeginTime);
      intervalEndTime = Math.max(stats.intervalEndTime, intervalEndTime);
      for (int i = 0; i < operationStats.length; ++i) {
         operationStats[i].merge(stats.operationStats[i]);
      }
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
      long sum = 0;
      for (int i = 0; i < operationStats.length - 1; ++i) {
         sum += operationStats[i].errors;
      }
      return sum;
   }

   public long getNumberOfRequests() {
      long sum = 0;
      for (int i = 0; i < operationStats.length - 1; ++i) {
         sum += operationStats[i].requests;
      }
      return sum;
   }

   public long getResponseTimeSum() {
      long sum = 0;
      for (int i = 0; i < operationStats.length - 1; ++i) {
         sum += operationStats[i].responseTimeSum;
      }
      return sum;
   }

   public long getTxOverheadSum() {
      long sum = 0;
      for (int i = 0; i < operationStats.length - 1; ++i) {
         sum += operationStats[i].txOverhead;
      }
      return sum;
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

   public static double getCacheSizeMaxRelativeDeviation(List<SimpleStatistics> stats) {
      if (stats.isEmpty() || stats.size() == 1) {
         return 0;
      }
      double sum = 0;
      int cnt = 0;
      for (SimpleStatistics s : stats) {
         if (s.isNodeUp() && s.cacheSize != -1) {
            sum += (double) s.cacheSize;
            cnt++;
         }
      }
      double avg = sum / ((double) cnt);
      double maxDev = -1;
      for (SimpleStatistics s : stats) {
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
      return operationStats[Operation.GET_NULL.ordinal()].requests;
   }

   public static long getIntervalBeginMin(List<SimpleStatistics> stats) {
      long ret = Long.MAX_VALUE;
      for (SimpleStatistics s : stats) {
         if (s.intervalBeginTime < ret) {
            ret = s.intervalBeginTime;
         }
      }
      return ret;
   }

   public static long getIntervalBeginMax(List<SimpleStatistics> stats) {
      long ret = Long.MIN_VALUE;
      for (SimpleStatistics s : stats) {
         if (s.intervalBeginTime > ret) {
            ret = s.intervalBeginTime;
         }
      }
      return ret;
   }

   public static long getIntervalEndMin(List<SimpleStatistics> stats) {
      long ret = Long.MAX_VALUE;
      for (SimpleStatistics s : stats) {
         if (s.intervalEndTime < ret) {
            ret = s.intervalEndTime;
         }
      }
      return ret;
   }

   public static long getIntervalEndMax(List<SimpleStatistics> stats) {
      long ret = Long.MIN_VALUE;
      for (SimpleStatistics s : stats) {
         if (s.intervalEndTime > ret) {
            ret = s.intervalEndTime;
         }
      }
      return ret;
   }

   public static double getTotalThroughput(List<SimpleStatistics> stats) {
      double ret = 0;
      for (SimpleStatistics s : stats) {
         ret += s.getThroughput();
      }
      return ret;
   }

   public static double getAvgThroughput(List<SimpleStatistics> stats) {
      return getTotalThroughput(stats) / ((double) stats.size());
   }

   public static double getAvgRespTime(List<SimpleStatistics> stats) {
      long responseTimeSum = 0;
      long numRequests = 0;
      for (SimpleStatistics s : stats) {
         responseTimeSum += s.getResponseTimeSum();
         numRequests += s.getNumberOfRequests();
      }
      return ((double) responseTimeSum) / ((double) numRequests);
   }

   public static long getMaxRespTime(List<SimpleStatistics> stats) {
      long ret = Long.MIN_VALUE;
      for (SimpleStatistics s : stats) {
         for (int i = 0; i < s.operationStats.length - 1; ++i) { // ignores txStats
            ret = Math.max(ret, s.operationStats[i].responseTimeMax);
         }
      }
      return ret;
   }

   public static long getTotalCacheSize(List<SimpleStatistics> stats) {
      long ret = 0;
      for (SimpleStatistics s : stats) {
         ret += s.getCacheSize();
      }
      return ret;
   }

   public static long getTotalErrors(List<SimpleStatistics> stats) {
      long ret = 0;
      for (SimpleStatistics s : stats) {
         ret += s.getNumErrors();
      }
      return ret;
   }

   public static long getTotalNullRequests(List<SimpleStatistics> stats) {
      long ret = 0;
      for (SimpleStatistics s : stats) {
         ret += s.getRequestsNullGet();
      }
      return ret;
   }

   public Map<String, Object> getResultsMap(int numThreads, String prefix) {
      Map<String, Object> results = new LinkedHashMap<String, Object>();
      results.put("DURATION", getResponseTimeSum() + getTxOverheadSum());
      results.put("FAILURES", getNumErrors());
      results.put("REQ_PER_SEC", numThreads * getOperationsPerSecond());
      Operation[] operations = Operation.values();
      for (int i = 0; i < operations.length; ++i) {
         OperationStats os = operationStats[i];
         String name = operations[i].getAltName();
         if (os.requests > 0) {
            results.put(prefix + name + "_COUNT", os.requests);
            if (os.errors != 0) {
               results.put(prefix + name + "_ERRORS", os.errors);
            }
            results.put(prefix + name + "S_PER_SEC", numThreads * os.getPerSecond(true));
            if (os.txOverhead != 0) {
               results.put(prefix + name + "S_PER_SEC_NET", numThreads * os.getPerSecond(false));
            }
         }
      }
      return results;
   }
}
