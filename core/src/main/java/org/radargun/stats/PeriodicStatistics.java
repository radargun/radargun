package org.radargun.stats;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.radargun.Operation;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.stats.representation.AbstractSeries;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

/**
 * Keeps a series of {@link Statistics} instances and records the requests according to current timestamp.
 *
 * Useful when the request response time is expected to change during the test execution.
 */
@DefinitionElement(name = "periodic", doc = "Periodically switches the statistics where the operation is recorded.")
public class PeriodicStatistics extends IntervalStatistics {
   @Property(name = "implementation", doc = "Operation statistics prototype. Default is BasicStatistics.", complexConverter = Statistics.Converter.class)
   private Statistics prototype = new BasicStatistics();

   @Property(doc = "Duration of one sample.", optional = false, converter = TimeConverter.class)
   private long period;

   private final List<Statistics> buckets;
   private long beginNanos = Long.MAX_VALUE;

   public PeriodicStatistics() {
      this.buckets = new ArrayList<>();
   }

   PeriodicStatistics(Statistics prototype, long period) {
      this();
      this.prototype = prototype;
      this.period = period;
   }

   public PeriodicStatistics(PeriodicStatistics other) {
      super(other);
      this.prototype = other.prototype;
      this.period = other.period;
      this.buckets = new ArrayList<>(other.buckets.size());
      for (Statistics s : other.buckets) {
         this.buckets.add(s.copy());
      }
   }

   public void setPeriod(long period) {
      this.period = period;
   }

   private Statistics getCurrentBucket(long millis) {
      int bucket = (int) (millis / period);
      while (buckets.size() <= bucket) {
         Statistics bucketStats = prototype.copy();
         if (bucketStats instanceof IntervalStatistics) {
            IntervalStatistics intervalStats = (IntervalStatistics) bucketStats;
            intervalStats.setBegin(getBegin() + bucket * period);
            intervalStats.setEnd(getBegin() + (bucket + 1) * period);
         }
         buckets.add(bucketStats);
      }
      return buckets.get(bucket);
   }

   @Override
   public void record(Request request, Operation operation) {
      getCurrentBucket(TimeUnit.NANOSECONDS.toMillis(request.getRequestStartTime() - beginNanos)).record(request, operation);
   }

   @Override
   public void record(Message message, Operation operation) {
      getCurrentBucket(message.getSendStartTime() - getBegin()).record(message, operation);
   }

   @Override
   public void record(RequestSet requestSet, Operation operation) {
      getCurrentBucket(TimeUnit.NANOSECONDS.toMillis(requestSet.getBegin() - beginNanos)).record(requestSet, operation);
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
   public void begin() {
      super.begin();
      beginNanos = TimeService.nanoTime();
   }

   @Override
   public void end() {
      super.end();
      // Discard last bucket if it contains < 5% of the period, as those data are usually
      // just some leftovers that screw the charts
      int numBuckets = buckets.size();
      if (numBuckets == 0) return;
      Statistics lastStats = buckets.get(numBuckets - 1);
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
      if (!(otherStats instanceof PeriodicStatistics)) {
         throw new IllegalArgumentException(String.valueOf(otherStats));
      }
      PeriodicStatistics other = (PeriodicStatistics) otherStats;
      if (other.period != period) {
         throw new IllegalArgumentException("Different periods: " + period + " vs. " + other.period);
      }
      if (getBegin() > getEnd()) {
         throw new IllegalArgumentException("This stats don't have begin/end set correctly: " + this);
      }
      if (other.getBegin() > other.getEnd()) {
         throw new IllegalArgumentException("Other stats don't have begin/end set correctly: " + other);
      }
      long distance = Math.abs(other.getBegin() - getBegin());
      int offset = (int) (distance / period);
      if (2 * (distance - offset * period) > period) {
         ++offset;
      }
      if (other.getBegin() < getBegin() && offset > 0) {
         buckets.addAll(0, other.buckets.subList(0, offset).stream()
            .map(Statistics::copy).collect(Collectors.toList()));
         for (int i = offset; i < other.buckets.size(); ++i) {
            if (i < buckets.size()) {
               buckets.get(i).merge(other.buckets.get(i));
            } else {
               buckets.add(other.buckets.get(i).copy());
            }
         }
      } else {
         for (int i = 0; i < other.buckets.size(); ++i) {
            if (i + offset < buckets.size()) {
               buckets.get(i + offset).merge(other.buckets.get(i));
            } else {
               buckets.add(other.buckets.get(i).copy());
            }
         }
      }
      // update beginTime/endTime after buckets
      super.merge(otherStats);
   }

   @Override
   public Set<String> getOperations() {
      return buckets.stream().flatMap(s -> s.getOperations().stream()).collect(Collectors.toSet());
   }

   @Override
   public OperationStats getOperationStats(String operation) {
      throw new UnsupportedOperationException();
   }

   @Override
   public <T> T getRepresentation(String operation, Class<T> clazz, Object... args) {
      if (AbstractSeries.class.isAssignableFrom(clazz)) {
         return (T) getRepresentationSeries(operation, (Class<? extends AbstractSeries>) clazz, args);
      } else {
         return null;
      }
   }

   private <T extends AbstractSeries<R>, R> T getRepresentationSeries(String operation, Class<T> clazz, Object[] args) {
      try {
         // force the class being initialized, otherwise it wouldn't be registered
         Class.forName(clazz.getName(), true, clazz.getClassLoader());
      } catch (ClassNotFoundException e) {
         throw new IllegalStateException("No kidding!", e);
      }
      Class<R> representationClass = AbstractSeries.representation(clazz);
      if (representationClass == null) {
         throw new IllegalStateException(clazz.getName());
      }
      // haven't found a better API for this
      R[] data = (R[]) Array.newInstance(representationClass, buckets.size());
      for (int i = 0; i < buckets.size(); ++i) {
         data[i] = buckets.get(i).getRepresentation(operation, representationClass, args);
      }
      Constructor<T> seriesCtor;
      try {
         seriesCtor = clazz.getConstructor(long.class, long.class, data.getClass());
      } catch (NoSuchMethodException e) {
         throw new IllegalStateException(clazz.getName() + " does not have long, long, " + data.getClass().getName() + " constructor", e);
      }
      try {
         return seriesCtor.newInstance(getBegin(), period, data);
      } catch (Exception e) {
         throw new IllegalStateException("Cannot instantiate series " + clazz.getName(), e);
      }
   }
}
