package org.radargun.stats;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.radargun.stats.representation.DataThroughput;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.stats.representation.Percentile;

/**
 * Underlying statistical data gathered for data processing operations.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class DataOperationStats extends AllRecordingOperationStats {
   protected long totalBytes = 0;

   /**
    * 
    * Set the amount of data processed in the operation to calculate DataThrouput
    * 
    * @param totalBytes
    *           data size in bytes
    */
   public void setTotalBytes(Long totalBytes) {
      if (totalBytes != null) {
         this.totalBytes = totalBytes.longValue();
      }
   }

   public String getResponseTimes() {
      String result = "";
      long requests = full ? responseTimes.length : pos;
      for (int i = 0; i < requests; ++i) {
         if (result.length() == 0) {
            result += responseTimes[i];
         } else {
            result += "," + responseTimes[i];
         }
      }
      return result;
   }

   @Override
   public void merge(OperationStats o) {
      super.merge(o);
      DataOperationStats other = (DataOperationStats) o;
      this.totalBytes = other.totalBytes;
   }

   @Override
   public OperationStats copy() {
      DataOperationStats copy = new DataOperationStats();
      copy.responseTimes = Arrays.copyOf(responseTimes, responseTimes.length);
      copy.full = full;
      copy.pos = pos;
      copy.totalBytes = totalBytes;
      return copy;
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T getRepresentation(Class<T> clazz, Object... args) {
      if (clazz == DataThroughput.class) {
         return (T) DataThroughput.compute(totalBytes, getMax());
      } else if (clazz == MeanAndDev.class) {
         double temp = 0;
         long requests = full ? responseTimes.length : pos;
         for (int i = 0; i < requests; ++i) {
            temp += responseTimes[i];
         }
         double mean = temp / requests;
         temp = 0;
         for (int i = 0; i < requests; ++i) {
            temp += ((mean - responseTimes[i]) * (mean - responseTimes[i]));
         }
         return (T) new MeanAndDev(mean, Math.sqrt(temp / requests));
      } else if (clazz == DefaultOutcome.class) {
         long responseTimeSum = 0;
         long responseTimeMax = 0;
         long requests = full ? responseTimes.length : pos;
         for (int i = 0; i < requests; ++i) {
            responseTimeSum += responseTimes[i];
            responseTimeMax = Math.max(responseTimeMax, responseTimes[i]);
         }

         return (T) new DefaultOutcome(requests, errors, TimeUnit.NANOSECONDS.convert(responseTimeSum,
               TimeUnit.MILLISECONDS) / (requests * 1.0), TimeUnit.NANOSECONDS.convert(responseTimeMax,
               TimeUnit.MILLISECONDS));
      } else if (clazz == Histogram.class) {
         //TODO: Find out why this causes an "IllegalArgumentException: Range(double, double): require lower <= upper" error
         return null;
      } else {
         return super.getRepresentation(clazz, args);
      }
   }

   private long getMax() {
      long max = Long.MIN_VALUE;
      long requests = full ? responseTimes.length : pos;
      for (int i = 0; i < requests; ++i) {
         max = Math.max(max, responseTimes[i]);
      }
      return max;
   }

}
