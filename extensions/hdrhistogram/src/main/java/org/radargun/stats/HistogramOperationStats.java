package org.radargun.stats;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.HistogramIterationValue;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.stats.representation.Percentile;
import org.radargun.utils.NanoTimeConverter;

/**
 * Keeps several buckets for response time ranges and stores number of requests falling into this range.
 * Does not differentiate between successful and error requests.
 */
@DefinitionElement(name = "histogram", doc = "Stores data required for producing histogram or percentiles.")
public final class HistogramOperationStats implements OperationStats {
   @Property(doc = "Maximum value that could be recorded. Default is one hour.", converter = NanoTimeConverter.class)
   private long maxValue = TimeUnit.HOURS.toNanos(1);

   @Property(doc = "Number of significant digits. Default is 2.")
   private int digits = 2;

   private AbstractHistogram histogram;
   private SoftReference<AbstractHistogram> soft;
   private long errors = 0;
   private Histogram compacted;

   public HistogramOperationStats() {
   }

   private HistogramOperationStats(AbstractHistogram histogram, long maxValue, int digits) {
      this.histogram = histogram;
      this.maxValue = maxValue;
      this.digits = digits;
   }

   @Init
   public void init() {
      if (histogram != null) throw new IllegalStateException("This histogram was already initialized!");
      histogram = new org.HdrHistogram.Histogram(maxValue, digits);
   }

   public void init(long maxValue, int digits) {
      this.maxValue = maxValue;
      this.digits = digits;
      init();
   }

   @Override
   public OperationStats newInstance() {
      HistogramOperationStats newInstance = new HistogramOperationStats();
      newInstance.maxValue = maxValue;
      newInstance.digits = digits;
      newInstance.init();
      return newInstance;
   }

   @Override
   public OperationStats copy() {
      return new HistogramOperationStats(getHistogram().copy(), maxValue, digits);
   }

   @Override
   public void merge(OperationStats other) {
      if (other instanceof HistogramOperationStats) {
         HistogramOperationStats otherStats = (HistogramOperationStats) other;
         histogram = getHistogram();
         histogram.add(otherStats.getHistogram());
         compact();
      } else {
         throw new IllegalArgumentException(String.valueOf(other));
      }
   }

   @Override
   public void record(Request request) {
      histogram.recordValue(request.duration());
      if (!request.isSuccessful()) {
         errors++;
      }
   }

   @Override
   public void record(Message message) {
      if (message.isValid()) {
         histogram.recordValue(message.totalTime());
      } else {
         errors++;
      }
   }

   @Override
   public void record(RequestSet requestSet) {
      histogram.recordValue(requestSet.sumDurations());
      if (!requestSet.isSuccessful()) {
         errors++;
      }
   }

   @Override
   public <T> T getRepresentation(Class<T> clazz, Statistics ownerStatistics, Object... args) {
      AbstractHistogram histogram = getHistogram();
      if (clazz == DefaultOutcome.class) {
         return (T) new DefaultOutcome(histogram.getTotalCount(), errors, histogram.getMean(), histogram.getMaxValue());
      } else if (clazz == MeanAndDev.class) {
         return (T) new MeanAndDev(histogram.getMean(), histogram.getStdDeviation());
      } else if (clazz == OperationThroughput.class) {
         return (T) OperationThroughput.compute(histogram.getTotalCount(), errors, ownerStatistics);
      } else if (clazz == Percentile.class) {
         double percentile = Percentile.getPercentile(args);
         return (T) new Percentile(histogram.getValueAtPercentile(percentile));
      } else if (clazz == Histogram.class) {
         if (args.length == 0) {
            return (T) getFullHistogram(histogram);
         } else {
            return (T) getReformattedHistogram(histogram, args);
         }
      } else {
         return null;
      }
   }

