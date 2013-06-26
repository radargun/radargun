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

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.XSReplicating;

/**
 * Checks data loaded in XSReplLoadStage.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Checks data loaded in XSReplLoadStage.")
public class XSReplCheckStage extends CheckDataStage {

   @Property(doc = "Postfix part of the value contents. Default is empty string.")
   private String valuePostFix = "";

   private transient XSReplicating xswrapper;
   private transient String[] backupCaches;
   private transient BackupCacheValueChecker[] backupCheckers;

   @Override
   public DistStageAck executeOnSlave() {
      if (!(slaveState.getCacheWrapper() instanceof XSReplicating)) {
         String message = "This stage requires wrapper that supports cross-site replication";
         log.error(message);
         DefaultDistStageAck ack = newDefaultStageAck();
         ack.setErrorMessage(message);
         ack.setError(true);
         return ack;
      }
      xswrapper = (XSReplicating) slaveState.getCacheWrapper();
      int numBackups = xswrapper.getBackupCaches().size();
      backupCaches = new String[numBackups];
      backupCheckers = new BackupCacheValueChecker[numBackups];
      Iterator<String> iterator = xswrapper.getBackupCaches().iterator();
      for (int i = 0; i < numBackups; ++i) {
         String cacheName = iterator.next();
         backupCaches[i] = cacheName;
         backupCheckers[i] = new BackupCacheValueChecker(cacheName);
      }
      return super.executeOnSlave();    // TODO: Customise this generated block
   }

   @Override
   protected boolean checkKey(CacheWrapper wrapper, String bucketId, int keyIndex, CheckResult result, ValueChecker checker) {
      boolean retval = super.checkKey(wrapper, null, keyIndex, result, new MainCacheValueChecker());
      for (int i = 0; i < backupCaches.length; ++i) {
         retval = retval && super.checkKey(wrapper, backupCaches[i], keyIndex, result, backupCheckers[i]);
      }
      return retval;
   }

   @Override
   protected int getExpectedNumEntries() {
      XSReplicating wrapper = (XSReplicating) slaveState.getCacheWrapper();
      return getNumEntries() * (wrapper.getBackupCaches().size() + 1);
   }

   private class MainCacheValueChecker implements ValueChecker {
      @Override
      public boolean check(int keyIndex, Object value) {
         return value.equals("value" + keyIndex + valuePostFix + "@" + xswrapper.getMainCache());
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
