package org.radargun.stages.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.BucketPolicy;
import org.radargun.stages.helpers.Range;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.Debugable;
import org.radargun.traits.InMemoryBasicOperations;
import org.radargun.traits.InjectTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stage for checking presence or absence of data entered in other stages.")
public class CheckCacheDataStage extends AbstractDistStage {

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

   @Property(doc = "Number of thread per node which check data validity. Default is 1.")
   private int checkThreads = 1;

   @Property(doc = "Usually the test checks that sum of local nodes = numOwners * numEntries + extraEntries." +
         "This option disables such behaviour. Default is false.")
   private boolean ignoreSum = false;

   @Property(doc = "If true, the entries are not retrieved, this stage only checks that the sum of entries from local nodes is correct. Default is false.")
   private boolean sizeOnly = false;

   @Property(doc = "Hint how many slaves are currently alive - if set to > 0 then the query for amount of entries in " +
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

   // TODO: better names, even when these are kind of hacks
   @Property(doc = "Check whether the sum of subparts sizes is the same as local size. Default is false.")
   private boolean checkSubpartsSumLocal = false;

   @Property(doc = "Check whether the same subparts from each cache have the same size. Default is false.")
   private boolean checkSubpartsEqual = false;

   @Property(doc = "Check that number of non-zero subparts is equal to number of replicas. Default is false.")
   private boolean checkSubpartsAreReplicas = false;

   private transient KeyGenerator keyGenerator;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected BasicOperations basicOperations;
   @InjectTrait
   protected InMemoryBasicOperations inMemoryBasicOperations;
   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected CacheInformation cacheInformation;
   @InjectTrait
   protected Debugable debugable;

   protected BasicOperations.Cache basicCache;
   protected Debugable.Cache debugableCache;

   @Override
   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         return successfulResponse();
      }
      if (!isServiceRunnning()) {
         // this slave is dead and does not participate on check
         return successfulResponse();
      }
      if (!sizeOnly) {
         keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
         if (keyGenerator == null) {
            keyGenerator = new StringKeyGenerator();
         }
         CheckResult result = new CheckResult();
         if (memoryOnly && inMemoryBasicOperations != null) {
            basicCache = inMemoryBasicOperations.getMemoryOnlyCache(getCacheName());
         } else {
            basicCache = basicOperations.getCache(getCacheName());
         }
         if (debugable != null){
            debugableCache = debugable.getCache(getCacheName());
         }

         try {
            if (checkThreads <= 1) {
               ValueChecker checker = new GeneratedValueChecker((ValueGenerator) slaveState.get(ValueGenerator.VALUE_GENERATOR));
               int entriesToCheck = numEntries;
               for (int i = firstEntryOffset * slaveState.getSlaveIndex(); entriesToCheck > 0; i += stepEntryCount) {
                  int checkAmount = Math.min(checkEntryCount, entriesToCheck);
                  for (int j = 0; j < checkAmount; ++j) {
                     if (!checkKey(basicCache, debugableCache, i + j, result, checker)) {
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
            return errorResponse("Failed to check entries", e);
         }

         if (!isDeleted()) {
            if (result.found != getExpectedNumEntries()) {
               return new InfoAck(slaveState, result).error("Found " + result.found + " entries while " + getExpectedNumEntries() + " should be loaded.");
            }
         } else {
            if (result.found > 0) {
               return new InfoAck(slaveState, result).error("Found " + result.found + " entries while these should be deleted.");
            }
         }
      }
      CacheInformation.Cache info = cacheInformation.getCache(getCacheName());
      if (liveSlavesHint > 0) {
         // try to wait until data are properly replicated
         int myExpectedSize;
         int extraEntries = getExtraEntries();
         int commonEntries = isDeleted() ? 0 : numEntries;
         int numOwners = info.getNumReplicas();
         if (numOwners < 0) {
            myExpectedSize = -numOwners * slaveState.getClusterSize() * (commonEntries + extraEntries) / liveSlavesHint;
         } else {
            myExpectedSize = numOwners * (commonEntries + extraEntries) / liveSlavesHint;
         }
         for (int attempt = 0; attempt < 5; ++attempt) {
            int local = info.getLocalSize();
            double ratio = (double) local / (double) myExpectedSize;
            if (ratio < 0.9 || ratio > 1.1) {
               log.warn("Local size (" + local + ") differs substantially from expected size (" + myExpectedSize + "), waiting 30s to let it replicate");
               try {
                  Thread.sleep(30000);
               } catch (InterruptedException e) {
                  break;
               }
            } else break;
         }
      }
      return new InfoAck(slaveState, info.getLocalSize(), info.getStructuredSize(), info.getNumReplicas());
   }

   private String getCacheName() {
      return (String) slaveState.get(BucketPolicy.LAST_BUCKET);
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
            ValueChecker checker = new GeneratedValueChecker((ValueGenerator) slaveState.get(ValueGenerator.VALUE_GENERATOR));
            String bucketId = getCacheName();
            int entriesToCheck = to - from;
            for (int i = from * (stepEntryCount / checkEntryCount) + firstEntryOffset * slaveState.getSlaveIndex(); entriesToCheck > 0; i += stepEntryCount) {
               int checkAmount = Math.min(checkEntryCount, entriesToCheck);
               for (int j = 0; j < checkAmount; ++j) {
                  if (!checkKey(basicCache, debugableCache, i + j, result, checker)) {
                     entriesToCheck = 0;
                     break;
                  }
               }
               entriesToCheck -= checkAmount;
            }
            return result;
         } catch (Exception e) {
            log.error("Failed to check entries", e);
            return null;
         }
      }
   }
   
   protected int getExpectedNumEntries() {
      return numEntries;
   }

   protected boolean checkKey(BasicOperations.Cache basicCache, Debugable.Cache debugableCache, int keyIndex, CheckResult result, ValueChecker checker) {
      Object key = keyGenerator.generateKey(keyIndex);
      try {
         Object value = basicCache.get(key);
         if (!isDeleted()) {
            if (value != null && checker.check(keyIndex, value)) {
               result.found++;
            } else {
               if (value == null) {
                  result.nullValues++;
                  if (debugNull && debugableCache != null) {
                     debugableCache.debugInfo();
                     debugableCache.debugKey(key);
                  }
                  if (failOnNull) {
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
      } catch (Exception e) {
         if (result.exceptions == 0) {
            log.error("Error retrieving value for key " + key, e);
         } else if (log.isTraceEnabled()) {
            log.trace("Error retrieving value for key " + key, e);
         }
         result.exceptions++;
      } finally {
         result.checked++;
         if (result.checked % logChecksCount == 0) {
            log.debug("Checked so far: " + result);
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
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean success = super.processAckOnMaster(acks);
      if (!success) {
         return false;
      }
      int sumSize = 0;
      Integer numReplicas = null;
      Map<Object, Map<Integer, Integer>> subparts = new HashMap<Object, Map<Integer, Integer>>();
      for (DistStageAck ack : acks) {
         if (!(ack instanceof InfoAck)) {
            continue;
         }
         InfoAck info = (InfoAck) ack;
         log.debug("Slave " + ack.getSlaveIndex() + " has local size " + info.localSize);
         sumSize += info.localSize;
         if (numReplicas == null) numReplicas = info.numReplicas;
         else if (numReplicas != info.numReplicas) {
            log.error("Slave " + ack.getSlaveIndex() + " reports " + info.numReplicas + " replicas but other slave reported " + numReplicas);
            success = false;
         }
         int sumSubpartSize = 0;
         for (Map.Entry<?, Integer> subpart : info.structuredSize.entrySet()) {
            log.trace("Subpart " + subpart.getKey() + " = " + subpart.getValue());
            if (subpart.getValue() == 0) continue;

            sumSubpartSize += subpart.getValue();
            Map<Integer, Integer> otherSubparts = subparts.get(subpart.getKey());
            if (otherSubparts == null) {
               subparts.put(subpart.getKey(), new HashMap<Integer, Integer>(Collections.singletonMap(info.getSlaveIndex(), subpart.getValue())));
            } else if (checkSubpartsEqual) {
               for (Map.Entry<Integer, Integer> os : otherSubparts.entrySet()) {
                  if ((int) subpart.getValue() != (int) os.getValue()) {
                     log.errorf("Slave %d reports %s = %d but slave %d reported size %d",
                           info.getSlaveIndex(), subpart.getKey(), subpart.getValue(), os.getKey(), os.getValue());
                     success = false;
                  }
               }
               otherSubparts.put(info.getSlaveIndex(), subpart.getValue());
            }
         }
         if (checkSubpartsSumLocal && sumSubpartSize != info.localSize) {
            log.errorf("On slave %d sum of subparts sizes (%d) is not the same as local size (%d)",
                  info.getSlaveIndex(), sumSubpartSize, info.localSize);
            success = false;
         }
      }
      if (checkSubpartsAreReplicas) {
         for (Map.Entry<Object, Map<Integer, Integer>> subpart : subparts.entrySet()) {
            if (subpart.getValue().size() != numReplicas) {
               log.errorf("Subpart %s was found in %s, should have %d replicas.", subpart.getKey(), subpart.getValue().keySet(), numReplicas);
               success = false;
            }
         }
      }
      if (ignoreSum) {
         log.info("The sum size is " + sumSize);
      } else {
         int expectedSize;
         int extraEntries = getExtraEntries();
         int commonEntries = isDeleted() ? 0 : numEntries;
         if (numReplicas < 0) {
            expectedSize = -numReplicas * masterState.getClusterSize() * (commonEntries + extraEntries);
         } else {
            expectedSize = numReplicas * (commonEntries + extraEntries);
         }
         if (expectedSize != sumSize) {
            log.error("The cache should contain " + expectedSize + " entries (including backups) but contains " + sumSize + " entries.");
            success = false;
         } else {
            log.trace("The sum size is " + sumSize + " entries as expected");
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
            multiplicator *= slaveState.getClusterSize();
         }
      } catch (NumberFormatException e) {
         log.error("Cannot parse " + extraEntries);
      }
      return sum;
   }

   public boolean isDeleted() {
      return deleted;
   }

   protected static class InfoAck extends DistStageAck {
      final long localSize;
      final Map<?, Integer> structuredSize;
      final int numReplicas;
      final CheckResult checkResult;

      public InfoAck(SlaveState slaveState, long localSize, Map<?, Integer> structuredSize, int numReplicas) {
         super(slaveState);
         this.localSize = localSize;
         this.structuredSize = structuredSize;
         this.numReplicas = numReplicas;
         checkResult = null;
      }

      public InfoAck(SlaveState slaveState, CheckResult checkResult) {
         super(slaveState);
         this.checkResult = checkResult;
         localSize = -1;
         structuredSize = null;
         numReplicas = -1;
      }

      @Override
      public String toString() {
         return "InfoAck{" +
               "localSize=" + localSize +
               ", numReplicas=" + numReplicas +
               ", checkResult=" + checkResult +
               "} " + super.toString();
      }
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

   protected class GeneratedValueChecker implements ValueChecker {
      private final ValueGenerator valueGenerator;

      public GeneratedValueChecker(ValueGenerator valueGenerator) {
         this.valueGenerator = valueGenerator == null ? new ByteArrayValueGenerator() : valueGenerator;
      }

      @Override
      public boolean check(int keyIndex, Object value) {
         return valueGenerator.checkValue(value, entrySize);
      }
   }
}
