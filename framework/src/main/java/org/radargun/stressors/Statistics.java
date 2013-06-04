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

package org.radargun.stressors;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Statistics extends Serializable {
   long NS_IN_SEC = 1000 * 1000 * 1000;
   long NS_IN_MS = 1000 * 1000;

   void registerRequest(long responseTime, long txOverhead, Operation operation);

   void registerError(long responseTime, long txOverhead, Operation operation);

   void reset(long time);

   Statistics copy();

   void merge(Statistics otherStats);

   Map<String, Object> getResultsMap(int threads, String prefix);

   double getOperationsPerSecond();
}
