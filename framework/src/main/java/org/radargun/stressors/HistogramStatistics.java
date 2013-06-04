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

package org.radargun.stressors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Statistics that store not only average values but histogram as well
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HistogramStatistics implements Statistics {

   public static final String HISTOGRAM_RANGES = "HistogramRanges";
   public static final int BUCKETS = 32;

   public static class OperationStats {
      /* right non-inclusive borders */
      long[] ranges;
      long[] counts;
      long min = Long.MAX_VALUE;
      long max = Long.MIN_VALUE;

      public OperationStats(long[] ranges) {
         this.ranges = ranges;
         counts = new long[ranges.length + 1];
      }

      public void add(long responseTime) {
         int pos = Arrays.binarySearch(ranges, responseTime);
         if (pos >= 0) {
            counts[pos]++;
         } else {
            counts[-pos - 1]++;
         }
         min = Math.min(min, responseTime);
         max = Math.max(max, responseTime);
      }

      public void reset() {
         for (int i = 0; i < counts.length; ++i) {
            counts[i] = 0;
         }
         min = Long.MAX_VALUE;
         max = Long.MIN_VALUE;
      }

      public OperationStats copy() {
         OperationStats stats = new OperationStats(ranges);
         stats.counts = Arrays.copyOf(counts, counts.length);
         stats.min = min;
         stats.max = max;
         return stats;
      }

      public void merge(OperationStats other) {
         if (!Arrays.equals(ranges, other.ranges)) throw new IllegalArgumentException();
         for (int i = 0; i < counts.length; ++i) {
            counts[i] += other.counts[i];
         }
         min = Math.min(min, other.min);
         max = Math.max(max, other.max);
      }

      @Override
      public String toString() {
         if (max == Long.MIN_VALUE) {
            return "";
         }
         StringBuilder sb = new StringBuilder();
         sb.append(min).append(':');
         for (int i = 0; i < ranges.length; ++i) {
            sb.append(ranges[i]).append(':');
         }
         sb.append(max).append('=');
         for (int i = 0; i < counts.length - 1; ++i) {
            sb.append(counts[i]).append(':');
         }
         sb.append(counts[counts.length - 1]);
         return sb.toString();
      }
   }

   private OperationStats[] nonTxStats = new OperationStats[Operation.values().length];
   private OperationStats[] incTxStats = new OperationStats[Operation.values().length];

   public HistogramStatistics(Map<String, Object> ranges) {
      this(convertToOperationMap(ranges, false), convertToOperationMap(ranges, true));
   }

   public static Map<Operation, long[]> convertToOperationMap(Map<String, Object> ranges, boolean tx) {
      Map<Operation, long[]> converted = new HashMap<Operation, long[]>(ranges.size());
      for (Map.Entry<String, Object> entry : ranges.entrySet()) {
         if (entry.getKey().endsWith("_TX") == tx) {
            String opName = entry.getKey().substring(0, entry.getKey().lastIndexOf('_'));
            Operation op;
            try {
               op = Enum.valueOf(Operation.class, opName);
            } catch (IllegalArgumentException ex) {
               continue;
            }
            converted.put(op, (long []) entry.getValue());
         }
      }
      return converted;
   }

   public static Map<String, Object> convertToResultsMap(Map<Operation, long[]> nonTx, Map<Operation, long[]> incTx) {
      Map<String, Object> results = new HashMap<String, Object>();
      for (Map.Entry<Operation, long[]> entry : nonTx.entrySet()) {
         results.put(entry.getKey().name() + "_NET", entry.getValue());
      }
      for (Map.Entry<Operation, long[]> entry : incTx.entrySet()) {
         results.put(entry.getKey().name() + "_TX", entry.getValue());
      }
      return results;
   }

   public HistogramStatistics(Map<Operation, long[]> nonTxRanges, Map<Operation, long[]> incTxRanges) {
      for (Operation op : Operation.values()) {
         nonTxStats[op.ordinal()] = new OperationStats(nonTxRanges.get(op));
         incTxStats[op.ordinal()] = new OperationStats(incTxRanges.get(op));
      }
   }

   private HistogramStatistics() {
   }

   @Override
   public void registerRequest(long responseTime, long txOverhead, Operation operation) {
      nonTxStats[operation.ordinal()].add(responseTime);
      incTxStats[operation.ordinal()].add(responseTime + txOverhead);
   }

   @Override
   public void registerError(long responseTime, long txOverhead, Operation operation) {
      // errors are ignored
   }

   @Override
   public void reset(long time) {
      for (OperationStats hist : nonTxStats) {
         hist.reset();
      }
      for (OperationStats hist : incTxStats) {
         hist.reset();
      }
   }

   @Override
   public Statistics copy() {
      HistogramStatistics stats = new HistogramStatistics();
      for (Operation op : Operation.values()) {
         stats.nonTxStats[op.ordinal()] = nonTxStats[op.ordinal()].copy();
         stats.incTxStats[op.ordinal()] = incTxStats[op.ordinal()].copy();
      }
      return stats;
   }

   @Override
   public void merge(Statistics otherStats) {
      if (!(otherStats instanceof HistogramStatistics)) throw new IllegalArgumentException();
      for (Operation op : Operation.values()) {
         nonTxStats[op.ordinal()].merge(((HistogramStatistics) otherStats).nonTxStats[op.ordinal()]);
         incTxStats[op.ordinal()].merge(((HistogramStatistics) otherStats).incTxStats[op.ordinal()]);
      }
   }

   @Override
   public Map<String, Object> getResultsMap(int threads, String prefix) {
      Map<String, Object> results = new HashMap<String, Object>();
      for (Operation op : Operation.values()) {
         if (nonTxStats[op.ordinal()].max != Long.MIN_VALUE) {
            results.put(prefix + op.name() + "_NET_HISTOGRAM", nonTxStats[op.ordinal()].toString());
         }
         if (incTxStats[op.ordinal()].max != Long.MIN_VALUE) {
            results.put(prefix + op.name() + "_TX_HISTOGRAM", incTxStats[op.ordinal()].toString());
         }
      }
      return results;
   }

   @Override
   public double getOperationsPerSecond() {
      return -1;
   }
}
