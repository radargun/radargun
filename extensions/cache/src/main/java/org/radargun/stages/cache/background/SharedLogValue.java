package org.radargun.stages.cache.background;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

/**
 * Log value that can be written by multiple stressors.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SharedLogValue implements Serializable {
   private final int[] threadIds;
   private final long[] operationIds;

   public SharedLogValue(int threadId, long operationId) {
      threadIds = new int[] {threadId};
      operationIds = new long[] {operationId};
   }

   protected SharedLogValue(int[] threadIds, long[] operationIds) {
      this.threadIds = threadIds;
      this.operationIds = operationIds;
   }

   public SharedLogValue with(int threadId, long operationId) {
      int[] newThreadIds = new int[threadIds.length + 1];
      System.arraycopy(threadIds, 0, newThreadIds, 0, threadIds.length);
      newThreadIds[threadIds.length] = threadId;
      long[] newOperationIds = new long[operationIds.length + 1];
      System.arraycopy(operationIds, 0, newOperationIds, 0, operationIds.length);
      newOperationIds[operationIds.length] = operationId;
      return new SharedLogValue(newThreadIds, newOperationIds);
   }

   public SharedLogValue with(int threadId, long operationId, Map<Integer, Long> checkedOperationIds) {
      int toRemoveCount = 0;
      for (int i = 0; i < threadIds.length; ++i) {
         long checked = checkedOperationIds.get(threadIds[i]);
         if (operationIds[i] <= checked) {
            ++toRemoveCount;
         }
      }
      int[] newThreadIds = new int[threadIds.length - toRemoveCount + 1];
      long[] newOperationIds = new long[operationIds.length - toRemoveCount + 1];
      for (int i = 0, j = 0; i < threadIds.length; ++i) {
         long checked = checkedOperationIds.get(threadIds[i]);
         if (operationIds[i] > checked) {
            newThreadIds[j] = threadIds[i];
            newOperationIds[j] = operationIds[i];
            ++j;
         }
      }
      newThreadIds[newThreadIds.length - 1] = threadId;
      newOperationIds[newOperationIds.length - 1] = operationId;
      return new SharedLogValue(newThreadIds, newOperationIds);
   }

   public SharedLogValue join(SharedLogValue other) {
      // reserve enough space
      // the values will likely have the same beginning
      int commonIndex;
      int minLength = Math.min(threadIds.length, other.threadIds.length);
      for (commonIndex = 0; commonIndex < minLength; ++commonIndex) {
         if (threadIds[commonIndex] != other.threadIds[commonIndex] || operationIds[commonIndex] != other.operationIds[commonIndex]) {
            break;
         }
      }
      if (commonIndex == threadIds.length) {
         return other;
      } else if (commonIndex == other.threadIds.length) {
         return this;
      } else {
         HashSet<ThreadOperation> operations = new HashSet<ThreadOperation>();
         for (int i = commonIndex; i < threadIds.length; ++i) {
            operations.add(new ThreadOperation(threadIds[i], operationIds[i]));
         }
         for (int i = commonIndex; i < other.threadIds.length; ++i) {
            operations.add(new ThreadOperation(other.threadIds[i], other.operationIds[i]));
         }
         int[] newThreadIds = new int[commonIndex + operations.size()];
         long[] newOperationIds = new long[commonIndex + operations.size()];
         System.arraycopy(threadIds, 0, newThreadIds, 0, commonIndex);
         System.arraycopy(operationIds, 0, newOperationIds, 0, commonIndex);
         for (ThreadOperation operation : operations) {
            newThreadIds[commonIndex] = operation.threadId;
            newOperationIds[commonIndex] = operation.operationId;
            ++commonIndex;
         }
         return new SharedLogValue(newThreadIds, newOperationIds);
      }
   }

   public long minFrom(int threadId) {
      long operationId = Long.MAX_VALUE;
      for (int i = 0; i < threadIds.length; ++i) {
         if (threadIds[i] == threadId)
            operationId = Math.min(operationIds[i], operationId);
      }
      return operationId;
   }

   public boolean contains(int threadId, long operationId) {
      for (int i = threadIds.length - 1; i >= 0; i--) {
         if (threadIds[i] == threadId && operationIds[i] == operationId)
            return true;
      }
      return false;
   }

   public int size() {
      return threadIds.length;
   }

   public int getThreadId(int index) {
      return threadIds[index];
   }

   public long getOperationId(int index) {
      return operationIds[index];
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SharedLogValue that = (SharedLogValue) o;

      if (!Arrays.equals(operationIds, that.operationIds)) return false;
      if (!Arrays.equals(threadIds, that.threadIds)) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = Arrays.hashCode(threadIds);
      result = 31 * result + Arrays.hashCode(operationIds);
      return result;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("[ #").append(threadIds.length).append(": ");
      for (int i = 0; i < threadIds.length; ++i) {
         sb.append(threadIds[i]).append('~').append(operationIds[i]).append(", ");
      }
      return sb.append(']').toString();
   }

   private static class ThreadOperation {
      public final int threadId;
      public final long operationId;

      private ThreadOperation(int threadId, long operationId) {
         this.threadId = threadId;
         this.operationId = operationId;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         ThreadOperation that = (ThreadOperation) o;

         if (operationId != that.operationId) return false;
         if (threadId != that.threadId) return false;

         return true;
      }

      @Override
      public int hashCode() {
         int result = threadId;
         result = 31 * result + (int) (operationId ^ (operationId >>> 32));
         return result;
      }
   }
}
