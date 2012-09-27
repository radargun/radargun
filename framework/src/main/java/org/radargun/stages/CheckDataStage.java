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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.stages.helpers.RangeHelper;
import org.radargun.state.MasterState;
import org.radargun.stressors.BackgroundStats;

public class CheckDataStage extends AbstractDistStage {

   protected static final int LOG_CHECKS_COUNT = 100;
   
   private int numEntries;
   private int entrySize;
   private String extraEntries;
   private int numOwners = -1;
   private int checkThreads = 1;
   private boolean ignoreSum = false;
    
   @Override
   public DistStageAck executeOnSlave() {      
      DefaultDistStageAck ack = newDefaultStageAck();
      int found = 0;
      try {
         if (slaveState.getCacheWrapper() == null) {         
            // this slave is dead and does not participate on check
            return ack;
         }
         if (checkThreads <= 1) {
            found = checkRange(0, numEntries);
         } else {
            ExecutorService executor = Executors.newFixedThreadPool(checkThreads);
            List<Callable<Integer>> tasks = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < checkThreads; ++i) {
               RangeHelper.Range range = RangeHelper.divideRange(numEntries, checkThreads, i);
               tasks.add(new CheckRangeTask(range.getStart(), range.getEnd()));
            }
            for (Future<Integer> future : executor.invokeAll(tasks)) {
               int value = future.get();
               found += value;
            }
         }
      } catch (Exception e) {
         log.error(e);
         ack.setError(true);
         ack.setRemoteException(e);
         ack.setErrorMessage("Failed to check entries");
         return ack;
      }
            
      if (found != getExpectedNumEntries()) {
         ack.setError(true);
         ack.setErrorMessage("Found " + found + " entries while " + numEntries + " should be loaded.");         
         return ack;
      }
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      ack.setPayload(wrapper.getLocalSize());
      return ack;
   }
   
   private class CheckRangeTask implements Callable<Integer> {
      private int from, to;
      
      public CheckRangeTask(int from, int to) {
         this.from = from;
         this.to = to;
      }
      
      @Override
      public Integer call() throws Exception {
         try {
            return checkRange(from, to);
         } catch (Exception e) {
            log.error(e);
            return -1;
         }
      }
   }
   
   protected int getExpectedNumEntries() {
      return numEntries;
   }
   
   protected int checkRange(int from, int to) {
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      int found = 0, checked = 0;
      BackgroundStats bgStats = (BackgroundStats) slaveState.get(BackgroundStats.NAME);
      String bucketId = BackgroundStats.NAME;
      if (bgStats != null) bucketId = bgStats.getBucketId();
      for (int i = from; i < to; ++i, ++checked) {
         if (checked % LOG_CHECKS_COUNT == 0) {
            log.debug("Checked " + checked + " entries, so far " + found + " found");
         }
         try {
            Object value = wrapper.get(bucketId, "key" + i);
            if (value != null && value instanceof byte[] && (entrySize <= 0 || ((byte[]) value).length == entrySize)) {
               found++;
            } else {
               if (log.isTraceEnabled()) {
                  log.trace("Key" + i + " has unexpected value " + value);
               }
            }
         } catch (Exception e) {
            if (log.isTraceEnabled()) {
               log.trace("Error retrieving value for key" + i + "\n" + e);
            }
         }
      }
      return found;
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
            if (numOwners < 0) {
               expectedSize = -numOwners * getActiveSlaveCount() * (numEntries + extraEntries);
            } else {
               expectedSize = numOwners * (numEntries + extraEntries);
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
   
   public void setNumEntries(int entries) {
      this.numEntries = entries;
   }
   
   public void setEntrySize(int size) {
      this.entrySize = size;
   }
   
   /**
    * The argument should be list like 1,2,3 which represents a polynome in number of slaves
    * - the first member is multiplied by 1, second by number of slaves, third by square of
    * number of slaves etc. 1,2,3 with 4 slaves would therefore result in 1 + 8 + 48 = 57
    * extra entries. 
    * 
    * @param extra
    */
   public void setExtraEntries(String extra) {
      extraEntries = extra;
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
   
   /**
    * Number of owners of each entry. If this number is negative it is used as its absolute
    * value multiplied by the number of slaves (use -1 for full replication).
    * 
    * @param numOwners
    */
   public void setNumOwners(int numOwners) {
      this.numOwners = numOwners;
   }
   
   public void setCheckThreads(int threads) {
      this.checkThreads = threads;
   }
   
   public void setIgnoreSum(boolean ignore) {
      ignoreSum = ignore;
   }
   
   public String attributesToString() {
      return "numEntries=" + numEntries + ", entrySize=" + entrySize + ", extraEntries=" + extraEntries + ", numOwners=" + numOwners + ", checkThreads=" + checkThreads + ", " + super.toString();
   }
   
   @Override
   public String toString() {
      return "CheckDataStage(" + attributesToString();
   }
   
}
