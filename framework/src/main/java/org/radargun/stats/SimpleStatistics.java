/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.radargun.stats;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* Statistics gathered in one period, requiring constant amount of memory regardless of the test duration.
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
* @since 1/3/13
*/
public class SimpleStatistics implements Statistics {

   public static class BoxAndWhiskers {
      public final double maxRegular;
      public final double q3;
      public final double mean;
      public final double q1;
      public final double minRegular;

      public BoxAndWhiskers(double maxRegular, double q3, double mean, double q1, double minRegular) {
         this.maxRegular = maxRegular;
         this.q3 = q3;
         this.mean = mean;
         this.q1 = q1;
         this.minRegular = minRegular;
      }
   }

   public static class MeanAndDev {
      public final double mean;
      public final double dev;

      public MeanAndDev(double mean, double dev) {
         this.mean = mean;
         this.dev = dev;
      }
   }

   protected static class OperationStats implements Serializable {
      private static final double INVERSE_NORMAL_95 = 1.96;
      private static final double INVERSE_NORMAL_50 = 0.67448;
      public long requests;
      public long responseTimeMax = Long.MIN_VALUE;
      public long responseTimeSum;
      public double responseTimeMean; // first moment
      public double responseTimeM2; // second moment, var = M2 / (n - 1)
      public double withTxOverheadMean; // first moment
      public double withTxOverheadM2; // second moment, var = M2 / (n - 1)
      public long txOverhead;
      public long errors;

      public OperationStats copy() {
         OperationStats copy = new OperationStats();
         copy.requests = requests;
         copy.responseTimeMax = responseTimeMax;
         copy.responseTimeSum = responseTimeSum;
         copy.responseTimeMean = responseTimeMean;
         copy.responseTimeM2 = responseTimeM2;
         copy.withTxOverheadMean = withTxOverheadMean;
         copy.withTxOverheadM2 = withTxOverheadM2;
         copy.txOverhead = txOverhead;
         copy.errors = errors;
         return copy;
      }

      public void merge(OperationStats other) {
         responseTimeM2 = mergeM2(responseTimeMean, responseTimeM2, requests, other.responseTimeMean, other.responseTimeM2, other.requests);
         responseTimeMean = mergeMean(responseTimeMean, requests, other.responseTimeMean, other.requests);
         withTxOverheadM2 = mergeM2(withTxOverheadMean, withTxOverheadM2, requests, other.withTxOverheadMean, other.withTxOverheadM2, other.requests);
         withTxOverheadMean = mergeMean(withTxOverheadMean, requests, other.withTxOverheadMean, other.requests);

         requests += other.requests;
         responseTimeMax = Math.max(responseTimeMax, other.responseTimeMax);
         responseTimeSum += other.responseTimeSum;
         txOverhead += other.txOverhead;
         errors += other.errors;
      }

      private static double mergeMean(double myMean, double myN, double otherMean, double otherN) {
         return (myMean * myN + otherMean * otherN) / (myN + otherN);
      }

      private static double mergeM2(double myMean, double myM2, double myN, double otherMean, double otherM2, double otherN) {
         double delta = myMean - otherMean;
         return myM2 + otherM2 + delta * delta * otherN * myN / (otherN + myN);
      }

      public double getPerSecond(boolean includeOverhead) {
         if (responseTimeMean == 0d || withTxOverheadMean == 0d) return 0d;
         return NS_IN_SEC / (includeOverhead ? withTxOverheadMean : responseTimeMean);
      }

      public String toString() {
         return String.format("requests=%d, responseTimeMax=%d, responseTimeSum=%d, errors=%d, txOverhead=%d",
                              requests, responseTimeMax, responseTimeSum, errors, txOverhead);
      }

      public void register(long responseTime, long txTime) {
         requests++;
         responseTimeMax = Math.max(responseTimeMax, responseTime);
         responseTimeSum += responseTime;
         // see http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Online_algorithm
         double delta = (double) responseTime - responseTimeMean;
         responseTimeMean += delta/(double) requests;
         responseTimeM2 += delta * ((double)responseTime - responseTimeMean);

         double deltaTx = (double) (responseTime + txTime) - withTxOverheadMean;
         withTxOverheadMean += deltaTx / (double) requests;
         withTxOverheadM2 += deltaTx * ((double) (responseTime + txTime) - withTxOverheadMean);

         txOverhead += txTime;
      }

