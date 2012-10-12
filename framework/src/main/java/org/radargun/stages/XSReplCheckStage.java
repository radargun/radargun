/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.features.XSReplicating;

public class XSReplCheckStage extends CheckDataStage {
   
   private String valuePostFix;
   
   @Override
   protected int checkRange(int from, int to) {
      if (!(slaveState.getCacheWrapper() instanceof XSReplicating)) {
         throw new IllegalStateException("This stage requires wrapper that supports cross-site replication");
      }
      XSReplicating wrapper = (XSReplicating) slaveState.getCacheWrapper();
      Pattern valuePattern = Pattern.compile("value(\\d*)@(.*)");      
      
      int found = 0, checked = 0;
      log.info("Checking contents of main cache " + wrapper.getMainCache());
      for (int i = from; i < to; ++i, ++checked) {
         if (checked % LOG_CHECKS_COUNT == 0) {
            log.debug("Checked " + checked + " entries, so far " + found + " found");
         }
         try {
            Object value = wrapper.get(null,  "key" + i);
            if (!isDeleted()) {
               if (value != null && value.equals("value" + i + valuePostFix + "@" + wrapper.getMainCache())) {
                  found++;
               } else if (value != null) {
                  unexpected(i, value);
               }
            } else {
               if (value != null) {
                  found++;
                  shouldBeDeleted(i);
               }
            }
         } catch (Exception e) {
            if (log.isTraceEnabled()) {
               log.trace("Error retrieving value for key" + i + "\n" + e);
            }
         }
      }
      
      for (String cacheName : wrapper.getBackupCaches()) {
         log.info("Checking contents of backup cache " + cacheName);
         String originCache = null;
         for (int i = from; i < to; ++i, ++checked) {
            if (checked % LOG_CHECKS_COUNT == 0) {
               log.debug("Checked " + checked + " entries, so far " + found + " found");
            }
            try {
               Object value = wrapper.get(cacheName, "key" + i);
               Matcher m;
               if (!isDeleted()) {
                  if (value != null && value instanceof String && (m = valuePattern.matcher((String) value)).matches()) {
                     try {
                        Integer.parseInt(m.group(1));
                     } catch (NumberFormatException e) {
                        unexpected(i, value);
                     }
                     if (originCache == null) {
                        log.info("Cache " + cacheName + " has entries from " + m.group(2));
                        originCache = m.group(2);
                     } else if (!originCache.equals(m.group(2))) {
                        String message = "Cache " + cacheName + " has entries from " + m.group(2) + " but it also had entries from " + originCache + "!"; 
                        log.error(message);
                        throw new IllegalStateException(message);
                     }
                     found++;
                  } else {
                     unexpected(i, value);
                  }
               } else {
                  if (value != null) {
                     found++;
                     shouldBeDeleted(i);
                  }
               }
            } catch (Exception e) {
               if (log.isTraceEnabled()) {
                  log.trace("Error retrieving value for key" + i + "\n" + e);
               }
            }
         }
      }
      return found;
   }

   private void shouldBeDeleted(int i) {
      if (log.isTraceEnabled()) {
         log.trace("Key" + i + " should be deleted!");
      }
   }

   @Override
   protected int getExpectedNumEntries() {
      XSReplicating wrapper = (XSReplicating) slaveState.getCacheWrapper();
      return getNumEntries() * (wrapper.getBackupCaches().size() + 1);
   }
   
   private void unexpected(Object key, Object value) {
      if (log.isTraceEnabled()) {
         log.trace("Key" + key + " has unexpected value " + value);
      }
   }
   
   public void setValuePostFix(String valuePostFix) {
      this.valuePostFix = valuePostFix;
   }

   
   @Override
   public String toString() {
      return "XSReplCheckStage(" + super.attributesToString();
   }
}
