package org.radargun.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.radargun.Operation;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.utils.TimeConverter;

/**
 * Keeps a series of {@link Statistics} instances and records the requests according to current timestamp.
 *
 * Useful when the request response time is expected to change during the test execution.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "periodic", doc = "Periodically switches the statistics where the operation is recorded.")
public class PeriodicStatistics extends IntervalStatistics implements IterationStatistics {
   @Property(name = "implementation", doc = "Operation statistics prototype. Default is DefaultStatistics.", complexConverter = Statistics.Converter.class)
   private Statistics prototype = new DefaultStatistics();

   @Property(doc = "Duration of one sample.", optional = false, converter = TimeConverter.class)
   private long period;

   private final List<IterationStatistics.Iteration> buckets;

   public PeriodicStatistics(Statistics prototype, long period) {
      this.prototype = prototype;
      this.period = period;
      this.buckets = new ArrayList<>();
   }

   public PeriodicStatistics(PeriodicStatistics other) {
      this.prototype = other.prototype;
      this.period = other.period;
      this.buckets = new ArrayList<>(other.buckets.size());
      for (Iteration s : other.buckets) {
         this.buckets.add(new Iteration(s.name, s.statistics.copy()));
      }
   }

   private Statistics getCurrentBucket() {
      long currentTime = System.currentTimeMillis();
      int bucket = (int)((currentTime - getBegin()) / period);
      while (buckets.size() <= bucket) {
         buckets.add(new Iteration("Period " + bucket, prototype.copy()));
      }
      return buckets.get(bucket).statistics;
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
   public <T> T[] getRepresentations(Class<T> clazz, Object... args) {
      throw new UnsupportedOperationException();
   }

   public List<Iteration> getIterations() {
      return Collections.unmodifiableList(buckets);
   }
}
