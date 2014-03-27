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

import java.util.Arrays;

import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;

/**
 * This class remembers all requests as these came, storing them in memory. Use only for statistics warmup
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class AllRecordingOperationStats implements OperationStats {
   private static final int DEFAULT_BUCKETS = 32;
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
   public <T> T getRepresentation(Class<T> clazz) {
      if (clazz == Histogram.class) {
         long[] range = new long[DEFAULT_BUCKETS - 1];
         int mySize = full ? responseTimes.length : pos;
         if (responseTimes.length != 0) {
            Arrays.sort(responseTimes, 0, mySize);
            for (int j = 0; j < range.length; ++j) {
               range[j] = responseTimes[(j + 1) * mySize / DEFAULT_BUCKETS];
            }
         }
         return (T) new Histogram(range, new long[DEFAULT_BUCKETS], responseTimes[0], responseTimes[Math.max(0, mySize - 1)]);
      } else if (clazz == DefaultOutcome.class) {
         long max = 0;
         long responseTimeSum = 0;
         long requests = full ? responseTimes.length : pos;
         for (int i = 0; i < requests; ++i) {
            responseTimeSum += responseTimes[i];
            max = Math.max(max, responseTimes[i]);
         }
         return (T) new DefaultOutcome(requests, 0, (double) responseTimeSum / requests, max);
      } else {
         return null;
      }
   }

   @Override
   public boolean isEmpty() {
      return pos == 0 && !full;
   }
}