   // this is different from the compacted histogram by having the lower bound as count, and including zero values, too
   private Histogram getFullHistogram(AbstractHistogram histogram) {
      AbstractHistogram.AllValues values = histogram.allValues();
      ArrayList<Long> ranges = new ArrayList<>();
      ArrayList<Long> counts = new ArrayList<>();
      ranges.add(histogram.getMinValue());
      for (HistogramIterationValue value : values) {
         ranges.add(value.getValueIteratedTo());
         counts.add(value.getCountAddedInThisIterationStep());
      }
      return new Histogram(ranges.stream().mapToLong(l -> l).toArray(), counts.stream().mapToLong(l -> l).toArray());
   }

   private Histogram getReformattedHistogram(AbstractHistogram histogram, Object[] args) {
      int buckets = Histogram.getBuckets(args);
      double percentile = Histogram.getPercentile(args);
      AbstractHistogram.AllValues values = histogram.allValues();
      ArrayList<Long> ranges = new ArrayList<>();
      ArrayList<Long> counts = new ArrayList<>();
      long min = Math.max(histogram.getMinValue(), 1);
      long max = Math.max(histogram.getValueAtPercentile(percentile), 1);
      if (max < min) max = Math.max(histogram.getMaxValue(), min + 1);
      double exponent = Math.pow((double) max / (double) min, 1d / buckets);
      double current = min * exponent;
      long accCount = 0, lastCount = 0;
      for (HistogramIterationValue value : values) {
         accCount += value.getCountAddedInThisIterationStep();
         if (value.getValueIteratedTo() >= current) {
            ranges.add(value.getValueIteratedTo());
            counts.add(accCount - lastCount);
            lastCount = accCount;
            current = current * exponent;
         }
         if (value.getValueIteratedTo() >= max) {
            break;
         }
      }
      if (accCount > 0) {
         ranges.add(max);
         counts.add(accCount - lastCount);
      }
      return new Histogram(ranges.stream().mapToLong(l -> l).toArray(), counts.stream().mapToLong(l -> l).toArray());
   }

   @Override
   public boolean isEmpty() {
      if (compacted != null) {
         return compacted.counts.length == 0;
      } else if (histogram != null) {
         return histogram.getTotalCount() == 0;
      } else {
         throw new IllegalArgumentException();
      }
   }

   public void compact() {
      if (compacted != null) {
         // make sure that we always store only the compacted
         histogram = null;
         return;
      }
      if (histogram == null) throw new IllegalStateException("Either compacted or expanded form has to be defined!");
      AbstractHistogram.AllValues values = histogram.allValues();
      ArrayList<Long> ranges = new ArrayList<>();
      ArrayList<Long> counts = new ArrayList<>();
      for (HistogramIterationValue value : values) {
         if (value.getCountAddedInThisIterationStep() > 0) {
            ranges.add(value.getValueIteratedTo());
            counts.add(value.getCountAddedInThisIterationStep());
         }
      }
      compacted = new Histogram(ranges.stream().mapToLong(l -> l).toArray(), counts.stream().mapToLong(l -> l).toArray());
      soft = new SoftReference<>(histogram);
      histogram = null;
   }

   protected AbstractHistogram getHistogram() {
      if (histogram != null) {
         return histogram;
      }
      AbstractHistogram hist;
      if (soft != null && (hist = soft.get()) != null) {
         return hist;
      }
      hist = new org.HdrHistogram.Histogram(maxValue, digits);
      for (int i = 0; i < compacted.ranges.length; ++i) {
         hist.recordValueWithCount(compacted.ranges[i], compacted.counts[i]);
      }
      return hist;
   }

   private void writeObject(ObjectOutputStream s) throws IOException {
      s.writeLong(maxValue);
      s.writeInt(digits);
      s.writeLong(errors);
      compact();
      s.writeInt(compacted.ranges.length);
      for (int i = 0; i < compacted.ranges.length; ++i) {
         s.writeLong(compacted.ranges[i]);
         s.writeLong(compacted.counts[i]);
      }
   }

   private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
      maxValue = s.readLong();
      digits = s.readInt();
      errors = s.readLong();
      int length = s.readInt();
      long[] ranges = new long[length];
      long[] counts = new long[length];
      for (int i = 0; i < length; ++i) {
         ranges[i] = s.readLong();
         counts[i] = s.readLong();
      }
      compacted = new Histogram(ranges, counts);
   }
}
