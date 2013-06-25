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

package org.radargun.stages.helpers;

import java.io.Serializable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class StartStopTime implements Serializable {
   private long startTime;
   private long stopTime;

   public StartStopTime(long startTime, long stopTime) {
      this.startTime = startTime;
      this.stopTime = stopTime;
   }

   public static StartStopTime withStartTime(long startTime, Object previous) {
      return new StartStopTime(startTime, previous instanceof StartStopTime ? ((StartStopTime) previous).stopTime : -1);
   }

   public static StartStopTime withStopTime(long stopTime, Object previous) {
      return new StartStopTime(previous instanceof StartStopTime ? ((StartStopTime) previous).startTime : -1, stopTime);
   }

   public long getStartTime() {
      return startTime;
   }

   public long getStopTime() {
      return stopTime;
   }
}
