package org.radargun.reporting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
public class HistogramData {
   public final static int BUCKETS = 32;

   public final String config;
   public final int clusterSize;
   public final int iteration;
   public final String requestType;
   public final boolean tx;

   List<long[]> rangesList = new ArrayList<long[]>();
   List<long[]> countsList = new ArrayList<long[]>();
   private long[] ranges;
   private long[] counts;

   public HistogramData(String config, int clusterSize, int iteration, String requestType, boolean tx) {
      this.config = config;
      this.clusterSize = clusterSize;
      this.iteration = iteration;
      this.requestType = requestType;
      this.tx = tx;
   }

   public String getFileName(String prefix) {
      return String.format("%s_%s_%d_%d_%s_histogram.png", prefix,
            config, clusterSize, iteration, requestType);
   }

   public void mergeConstantHeight() {
      SortedSet<Long> rr = new TreeSet<Long>();
      for (long[] ranges : rangesList) {
         for (int i = 0; i < ranges.length; ++i) {
            rr.add(ranges[i]);
         }
      }
      long[] mergeRanges = new long[rr.size()];
      {
         int i = 0;
         for (Long l : rr) mergeRanges[i++] = l;
      }
      long[] mergeCounts = new long[mergeRanges.length - 1];
      distributeAll(mergeRanges, mergeCounts);
      long[] cumulativeSum = new long[mergeCounts.length];
      long sum = 0;
      for (int i = 0; i < mergeCounts.length; ++i) {
         sum += mergeCounts[i];
         cumulativeSum[i] = sum;
      }
      long[] resultRanges = new long[BUCKETS + 1];
      long[] resultCounts = new long[BUCKETS];
      resultRanges[0] = mergeRanges[0];
      int resultIndex = 0;
      long prevSum = 0;
      for (int i = 0; i < cumulativeSum.length && resultIndex < BUCKETS; ++i) {
         if (cumulativeSum[i] >= (sum * resultIndex)/BUCKETS) {
            resultRanges[resultIndex + 1] = mergeRanges[i + 1];
            resultCounts[resultIndex] = cumulativeSum[i] - prevSum;
            prevSum = cumulativeSum[i];
            resultIndex++;
         }
      }
      this.ranges = resultRanges;
      this.counts = resultCounts;
   }

   private void distributeAll(long[] mergeRanges, long[] mergeCounts) {
      Iterator<long[]> rangesIterator = rangesList.iterator();
      Iterator<long[]> countsIterator = countsList.iterator();
      while (rangesIterator.hasNext()) {
         distribute(mergeRanges, mergeCounts, rangesIterator.next(), countsIterator.next());
      }
   }

   public void mergeConstantLogWidth() {
      long min = Long.MAX_VALUE, max = 0;
      for (long[] ranges : rangesList) {
         min = Math.min(min, ranges[0]);
         max = Math.max(max, ranges[ranges.length - 1]);
      }
      final double base = Math.pow((double) max / (double) min, 1d/BUCKETS);
      ranges = new long[BUCKETS + 1];
      double limit = min;
      for (int i = 0; i <= BUCKETS; ++i) {
         this.ranges[i] = (long) limit;
         limit *= base;
      }
      ranges[BUCKETS] = max;
      counts = new long[BUCKETS];
      distributeAll(ranges, counts);
   }

   private void distribute(long[] resultRanges, long[] resultCounts, long[] myRanges, long[] myCounts) {
      int resultIndex = 1;
      long myPrevLimit = 0;
      long resultPrevLimit = 0;
      while (resultRanges[resultIndex] < myRanges[0]) {
         resultPrevLimit = resultRanges[resultIndex];
         resultIndex++;
      }
      for (int i = 1; i < myRanges.length; ++i) {
         long myInterval = myRanges[i] - myPrevLimit;
         long myCount = myCounts[i - 1];
         while (myInterval > 0 && resultRanges[resultIndex] <= myRanges[i]) {
            long resultInterval = resultRanges[resultIndex] - resultPrevLimit;
            long pushedCount = (resultInterval * myCount) / myInterval;
            resultCounts[resultIndex - 1] += pushedCount;
            myCount -= pushedCount;
            myInterval -= resultInterval;
            resultPrevLimit = resultRanges[resultIndex++];
         }
         if (myCount > 0) {
            resultCounts[resultIndex - 1] += myCount;
         }
         myPrevLimit = myRanges[i];
      }
   }

   public void add(String[] ranges, String[] counts) {
      this.rangesList.add(toLongArray(ranges));
      this.countsList.add(toLongArray(counts));
   }

   private long[] toLongArray(String[] strings) {
      long[] arr = new long[strings.length];
      for (int i = 0; i < strings.length; ++i) {
         arr[i] = Long.parseLong(strings[i]);
      }
      return arr;
   }

   public long[] getRanges() {
      return ranges;
   }

   public long[] getCounts() {
      return counts;
   }
}
