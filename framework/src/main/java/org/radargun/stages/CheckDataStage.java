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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.Debugable;
import org.radargun.features.PersistentStorageCapable;
import org.radargun.stages.helpers.Range;
import org.radargun.state.MasterState;
import org.radargun.stressors.BackgroundOpsManager;
import org.radargun.stressors.KeyGenerator;
import org.radargun.stressors.StringKeyGenerator;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stage for checking presence or absence of data uploaded by BackgroundStressors")
public class CheckDataStage extends AbstractDistStage {

   @Property(optional = false, doc = "Number of entries with key in form specified by the last used key generator, in the cache.")
   private int numEntries;

   @Property(doc = "Index of key of first entry. This number will be multiplied by slaveIndex. Default is 0.")
   private int firstEntryOffset = 0;

   @Property(doc = "Number of entries that will be checked in each step. Default is 1.")
   private int checkEntryCount = 1;

   @Property(doc = "Number of entries stepped in each step. Default is 1.")
   private int stepEntryCount = 1;

   @Property(optional = false, doc = "Number of bytes carried in single entry.")
   private int entrySize;

   @Property(doc = "Entries that do not have the expected form but occur in the cluster. This string specifies " +
         "a polynomial in number of slaves: 1,2,3 with 4 slaves would result in 1 + 2*4 + 3*4*4 = 57 extra entries." +
         "Defaults to 0.")
   private String extraEntries;

   @Property(doc = "Number of owners of one key (primary + backups). If negative the number is multiplied by cluster " +
         "size, therefore, -1 means full replication. Default is -1.")
   private int numOwners = -1;

   @Property(doc = "Number of thread per node which check data validity. Default is 1.")
   private int checkThreads = 1;

   @Property(doc = "Usually the test checks that sum of local nodes = numOwners * numEntries + extraEntries." +
         "This option disables such behaviour. Default is false.")
   private boolean ignoreSum = false;

   @Property(doc = "Hint how many slaves are currently alive - if set to > 0 then the query for amount of entries in" +
         "this cache is postponed until the cache appears to be fully replicated. By default this is disabled.")
   private int liveSlavesHint = -1;

   @Property(doc = "If set to true, we are checking that the data are NOT in the cluster anymore. Default is false.")
   private boolean deleted = false;

   @Property(doc = "Number of queries after which a DEBUG log message is printed. Default is 10000.")
   private int logChecksCount = 10000;

   @Property(doc = "If the GET request results in null response, call wrapper-specific functions to show debug info. " +
         "Default is false.")
   private boolean debugNull = false;

   @Property(doc = "If entry is null, fail immediatelly. Default is false.")
   private boolean failOnNull = false;

   @Property(doc = "If the cache wrapper supports persistent storage and this is set to true, the check " +
         "will be executed only against in-memory data. Default is false.")
   private boolean memoryOnly = false;

