package org.radargun.stats;

import java.io.Serializable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface OperationStats extends Serializable {
   OperationStats copy();

   void merge(OperationStats other);

   void registerRequest(long responseTime);

   void registerError(long responseTime);

   <T> T getRepresentation(Class<T> clazz);

   boolean isEmpty();
}
