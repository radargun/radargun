package org.radargun.stages.cache.background;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Log value that is written by single stressor - keeps his ID.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PrivateLogValue implements Serializable {
   private final int threadId;
   private final long[] operationIds;
   public PrivateLogValue(int threadId, long operationId) {
      this.threadId = threadId;
      operationIds = new long[] { operationId };
   }

   private PrivateLogValue(int threadId, long[] operationIds) {
      this.threadId = threadId;
      this.operationIds = operationIds;
   }

   public PrivateLogValue with(long operationId) {
      long[] newOperationIds = new long[operationIds.length + 1];
      System.arraycopy(operationIds, 0, newOperationIds, 0, operationIds.length);
      newOperationIds[operationIds.length] = operationId;
      return new PrivateLogValue(threadId, newOperationIds);
   }

   public PrivateLogValue shift(int checkedValues, long operationId) {
      long[] newOperationIds = new long[operationIds.length - checkedValues + 1];
      System.arraycopy(operationIds, checkedValues, newOperationIds, 0, operationIds.length - checkedValues);
      newOperationIds[operationIds.length - checkedValues] = operationId;
      return new PrivateLogValue(threadId, newOperationIds);
   }

   public int size() {
      return operationIds.length;
   }

   public long getOperationId(int i) {
      return operationIds[i];
   }

   public int getThreadId() {
      return threadId;
   }

   public boolean contains(long operationId) {
      for (long id : operationIds) {
         if (id == operationId) {
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean equals(Object obj) {
      if (!(obj instanceof PrivateLogValue)) return false;
      PrivateLogValue other = (PrivateLogValue) obj;
      if (other.threadId != threadId) return false;
      return Arrays.equals(other.operationIds, operationIds);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("[").append(threadId).append(" #").append(operationIds.length).append(": ");
      for (long op : operationIds) {
         sb.append(op).append(", ");
      }
      return sb.append("]").toString();
   }
}