      public BoxAndWhiskers getBoxAndWhiskers(boolean includeOverhead) {
         if (includeOverhead) {
            if (requests < 2) {
               return new BoxAndWhiskers(withTxOverheadMean, withTxOverheadMean, withTxOverheadMean, withTxOverheadMean, withTxOverheadMean);
            }
            double stddev =  Math.sqrt(withTxOverheadM2 / (double) (requests - 1));
            return new BoxAndWhiskers(withTxOverheadMean + INVERSE_NORMAL_95 * stddev, withTxOverheadMean + INVERSE_NORMAL_50 * stddev,
                  withTxOverheadMean, withTxOverheadMean - INVERSE_NORMAL_50 * stddev, withTxOverheadMean - INVERSE_NORMAL_95 * stddev);
         } else {
            if (requests < 2) {
               return new BoxAndWhiskers(responseTimeMean, responseTimeMean, responseTimeMean, responseTimeMean, responseTimeMean);
            }
            double stddev =  Math.sqrt(responseTimeM2 / (double) (requests - 1));
            return new BoxAndWhiskers(responseTimeMean + INVERSE_NORMAL_95 * stddev, responseTimeMean + INVERSE_NORMAL_50 * stddev,
                  responseTimeMean, responseTimeMean - INVERSE_NORMAL_50 * stddev, responseTimeMean - INVERSE_NORMAL_95 * stddev);
         }
      }

      public MeanAndDev getMeanAndDev(boolean includeOverhead) {
         if (requests < 2) return new MeanAndDev(includeOverhead ? withTxOverheadMean : responseTimeMean, 0);
         if (includeOverhead) {
            double stddev =  Math.sqrt(withTxOverheadM2 / (double) (requests - 1));
            return new MeanAndDev(withTxOverheadMean, stddev);
         } else {
            double stddev =  Math.sqrt(responseTimeM2 / (double) (requests - 1));
            return new MeanAndDev(responseTimeMean, stddev);
         }
      }
   }

   protected OperationStats[] operationStats;

   protected long intervalBeginTime;
   protected long intervalEndTime;
   protected boolean nodeUp = true;

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

   public void setCacheSize(int cacheSize) {
      this.cacheSize = cacheSize;
   }

   public Map<String, BoxAndWhiskers> getBoxAndWhiskers(boolean includeOverhead) {
      Map<String, BoxAndWhiskers> map = new HashMap<String, BoxAndWhiskers>();
      for (Operation op : Operation.values()) {
         if (operationStats[op.ordinal()].requests > 0) {
            map.put(op.getAltName(), operationStats[op.ordinal()].getBoxAndWhiskers(includeOverhead));
         }
      }
      return map;
   }

   public long getNumRequests(String operation) {
      for (Operation op : Operation.values()) {
         if (op.getAltName().equals(operation)) {
            return operationStats[op.ordinal()].requests;
         }
      }
      return 0;
   }

   public MeanAndDev getMeanAndDev(boolean includeOverhead, String operation) {
      for (Operation op : Operation.values()) {
         if (op.getAltName().equals(operation)) {
            return operationStats[op.ordinal()].getMeanAndDev(includeOverhead);
         }
      }
      return null;
   }

   public Map<String, MeanAndDev> getMeanAndDev(boolean includeOverhead) {
      Map<String, MeanAndDev> map = new HashMap<String, MeanAndDev>();
      for (Operation op : Operation.values()) {
         if (operationStats[op.ordinal()].requests > 0) {
            map.put(op.getAltName(), operationStats[op.ordinal()].getMeanAndDev(includeOverhead));
         }
      }
      return map;
   }

   public Set<String> getUsedOperations() {
      Set<String> set = new HashSet<String>();
      for (Operation op : Operation.values()) {
         if (operationStats[op.ordinal()].requests > 0) {
            set.add(op.getAltName());
         }
      }
      return set;
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
      OperationStats stats = operationStats[operation.ordinal()];
      stats.register(responseTime, txOverhead);
   }

