package org.radargun.stats;

import java.util.ArrayList;
import java.util.Arrays;

import org.radargun.config.DefinitionElement;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.Percentile;
import org.radargun.utils.Projections;

/**
 * This class remembers all requests as these came, storing them in memory.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "all", doc = "Operation statistics recording all requests' response times.")
public class AllRecordingOperationStats implements OperationStats {
   private static final int INITIAL_CAPACITY = (1 << 10);
   private static final int MAX_CAPACITY = (1 << 20); // max 8MB

   /* We don't use ArrayList because it would box all the longs */
   private long[] responseTimes = new long[INITIAL_CAPACITY];
   private int pos = 0;
   private boolean full = false;


   @Override
   public void registerRequest(long responseTime) {
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
      responseTimes[pos++] = responseTime;
   }

   @Override
   public void registerError(long responseTime) {
      registerRequest(responseTime);
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
      AllRecordingOperationStats copy = new AllRecordingOperationStats();
      copy.responseTimes = Arrays.copyOf(responseTimes, responseTimes.length);
      copy.full = full;
      copy.pos = pos;
      return copy;
   }

   @Override
   public <T> T getRepresentation(Class<T> clazz, Object... args) {
      if (clazz == DefaultOutcome.class) {
         long max = 0;
         long responseTimeSum = 0;
         long requests = full ? responseTimes.length : pos;
         for (int i = 0; i < requests; ++i) {
            responseTimeSum += responseTimes[i];
            max = Math.max(max, responseTimes[i]);
         }
         return (T) new DefaultOutcome(requests, 0, (double) responseTimeSum / requests, max);
      } else if (clazz == Percentile.class) {
         double percentile = Percentile.getPercentile(args);
         int size = full ? responseTimes.length : pos;
         Arrays.sort(responseTimes, 0, size);
         return (T) new Percentile(responseTimes[Math.min((int) Math.ceil(percentile / 100d * size), size - 1)]);
      } else if (clazz == Histogram.class) {
         int buckets = Histogram.getBuckets(args);
         double percentile = Histogram.getPercentile(args);
         int size = full ? responseTimes.length : pos;
         ArrayList<Long> ranges = new ArrayList<>();
         ArrayList<Long> counts = new ArrayList<>();
         Arrays.sort(responseTimes, 0, size);
         long min = size == 0 ? 1 : responseTimes[0];
         int end = (int) Math.ceil(size * percentile / 100);
         long max = size == 0 ? 1 : responseTimes[end];
         double exponent = Math.pow((double) max / (double) min, 1d / buckets);
         double current = min * exponent;
         long accCount, lastCount = 0;
         for (accCount = 0; accCount < end;) {
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
         return (T) new Histogram(Projections.toLongArray(ranges), Projections.toLongArray(counts));
      } else {
         return null;
      }
   }

   @Override
   public boolean isEmpty() {
      return pos == 0 && !full;
   }
}
