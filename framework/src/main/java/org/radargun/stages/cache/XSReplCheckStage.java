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
package org.radargun.stages.cache;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Debugable;

/**
 * Checks data loaded in XSReplLoadStage.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Checks data loaded in XSReplLoadStage.")
public class XSReplCheckStage extends CheckCacheDataStage {

   @Property(doc = "Postfix part of the value contents. Default is empty string.")
   private String valuePostFix = "";

   private transient BasicOperations.Cache[] backupCaches;
   private transient Debugable.Cache[] backupDebugable;
   private transient BackupCacheValueChecker[] backupCheckers;

   @Override
   public DistStageAck executeOnSlave() {
      int numBackups = slaveState.getGroupCount();
      backupCaches = new BasicOperations.Cache[numBackups];
      backupCheckers = new BackupCacheValueChecker[numBackups];
      if (debugable != null) {
         backupDebugable = new Debugable.Cache[numBackups];
      }
      String mainCacheName = cacheInformation.getDefaultCacheName();
      Iterator<String> iterator = cacheInformation.getCacheNames().iterator();
      for (int i = 0; i < numBackups; ++i) {
         String cacheName = iterator.next();
         if (cacheName.equals(mainCacheName)) {
            --i;
            continue;
         }
         backupCaches[i] = basicOperations.getCache(cacheName);
         backupCheckers[i] = new BackupCacheValueChecker(cacheName);
         if (debugable != null) {
            backupDebugable[i] = debugable.getCache(cacheName);
         }
      }
      return super.executeOnSlave();
   }

   @Override
   protected boolean checkKey(BasicOperations.Cache basicCache, Debugable.Cache debugableCache, int keyIndex, CheckResult result, ValueChecker checker) {
      boolean retval = super.checkKey(basicCache, debugableCache, keyIndex, result, new MainCacheValueChecker());
      for (int i = 0; i < backupCaches.length; ++i) {
         retval = retval && super.checkKey(backupCaches[i], backupDebugable[i], keyIndex, result, backupCheckers[i]);
      }
      return retval;
   }

   @Override
   protected int getExpectedNumEntries() {
      return getNumEntries() * cacheInformation.getCacheNames().size();
   }

   private class MainCacheValueChecker implements ValueChecker {
      @Override
      public boolean check(int keyIndex, Object value) {
         return value.equals("value" + keyIndex + valuePostFix + "@" + cacheInformation.getDefaultCacheName());
      }
   }

   private class BackupCacheValueChecker implements ValueChecker {
      private Pattern valuePattern = Pattern.compile("value(\\d*)([^@]*)@(.*)");
      private volatile String originCache = null;
      private String cacheName;

      private BackupCacheValueChecker(String cacheName) {
         this.cacheName = cacheName;
      }

      @Override
      public boolean check(int keyIndex, Object value) {
         Matcher m;
         if (!(value instanceof String) || !(m = valuePattern.matcher((String) value)).matches()) {
            return false;
         }
         try {
            Integer.parseInt(m.group(1));
         } catch (NumberFormatException e) {
            return false;
         }
         if (!m.group(2).equals(valuePostFix)) {
            return false;
         }
         if (originCache == null) {
            synchronized (this) {
               if (originCache == null) {
                  log.info("Cache " + cacheName + " has entries from " + m.group(3));
                  originCache = m.group(3);
               }
            }
         } else if (!originCache.equals(m.group(3))) {
            String message = "Cache " + cacheName + " has entries from " + m.group(3) + " but it also had entries from " + originCache + "!";
            log.error(message);
            return false;
         }
         return true;
      }
   }

}
