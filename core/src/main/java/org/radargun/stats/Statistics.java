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
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Statistics extends Serializable {
   void begin();
   void end();
   void reset();

   void registerRequest(long responseTime, Operation operation);
   void registerError(long responseTime, Operation operation);

   Statistics newInstance();
   Statistics copy();
   void merge(Statistics otherStats);


   long getBegin();
   long getEnd();

   /* We return it by name as the ids can differ on nodes */
   Map<String, OperationStats> getOperationsStats();

   /* Util method, execute only on the same node */
   <T> T[] getRepresentations(Class<T> clazz);
}
