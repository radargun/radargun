/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.radargun.stats;

import java.io.Serializable;
import java.util.Map;

import org.radargun.Operation;

/**
 * Collects and provides statistics of operations executed against the service.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Statistics extends Serializable {
   /**
    * Mark this moment as start of the measurement.
    * No operations should be recorded before this call.
    */
   void begin();

   /**
    * Mark this moment as the end of the measurement.
    * No more operations should be executed after this call.
    */
   void end();

   /**
    * Clean the statistics and start the measurement again.
    */
   void reset();

   /**
    * Register response latency of successful operation.
    * @param responseTime
    * @param operation
    */
   void registerRequest(long responseTime, Operation operation);

   /**
    * Register response latency of failed operation.
    * @param responseTime
    * @param operation
    */
   void registerError(long responseTime, Operation operation);

   /**
    * Create new instance of the same class.
    */
   Statistics newInstance();

   /**
    * Create deep copy of this object
    */
   Statistics copy();

   /**
    * Add the measurements collected into another instance to this instance.
    * @param otherStats Must be of the same class as this instance.
    */
   void merge(Statistics otherStats);

   /**
    * @return Timestamp of the measurement start, in epoch milliseconds.
    */
   long getBegin();

   /**
    * @return Timestamp of the measurement end, in epoch milliseconds.
    */
   long getEnd();

   /**
    * Operation names should be identical on all nodes, as oposed to operations IDs which can differ.
    * @return Map of operations stats keyed by operations names.
    */
   Map<String, OperationStats> getOperationsStats();

   /* Util method, execute only on the same node */

   /**
    * Get particular representation of each operation stats, in array with operation IDs as indices.
    * @param clazz
    * @param <T>
    * @return
    */
   <T> T[] getRepresentations(Class<T> clazz);
}