   private transient KeyGenerator keyGenerator;

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaves != null && !slaves.contains(getSlaveIndex())) {
         return ack;
      }
      keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
      if (keyGenerator == null) {
         keyGenerator = new StringKeyGenerator();
      }
      CheckResult result = new CheckResult();
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      try {
         if (wrapper == null) {
            // this slave is dead and does not participate on check
            return ack;
         }
         if (checkThreads <= 1) {
            String bucketId = getBucketId();
            ValueChecker checker = new ByteArrayValueChecker();
            int entriesToCheck = numEntries;
            for (int i = firstEntryOffset * slaveIndex; entriesToCheck > 0; i += stepEntryCount) {
               int checkAmount = Math.min(checkEntryCount, entriesToCheck);
               for (int j = 0; j < checkAmount; ++j) {
                  if (!checkKey(wrapper, bucketId, i + j, result, checker)) {
                     entriesToCheck = 0;
                     break;
                  }
               }
               entriesToCheck -= checkAmount;
            }
         } else {
            ExecutorService executor = Executors.newFixedThreadPool(checkThreads);
            List<Callable<CheckResult>> tasks = new ArrayList<Callable<CheckResult>>();
            for (int i = 0; i < checkThreads; ++i) {
               Range range = Range.divideRange(numEntries, checkThreads, i);
               tasks.add(new CheckRangeTask(range.getStart(), range.getEnd()));
            }
            for (Future<CheckResult> future : executor.invokeAll(tasks)) {
               CheckResult value = future.get();
               result.merge(value);
            }
         }
      } catch (Exception e) {         
         log.error(e);
         ack.setError(true);
         ack.setRemoteException(e);
         ack.setErrorMessage("Failed to check entries");
         return ack;
      }

      if (wrapper != null && wrapper.isRunning()) {
         if (!isDeleted()) {
            if (result.found != getExpectedNumEntries()) {
               ack.setError(true);
               ack.setErrorMessage("Found " + result.found + " entries while " + getExpectedNumEntries() + " should be loaded.");
               ack.setPayload(result);
               return ack;
            }
         } else {
            if (result.found > 0) {
               ack.setError(true);
               ack.setErrorMessage("Found " + result.found + " entries while these should be deleted.");
               ack.setPayload(result);
               return ack;
            }
         }
         if (liveSlavesHint > 0) {
            // try to wait until data are properly replicated
            int myExpectedSize;
            int extraEntries = getExtraEntries();
            int commonEntries = isDeleted() ? 0 : numEntries;
            if (numOwners < 0) {
               myExpectedSize = -numOwners * getActiveSlaveCount() * (commonEntries + extraEntries) / liveSlavesHint;
            } else {
               myExpectedSize = numOwners * (commonEntries + extraEntries) / liveSlavesHint;
            }
            for (int attempt = 0; attempt < 5; ++attempt) {
               int local = wrapper.getLocalSize();
               double ratio = (double) local / (double) myExpectedSize;
               if (ratio < 0.9 || ratio > 1.1) {
                  log.warn("Local size (" + wrapper.getLocalSize() + ") differs substantially from expected size (" + myExpectedSize + "), waiting 30s to let it replicate");
                  try {
                     Thread.sleep(30000);
                  } catch (InterruptedException e) {
                     break;
                  }
               } else break;
            }
         }
         ack.setPayload(wrapper.getLocalSize());
      }
      return ack;
   }

   private String getBucketId() {
      // TODO: different stages may use different buckets
      BackgroundOpsManager bgStats = (BackgroundOpsManager) slaveState.get(BackgroundOpsManager.NAME);
      return bgStats == null ? BackgroundOpsManager.NAME : bgStats.getBucketId();
   }

   private class CheckRangeTask implements Callable<CheckResult> {
      private int from, to;
      
      public CheckRangeTask(int from, int to) {
         this.from = from;
         this.to = to;
      }
      
      @Override
      public CheckResult call() throws Exception {
         try {
            CheckResult result = new CheckResult();
            ValueChecker checker = new ByteArrayValueChecker();
            CacheWrapper wrapper = slaveState.getCacheWrapper();
            String bucketId = getBucketId();
            int entriesToCheck = to - from;
            for (int i = from * (stepEntryCount / checkEntryCount) + firstEntryOffset * slaveIndex; entriesToCheck > 0; i += stepEntryCount) {
               int checkAmount = Math.min(checkEntryCount, entriesToCheck);
               for (int j = 0; j < checkAmount; ++j) {
                  if (!checkKey(wrapper, bucketId, i + j, result, checker)) {
                     entriesToCheck = 0;
                     break;
                  }
               }
               entriesToCheck -= checkAmount;
            }
            return result;
         } catch (Exception e) {
            log.error(e);
            return null;
         }
      }
   }
   
   protected int getExpectedNumEntries() {
      return numEntries;
   }

   protected boolean checkKey(CacheWrapper wrapper, String bucketId, int keyIndex, CheckResult result, ValueChecker checker) {
      if (result.checked % logChecksCount == 0) {
         log.debug("Checked " + result.checked + " entries, so far " + result);
      }
      Object key = keyGenerator.generateKey(keyIndex);
      try {
         Object value;
         if (memoryOnly && wrapper instanceof PersistentStorageCapable) {
            value = ((PersistentStorageCapable) wrapper).getMemoryOnly(bucketId, key);
         } else {
            value = wrapper.get(bucketId, key);
         }
         if (!isDeleted()) {
            if (value != null && checker.check(keyIndex, value)) {
               result.found++;
            } else {
               if (value == null) {
                  result.nullValues++;
                  if (debugNull && wrapper instanceof Debugable) {
                     ((Debugable) wrapper).debugInfo(bucketId);
                     ((Debugable) wrapper).debugKey(bucketId, key);
                  }
                  if (failOnNull) {
                     result.checked++;
                     return false;
                  }
               } else {
                  result.invalidValues++;
               }
               unexpected(key, value);
            }
         } else {
            if (value != null) {
               result.found++;
               shouldBeDeleted(key, value);
            } else {
               result.nullValues++;
            }
         }
         result.checked++;
      } catch (Exception e) {
         result.exceptions++;
         if (log.isTraceEnabled()) {
            log.trace("Error retrieving value for key " + key + "\n" + e);
         }
      }
      return true;
   }

   protected void shouldBeDeleted(Object key, Object value) {
      if (log.isTraceEnabled()) {
         log.trace("Key " + key + " still has value " + value);
      }
   }

   protected void unexpected(Object key, Object value) {
      if (log.isTraceEnabled()) {
         log.trace("Key " + key + " has unexpected value " + value);
      }
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      boolean success = super.processAckOnMaster(acks, masterState);
      if (success) {
         int sumSize = 0;
         for (DistStageAck ack : acks) {
            DefaultDistStageAck dack = (DefaultDistStageAck) ack;
            if (dack.getPayload() != null) {
               Integer localSize = (Integer) dack.getPayload();
               log.debug("Slave " + dack.getSlaveIndex() + " has local size " + localSize);
               sumSize += localSize;
            }
         }
         if (ignoreSum) {
            log.info("The sum size is " + sumSize);
         } else {
            int expectedSize;
            int extraEntries = getExtraEntries();
            int commonEntries = isDeleted() ? 0 : numEntries;            
            if (numOwners < 0) {
               expectedSize = -numOwners * getActiveSlaveCount() * (commonEntries + extraEntries);
            } else {
               expectedSize = numOwners * (commonEntries + extraEntries);
            }
            if (expectedSize != sumSize) {
               log.error("The cache should contain " + expectedSize + " entries (including backups) but contains " + sumSize + " entries.");
               return false;
            }
         }
      }
      return success;
   }

   public int getNumEntries() {
      return this.numEntries;
   }

   private int getExtraEntries() {
      if (extraEntries == null) return 0;
      
      int sum = 0;
      int multiplicator = 1;      
      try {
         for (String entries : extraEntries.split(",")) {
            int count = Integer.parseInt(entries);
            sum += count * multiplicator;
            multiplicator *= getActiveSlaveCount();
         }
      } catch (NumberFormatException e) {
         log.error("Cannot parse " + extraEntries);
      }
      return sum;
   }

   public boolean isDeleted() {
      return deleted;
   }

   public int getLogChecksCount() {
      return logChecksCount;
   }

   protected static class CheckResult implements Serializable {
      public long checked;
      public long found;
      public long nullValues;
      public long invalidValues;
      public long exceptions;

      public void merge(CheckResult value) {
         if (value == null) return;
         checked += value.checked;
         found += value.found;
         nullValues += value.nullValues;
         invalidValues += value.invalidValues;
         exceptions += value.invalidValues;
      }

      @Override
      public String toString() {
         return String.format("[checked=%d, found=%d, nullValues=%d, invalidValues=%d, exceptions=%d]",
                              checked, found, nullValues, invalidValues, exceptions);
      }
   }

   protected interface ValueChecker {
      boolean check(int keyIndex, Object value);
   }

   protected class ByteArrayValueChecker implements ValueChecker {
      @Override
      public boolean check(int keyIndex, Object value) {
         return value instanceof byte[] && (entrySize <= 0 || ((byte[]) value).length == entrySize);
      }
   }
}
