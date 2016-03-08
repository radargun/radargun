package org.radargun.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.radargun.Operation;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.reporting.IterationData;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

/**
 * Keeps a series of {@link Statistics} instances and records the requests according to current timestamp.
 *
 * Useful when the request response time is expected to change during the test execution.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "periodic", doc = "Periodically switches the statistics where the operation is recorded.")
public class PeriodicStatistics extends IntervalStatistics implements IterationData<Statistics> {
   @Property(name = "implementation", doc = "Operation statistics prototype. Default is DefaultStatistics.", complexConverter = Statistics.Converter.class)
   private Statistics prototype = new DefaultStatistics();

   @Property(doc = "Duration of one sample.", optional = false, converter = TimeConverter.class)
   private long period;

   private final List<IterationData.Iteration<Statistics>> buckets;

   public PeriodicStatistics() {
      this.buckets = new ArrayList<>();
   }

   public PeriodicStatistics(Statistics prototype, long period) {
      this();
      this.prototype = prototype;
      this.period = period;
   }

   public PeriodicStatistics(PeriodicStatistics other) {
      this.prototype = other.prototype;
      this.period = other.period;
      this.buckets = new ArrayList<>(other.buckets.size());
      for (Iteration<Statistics> s : other.buckets) {
         this.buckets.add(new Iteration(s.name, s.data.copy()));
      }
   }

   private Statistics getCurrentBucket() {
      long currentTime = TimeService.currentTimeMillis();
      int bucket = (int) ((currentTime - getBegin()) / period);
      while (buckets.size() <= bucket) {
         Statistics bucketStats = prototype.copy();
         if (bucketStats instanceof IntervalStatistics) {
            IntervalStatistics intervalStats = (IntervalStatistics) bucketStats;
            intervalStats.setBegin(getBegin() + bucket * period);
            intervalStats.setEnd(getBegin() + (bucket + 1) * period);
         }
         buckets.add(new Iteration("Period " + bucket, bucketStats));
      }
      return buckets.get(bucket).data;
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
   public void end() {
      super.end();
      // Discard last bucket if it contains < 5% of the period, as those data are usually
      // just some leftovers that screw the charts
      int numBuckets = buckets.size();
      if (numBuckets == 0) return;
      Statistics lastStats = buckets.get(numBuckets - 1).data;
      if (lastStats instanceof IntervalStatistics) {
         IntervalStatistics intervalStats = (IntervalStatistics) lastStats;
         if (getEnd() - intervalStats.getBegin() < period / 20) {
            buckets.remove(numBuckets - 1);
         }
      }
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
   public <T> T[] getRepresentations(Class<T> clazz, Object... args) {
      throw new UnsupportedOperationException();
   }

   public List<Iteration<Statistics>> getIterations() {
      return Collections.unmodifiableList(buckets);
   }
}
