package org.radargun.stats;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.HistogramIterationValue;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.Percentile;
import org.radargun.stats.representation.Throughput;
import org.radargun.utils.Projections;

/**
 * Keeps several buckets for response time ranges and stores number of requests falling into this range.
 * Does not differentiate between successful and error requests.
 *
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
@DefinitionElement(name = "histogram", doc = "Stores data required for producing histogram or percentiles.")
public final class HistogramOperationStats implements OperationStats {
   @Property(doc = "Maximum value that could be recorded. Default is one hour.")
   private long maxValue = TimeUnit.HOURS.toNanos(1);

   @Property(doc = "Number of significant digits. Default is 2.")
   private int digits = 2;

   private AbstractHistogram histogram;
   private long errors = 0;
   private Histogram compacted;

   public HistogramOperationStats() {
      this.histogram = new org.HdrHistogram.Histogram(maxValue, digits);
   }

   private HistogramOperationStats(AbstractHistogram histogram) {
      this.histogram = histogram;
   }

   @Override
   public OperationStats copy() {
      return new HistogramOperationStats(getHistogram().copy());
   }

   @Override
   public void merge(OperationStats other) {
      if (other instanceof HistogramOperationStats) {
         histogram = getHistogram();
         histogram.add(((HistogramOperationStats) other).getHistogram());
         if (compacted != null) {
            compact();
         }
      } else {
         throw new IllegalArgumentException(String.valueOf(other));
      }
   }

   @Override
   public void registerRequest(long responseTime) {
      histogram.recordValue(responseTime);
   }

   @Override
   public void registerError(long responseTime) {
      histogram.recordValue(responseTime);
      errors++;
   }

   @Override
   public <T> T getRepresentation(Class<T> clazz, Object... args) {
      AbstractHistogram histogram = getHistogram();
      if (clazz == DefaultOutcome.class) {
         return (T) new DefaultOutcome(histogram.getTotalCount(), errors, histogram.getMean(), histogram.getMaxValue());
      } else if (clazz == MeanAndDev.class) {
         return (T) new MeanAndDev(histogram.getMean(), histogram.getStdDeviation());
      } else if (clazz == Throughput.class) {
         return (T) Throughput.compute(histogram.getTotalCount(), histogram.getMean(), args);
      } else if (clazz == Percentile.class) {
         double percentile = Percentile.getPercentile(args);
         return (T) new Percentile(histogram.getValueAtPercentile(percentile));
      } else if (clazz == Histogram.class) {
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
         return (T) new Histogram(Projections.toLongArray(ranges), Projections.toLongArray(counts));
      } else {
         return null;
      }
   }

   @Override
   public boolean isEmpty() {
      return getHistogram().getTotalCount() == 0;
   }

   public void compact() {
      if (compacted != null) return;
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
      compacted = new Histogram(Projections.toLongArray(ranges), Projections.toLongArray(counts));
      AbstractHistogram temp = histogram;
      histogram = null;
      AbstractHistogram revived = getHistogram();
      if (temp.getTotalCount() != revived.getTotalCount()) throw new IllegalStateException(temp.getTotalCount() + " vs. " + revived.getTotalCount());
      if (!temp.equals(revived)) {
         throw new IllegalStateException("different");
      }
   }

   protected AbstractHistogram getHistogram() {
      if (histogram != null) {
         return histogram;
      }
      AbstractHistogram hist = new org.HdrHistogram.Histogram(maxValue, digits);
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
