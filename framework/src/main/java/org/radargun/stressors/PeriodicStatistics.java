package org.radargun.stressors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics for tests where the request response time is expected to change during the test execution.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PeriodicStatistics implements Statistics {
   private final Statistics prototype;
   private final long period;
   private final List<Statistics> buckets;
   private long start = Long.MIN_VALUE;
   private final AtomicLong commonStart;

   public PeriodicStatistics(Statistics prototype, long period) {
      this.prototype = prototype;
      this.period = period;
      this.buckets = new ArrayList<Statistics>();
      this.commonStart = new AtomicLong(Long.MIN_VALUE);
   }

   public PeriodicStatistics(PeriodicStatistics other) {
      this.prototype = other.prototype;
      this.period = other.period;
      this.buckets = new ArrayList<Statistics>(other.buckets.size());
      this.commonStart = other.commonStart;
      for (Statistics s : other.buckets) {
         this.buckets.add(s.copy());
      }
   }

   private Statistics getCurrentBucket() {
      long currentTime = System.nanoTime();
      if (start == Long.MIN_VALUE) {
         if (commonStart.compareAndSet(Long.MIN_VALUE, currentTime)) {
            start = currentTime;
         } else {
            start = commonStart.get();
         }
      }
      int bucket = (int)((currentTime - start) / period);
      while (buckets.size() <= bucket) {
         buckets.add(prototype.copy());
      }
      return buckets.get(bucket);
   }

   @Override
   public void registerRequest(long responseTime, long txOverhead, Operation operation) {
      getCurrentBucket().registerRequest(responseTime, txOverhead, operation);
   }

   @Override
   public void registerError(long responseTime, long txOverhead, Operation operation) {
      getCurrentBucket().registerError(responseTime, txOverhead, operation);
   }

   @Override
   public void reset(long time) {
      buckets.clear();
   }

   @Override
   public Statistics copy() {
      return new PeriodicStatistics(this);
   }

   @Override
   public void merge(Statistics otherStats) {
      if (!(otherStats instanceof PeriodicStatistics)) {
         throw new IllegalArgumentException();
      }
      PeriodicStatistics other = (PeriodicStatistics) otherStats;

      if (other.period != this.period) {
         throw new IllegalArgumentException(String.format("Period: this = %d, other = %d", this.period, other.period));
      }
      if (this.start == Long.MIN_VALUE) {
         this.start = other.start;
      } else if (other.start != this.start) {
         throw new IllegalArgumentException(String.format("Start: this = %d, other = %d", this.start, other.start));
      }

      int minSize = Math.min(this.buckets.size(), other.buckets.size());
      for (int i = 0; i < minSize; ++i) {
         this.buckets.get(i).merge(other.buckets.get(i));
      }
      for (int i = this.buckets.size(); i < other.buckets.size(); ++i) {
         this.buckets.add(other.buckets.get(i).copy());
      }
   }

   @Override
   public Map<String, Object> getResultsMap(int threads, String prefix) {
      HashMap<String, Object> results = new HashMap<String, Object>();
      for (int i = 0; i < buckets.size(); ++i) {
         results.putAll(buckets.get(i).getResultsMap(threads, String.format("%s%d.", prefix, i)));
      }
      return results;
   }

   @Override
   public double getOperationsPerSecond(boolean includeOverhead) {
      double sum = 0;
      for (Statistics s : buckets) {
         sum += s.getOperationsPerSecond(includeOverhead);
      }
      return sum / buckets.size();
   }
}
