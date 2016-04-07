package org.radargun.stats;

import java.util.ArrayList;
import java.util.Arrays;

import org.radargun.config.DefinitionElement;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.stats.representation.Percentile;

/**
 * This class remembers all requests as these came, storing them in memory.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "all", doc = "Operation statistics recording all requests' response times.")
public class AllRecordingOperationStats implements OperationStats {

   private static final int INITIAL_CAPACITY = (1 << 10);
   protected static final int MAX_CAPACITY = (1 << 20); // max 8MB

   /* We don't use ArrayList because it would box all the longs */
   protected long[] responseTimes = new long[INITIAL_CAPACITY];
   protected int pos = 0;
   protected boolean full = false;
   protected long errors;

   /**
    *
    * Factory method to use in the copy method
    *
    * @return a new AllRecordingOperationStats instance
    */
   @Override
   public AllRecordingOperationStats newInstance() {
      return new AllRecordingOperationStats();
   }

   @Override
   public void record(Request request) {
      ensureCapacity();
      responseTimes[pos++] = request.duration();
      if (!request.isSuccessful()) {
         errors++;
      }
   }

   @Override
   public void record(Message message) {
      if (message.isValid()) {
         ensureCapacity();
         responseTimes[pos++] = message.totalTime();
      } else {
         errors++;
      }
   }

   @Override
   public void record(RequestSet requestSet) {
      ensureCapacity();
      responseTimes[pos++] = requestSet.sumDurations();
      if (!requestSet.isSuccessful()) {
         errors++;
      }
   }

   public void ensureCapacity() {
      if (pos >= responseTimes.length) {
         int newCapacity = Math.min(responseTimes.length << 1, MAX_CAPACITY);
         if (newCapacity <= responseTimes.length) {
            pos = 0;
            full = true;
         }
         long[] temp = new long[newCapacity];
         System.arraycopy(responseTimes, 0, temp, 0, responseTimes.length);
         responseTimes = temp;
      }
   }

   @Override
   public void merge(OperationStats o) {
      if (!(o instanceof AllRecordingOperationStats)) throw new IllegalArgumentException();
      AllRecordingOperationStats other = (AllRecordingOperationStats) o;
      int mySize = full ? responseTimes.length : pos;
      int otherSize = other.full ? other.responseTimes.length : other.pos;
      if (mySize + otherSize > responseTimes.length) {
         // when merging, ignore the capacity limit
         long[] temp = new long[mySize + otherSize];
         System.arraycopy(responseTimes, 0, temp, 0, mySize);
         responseTimes = temp;
      }
      System.arraycopy(other.responseTimes, 0, responseTimes, mySize, otherSize);
      pos = mySize + otherSize;
      full = responseTimes.length > MAX_CAPACITY;
   }

   @Override
   public OperationStats copy() {
      AllRecordingOperationStats copy = this.newInstance();
      copy.responseTimes = Arrays.copyOf(responseTimes, responseTimes.length);
      copy.full = full;
      copy.pos = pos;
      return copy;
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T getRepresentation(Class<T> clazz, Statistics ownerStatistics, Object... args) {
      long requests = full ? responseTimes.length : pos;
      if (clazz == DefaultOutcome.class) {
         return (T) new DefaultOutcome(requests, errors, getMeanDuration(), getMaxDuration());
      } else if (clazz == OperationThroughput.class) {
         return (T) OperationThroughput.compute(requests, errors, ownerStatistics);
      } else if (clazz == Percentile.class) {
         double percentile = Percentile.getPercentile(args);
         int size = full ? responseTimes.length : pos;
         Arrays.sort(responseTimes, 0, size);
         return (T) new Percentile(responseTimes[Math.min((int) Math.ceil(percentile / 100d * size), size - 1)]);
      } else if (clazz == Histogram.class) {
         return (T) getHistogram(args);
      } else if (clazz == MeanAndDev.class) {
         double temp = 0;
         double mean = getMeanDuration();
         for (int i = 0; i < requests; ++i) {
            temp += ((mean - responseTimes[i]) * (mean - responseTimes[i]));
         }
         if (requests < 2) {
            return (T) new MeanAndDev(mean, 0);
         } else {
            return (T) new MeanAndDev(mean, Math.sqrt(temp / (requests - 1)));
         }
      } else {
         return null;
      }
   }

   private <T> Histogram getHistogram(Object[] args) {
      int size = full ? responseTimes.length : pos;
      Arrays.sort(responseTimes, 0, size);

      if (args.length == 0) {
         long[] ranges = new long[size + 1];
         System.arraycopy(responseTimes, 0, ranges, 0, size);
         ranges[size] = responseTimes[size - 1];
         long[] counts = new long[size];
         Arrays.fill(counts, 1L);
         return new Histogram(ranges, counts);
      } else {
         ArrayList<Long> ranges = new ArrayList<>();
         ArrayList<Long> counts = new ArrayList<>();
         int buckets = Histogram.getBuckets(args);
         double percentile = Histogram.getPercentile(args);
         int end = (int) Math.ceil(size * percentile / 100);

         long min = size == 0 ? 1 : responseTimes[0];
         long max = size == 0 ? 1 : responseTimes[end];
         double exponent = Math.pow((double) max / (double) min, 1d / buckets);
         double current = min * exponent;
         long accCount, lastCount = 0;
         for (accCount = 0; accCount < end; ) {
            long responseTime = responseTimes[(int) accCount];
            accCount++;
            if (responseTime >= current) {
               ranges.add(responseTime);
               counts.add(accCount - lastCount);
               lastCount = accCount;
               current = current * exponent;
            }
         }
         if (accCount > 0) {
            ranges.add(max);
            counts.add(accCount - lastCount);
         }
         return new Histogram(ranges.stream().mapToLong(l -> l).toArray(), counts.stream().mapToLong(l -> l).toArray());
      }
   }

   @Override
   public boolean isEmpty() {
      return pos == 0 && !full;
   }

   protected long getMaxDuration() {
      long max = Long.MIN_VALUE;
      long requests = full ? responseTimes.length : pos;
      if (requests == 0) {
         return 0;
      } else {
         for (int i = 0; i < requests; ++i) {
            max = Math.max(max, responseTimes[i]);
         }
         return max;
      }
   }

   protected long getMinDuration() {
      long min = Long.MAX_VALUE;
      long requests = full ? responseTimes.length : pos;
      if (requests == 0) {
         return 0;
      } else {
         for (int i = 0; i < requests; ++i) {
            min = Math.min(min, responseTimes[i]);
         }
         return min;
      }
   }

   protected double getMeanDuration() {
      long durationSum = 0;
      long requests = full ? responseTimes.length : pos;
      if (requests == 0) {
         return 0;
      } else {
         for (int i = 0; i < requests; ++i) {
            durationSum += responseTimes[i];
         }
         return durationSum / requests;
      }
   }
}
