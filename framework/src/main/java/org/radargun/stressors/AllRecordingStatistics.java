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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class remembers all requests as these came, storing them in memory. Use only for statistics warmup
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class AllRecordingStatistics implements Statistics {

   public static class Request {
      long responseTime;
      long responseWithTx;

      private Request(long responseTime, long txOverhead) {
         this.responseTime = responseTime;
         this.responseWithTx = responseTime + txOverhead;
      }

      public long getTime(boolean tx) {
         return tx ? responseWithTx : responseTime;
      }
   }

   private List<Request>[] succesfull = new List[Operation.values().length];
   private List<Request>[] error = new List[Operation.values().length];

   public AllRecordingStatistics() {
      for (Operation op : Operation.values()) {
         succesfull[op.ordinal()] = new ArrayList<Request>();
         error[op.ordinal()] = new ArrayList<Request>();
      }
   }

   @Override
   public void registerRequest(long responseTime, long txOverhead, Operation operation) {
      succesfull[operation.ordinal()].add(new Request(responseTime, txOverhead));
   }

   @Override
   public void registerError(long responseTime, long txOverhead, Operation operation) {
      error[operation.ordinal()].add(new Request(responseTime, txOverhead));
   }

   @Override
   public void reset(long time) {
      for (Operation op : Operation.values()) {
         succesfull[op.ordinal()].clear();
         error[op.ordinal()].clear();
      }
   }

   @Override
   public Statistics copy() {
      AllRecordingStatistics statistics = new AllRecordingStatistics();
      for (Operation op : Operation.values()) {
         statistics.succesfull[op.ordinal()] = new ArrayList<Request>(succesfull[op.ordinal()]);
         statistics.error[op.ordinal()] = new ArrayList<Request>(error[op.ordinal()]);
      }
      return statistics;
   }

   @Override
   public void merge(Statistics otherStats) {
      if (!(otherStats instanceof AllRecordingStatistics)) throw new IllegalArgumentException();
      AllRecordingStatistics stats = (AllRecordingStatistics) otherStats;
      for (Operation op : Operation.values()) {
         succesfull[op.ordinal()].addAll(stats.succesfull[op.ordinal()]);
         error[op.ordinal()].addAll(stats.error[op.ordinal()]);
      }
   }

   @Override
   public double getOperationsPerSecond(boolean includeOverhead) {
      return 0;
   }

   @Override
   public Map<String, Object> getResultsMap(int threads, String prefix) {
      return HistogramStatistics.convertToResultsMap(computeNonTxRanges(HistogramStatistics.BUCKETS), computeIncTxRanges(HistogramStatistics.BUCKETS));
   }

   private Map<Operation, long[]> computeNonTxRanges(int buckets) {
      return computeRanges(buckets, false, new Comparator<Request>() {
         @Override
         public int compare(Request r1, Request r2) {
            return Long.compare(r1.responseTime, r2.responseTime);
         }
      });
   }

   private Map<Operation, long[]> computeIncTxRanges(int buckets) {
      return computeRanges(buckets, true, new Comparator<Request>() {
         @Override
         public int compare(Request r1, Request r2) {
            return Long.compare(r1.responseWithTx, r2.responseWithTx);
         }
      });
   }

   private Map<Operation, long[]> computeRanges(int buckets, boolean tx, Comparator<Request> comparator) {
      Map<Operation, long[]> rangeMap = new HashMap<Operation, long[]>();
      for (Operation op : Operation.values()) {
         Request[] requests = succesfull[op.ordinal()].toArray(new Request[0]);
         long[] range = new long[buckets - 1];
         if (requests.length != 0) {
            Arrays.sort(requests, comparator);
            for (int i = 0; i < range.length; ++i) {
               range[i] = requests[(i + 1) * requests.length / buckets].getTime(tx);
            }
         }
         rangeMap.put(op, range);
      }
      return rangeMap;
   }
}