   @Override
   public void registerError(long responseTime, long txOverhead, Operation operation) {
      OperationStats stats = operationStats[operation.ordinal()];
      stats.register(responseTime, txOverhead);
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

   public double getOperationsPerSecond(boolean includeOverhead, String operation) {
      for (Operation op : Operation.values()) {
         if (op.getAltName().equals(operation)) {
            OperationStats stats = operationStats[op.ordinal()];
            return (NS_IN_SEC * stats.requests) / (double) (stats.responseTimeSum + (includeOverhead ? stats.txOverhead : 0));
         }
      }
      return Double.NaN;
   }

   @Override
   public double getOperationsPerSecond(boolean includeOverhead) {
      long respSum = getResponseTimeSum();
      if (includeOverhead) {
         respSum += getTxOverheadSum();
      }
      if (respSum == 0) return 0;
      return (double) (NS_IN_SEC * getNumberOfRequests()) / (double) respSum;
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
      results.put(prefix + "DURATION", getResponseTimeSum() + getTxOverheadSum());
      results.put(prefix + "FAILURES", getNumErrors());
      results.put(prefix + "THREADS", numThreads);
      results.put(prefix + "REQ_PER_SEC_NET", numThreads * getOperationsPerSecond(false));
      results.put(prefix + REQ_PER_SEC, numThreads * getOperationsPerSecond(true));
      Operation[] operations = Operation.values();
      for (int i = 0; i < operations.length; ++i) {
         OperationStats os = operationStats[i];
         String prefixedName = prefix + operations[i].getAltName();
         if (os.requests > 0) {
            results.put(prefixedName + "_COUNT", os.requests);
            if (os.errors != 0) {
               results.put(prefixedName + "_ERRORS", os.errors);
            }
            results.put(prefixedName + "S_PER_SEC", numThreads * os.getPerSecond(true));
            if (os.txOverhead != 0) {
               results.put(prefixedName + "S_PER_SEC_NET", numThreads * os.getPerSecond(false));
            }
            results.put(prefixedName + "_AVG_NET", (double) os.responseTimeSum / (double) os.requests);
            results.put(prefixedName + "_AVG_TX", (double) (os.responseTimeSum + os.txOverhead) / (double) os.requests);
            results.put(prefixedName + "_DURATION_NET", os.responseTimeSum);
            results.put(prefixedName + "_TX_OVERHEAD", os.txOverhead);
            results.put(prefixedName + "_MEAN_NET", os.responseTimeMean);
            results.put(prefixedName + "_MEAN_TX", os.withTxOverheadMean);
            results.put(prefixedName + "_M2_NET", os.responseTimeM2);
            results.put(prefixedName + "_M2_TX", os.withTxOverheadM2);
         }
      }
      return results;
   }

   public void parseIn(String key, String value) {
      for (Operation op : Operation.values()) {
         if (key.startsWith(op.getAltName())) {
            String type = key.substring(op.getAltName().length() + 1);
            OperationStats opStats = operationStats[op.ordinal()];
            if (type.equals("COUNT")) {
               opStats.requests = value.isEmpty() ? 0 : Long.parseLong(value);
            } else if (type.equals("ERRORS")) {
               opStats.errors = value.isEmpty() ? 0 : Long.parseLong(value);
            } else if (type.equals("MEAN_NET")) {
               opStats.responseTimeMean = value.isEmpty() ? 0d : Double.parseDouble(value);
            } else if (type.equals("MEAN_TX")) {
               opStats.withTxOverheadMean = value.isEmpty() ? 0d : Double.parseDouble(value);
            } else if (type.equals("M2_NET")) {
               opStats.responseTimeM2 = value.isEmpty() ? 0d : Double.parseDouble(value);
            } else if (type.equals("M2_TX")) {
               opStats.withTxOverheadM2 = value.isEmpty() ? 0d : Double.parseDouble(value);
            } else if (type.equals("DURATION_NET")) {
               opStats.responseTimeSum = value.isEmpty() ? 0 : Long.parseLong(value);
            } else if (type.equals("TX_OVERHEAD")) {
               opStats.txOverhead = value.isEmpty() ? 0 : Long.parseLong(value);
            } else {
               // operation name may be only prefix of different operation name
               continue;
            }
            break;
         }
      }
   }

}
