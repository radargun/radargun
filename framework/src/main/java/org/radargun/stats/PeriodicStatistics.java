package org.radargun.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.Operation;

/**
 * Statistics for tests where the request response time is expected to change during the test execution.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PeriodicStatistics extends IntervalStatistics {
   private final Statistics prototype;
   private final long period;
   private final List<Statistics> buckets;

   public PeriodicStatistics(Statistics prototype, long period) {
      this.prototype = prototype;
      this.period = TimeUnit.MILLISECONDS.toNanos(period);
      this.buckets = new ArrayList<Statistics>();
   }

   public PeriodicStatistics(PeriodicStatistics other) {
      this.prototype = other.prototype;
      this.period = other.period;
      this.buckets = new ArrayList<Statistics>(other.buckets.size());
      for (Statistics s : other.buckets) {
         this.buckets.add(s.copy());
      }
   }

   private Statistics getCurrentBucket() {
      long currentTime = System.nanoTime();
      int bucket = (int)((currentTime - getBegin()) / period);
      while (buckets.size() <= bucket) {
         buckets.add(prototype.copy());
      }
      return buckets.get(bucket);
   }

   @Override
   public void registerRequest(long responseTime, Operation operation) {
      getCurrentBucket().registerRequest(responseTime, operation);
   }

   @Override
   public void registerError(long responseTime, Operation operation) {
      getCurrentBucket().registerError(responseTime, operation);
   }

   @Override
   public Statistics newInstance() {
      return new PeriodicStatistics(prototype, period);
   }

   @Override
   public void reset() {
      buckets.clear();
   }

   @Override
   public Statistics copy() {
      return new PeriodicStatistics(this);
   }

   @Override
   public void merge(Statistics otherStats) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Map<String, OperationStats> getOperationsStats() {
      throw new UnsupportedOperationException();
   }

   @Override
   public <T> T[] getRepresentations(Class<T> clazz) {
      throw new UnsupportedOperationException();
   }

   public List<Statistics> asList() {
      return Collections.unmodifiableList(buckets);
   }
}
