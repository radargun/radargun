package org.radargun.stats;

import org.radargun.stats.representation.DataThroughput;
import org.radargun.stats.representation.Histogram;

/**
 * Underlying statistical data gathered for data processing operations.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class DataOperationStats extends AllRecordingOperationStats {
   protected long totalBytes = 0;

   @Override
   public DataOperationStats newInstance() {
      return new DataOperationStats();
   }

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
      DataOperationStats copy = (DataOperationStats) super.copy();
      copy.totalBytes = totalBytes;
      return copy;
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T getRepresentation(Class<T> clazz, Object... args) {
      if (clazz == DataThroughput.class) {
         return (T) DataThroughput.compute(totalBytes, responseTimes, full ? responseTimes.length : pos);
      } else if (clazz == Histogram.class) {
         //TODO: Find out why this causes an "IllegalArgumentException: Range(double, double): require lower <= upper" error
         return null;
      } else {
         return super.getRepresentation(clazz, args);
      }
   }

}
