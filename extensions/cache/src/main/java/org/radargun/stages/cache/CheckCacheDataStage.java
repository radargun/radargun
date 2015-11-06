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
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;
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
   public long numEntries;

   @Property(doc = "Index of key of the first entry. This number will be multiplied by slaveIndex. Default is 0. Has precedence over 'first-entry-offset'.")
   public long firstEntryOffsetSlaveIndex = 0;

   @Property(doc = "Index of key of the first entry.")
   public long firstEntryOffset = 0;

   @Property(doc = "Number of entries that will be checked in each step. Default is 1.")
   public long checkEntryCount = 1;

   @Property(doc = "Number of entries stepped in each step. Default is 1.")
   public long stepEntryCount = 1;

   @Property(optional = false, doc = "Number of bytes carried in single entry.")
   public int entrySize;

   @Property(doc = "Entries that do not have the expected form but occur in the cluster. This string specifies " +
      "a polynomial in number of slaves: 1,2,3 with 4 slaves would result in 1 + 2*4 + 3*4*4 = 57 extra entries." +
      "Defaults to 0.")
   public String extraEntries;

   @Property(doc = "Number of thread per node which check data validity. Default is 1.")
   public int checkThreads = 1;

   @Property(doc = "Usually the test checks that sum of local nodes = numOwners * numEntries + extraEntries." +
      "This option disables such behaviour. Default is false.")
   public boolean ignoreSum = false;

   @Property(doc = "If true, the entries are not retrieved, this stage only checks that the sum of entries from local nodes is correct. Default is false.")
   public boolean sizeOnly = false;

   @Property(doc = "Hint how many slaves are currently alive - if set to > 0 then the query for number of entries in " +
      "this cache is postponed until the cache appears to be fully replicated. By default this is disabled.")
   public int liveSlavesHint = -1;

   @Property(doc = "If set to true, we are checking that the data are NOT in the cluster anymore. Default is false.")
   public boolean deleted = false;

   @Property(doc = "Number of queries after which a DEBUG log message is printed. Default is 10000.")
   public int logChecksCount = 10000;

   @Property(doc = "If the GET request results in null response, call wrapper-specific functions to show debug info. " +
      "Default is false.")
   public boolean debugNull = false;

   @Property(doc = "If entry is null, fail immediately. Default is false.")
   public boolean failOnNull = false;

   @Property(doc = "If the cache wrapper supports persistent storage and this is set to true, the check " +
      "will be executed only against in-memory data. Default is false.")
   public boolean memoryOnly = false;

   @Property(doc = "Generator of keys (transforms key ID into key object). By default the generator is retrieved from slave state.",
      complexConverter = KeyGenerator.ComplexConverter.class)
   public KeyGenerator keyGenerator = null;

   @Property(doc = "Generator of values. By default the generator is retrieved from slave state.",
      complexConverter = ValueGenerator.ComplexConverter.class)
   public ValueGenerator valueGenerator = null;

   // TODO: better names, even when these are kind of hacks
   @Property(doc = "Check whether the sum of subparts sizes is the same as local size. Default is false.")
   public boolean checkSubpartsSumLocal = false;

   @Property(doc = "Check whether the same subparts from each cache have the same size. Default is false.")
   public boolean checkSubpartsEqual = false;

   @Property(doc = "Check that number of non-zero subparts is equal to number of replicas. Default is false.")
   public boolean checkSubpartsAreReplicas = false;

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
      if (!isServiceRunning()) {
         // this slave is dead and does not participate on check
         return successfulResponse();
      }
      if (!sizeOnly) {
         if (keyGenerator == null) {
            keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
            if (keyGenerator == null) {
               throw new IllegalStateException("Key generator was not specified and no key generator was used before.");
            }
         }
         if (valueGenerator == null) {
            valueGenerator = (ValueGenerator) slaveState.get(ValueGenerator.VALUE_GENERATOR);
            if (valueGenerator == null) {
               throw new IllegalStateException("Value generator was not specified and no key generator was used before.");
            }
         }
         CheckResult result = new CheckResult();
         if (memoryOnly && inMemoryBasicOperations != null) {
            basicCache = inMemoryBasicOperations.getMemoryOnlyCache(getCacheName());
         } else {
            basicCache = basicOperations.getCache(getCacheName());
         }
         if (debugable != null) {
            debugableCache = debugable.getCache(getCacheName());
         }

         try {
            if (checkThreads <= 1) {
               long entriesToCheck = numEntries;
               long initValue = firstEntryOffsetSlaveIndex > 0 ? firstEntryOffsetSlaveIndex * slaveState.getSlaveIndex() : firstEntryOffset;
               for (long i = initValue; entriesToCheck > 0; i += stepEntryCount) {
                  long checkAmount = Math.min(checkEntryCount, entriesToCheck);
                  for (long j = 0; j < checkAmount; ++j) {
                     if (!checkKey(basicCache, debugableCache, i + j, result, valueGenerator)) {
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
               executor.shutdown();
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
         long extraEntries = getExtraEntries();
         long commonEntries = isDeleted() ? 0 : numEntries;
         long myExpectedOwnedSize = (commonEntries + extraEntries) / liveSlavesHint;
         int numOwners = info.getNumReplicas();
         long myExpectedLocalSize = myExpectedOwnedSize * (numOwners < 0 ? -numOwners * slaveState.getClusterSize() : numOwners);
         for (int attempt = 0; attempt < 5; ++attempt) {
            long owned = info.getOwnedSize();
            long local = info.getLocallyStoredSize();
            double ratioOwned = (double) owned / (double) myExpectedOwnedSize;
            double ratioLocal = (double) local / (double) myExpectedLocalSize;
            if (ratioOwned < 0.9 || ratioOwned > 1.1) {
               log.warn("Owned size (" + owned + ") differs substantially from expected size (" + myExpectedOwnedSize + "), waiting 30s to let it replicate");
            } else if (ratioLocal < 0.9 || ratioLocal > 1.1) {
               log.warn("Locally stored size (" + local + ") differs substantially from expected size (" + myExpectedLocalSize + "), waiting 30s to let it replicate");
            } else {
               break;
            }
            try {
               Thread.sleep(30000);
            } catch (InterruptedException e) {
               break;
            }
         }
      }
      return new InfoAck(slaveState, info.getOwnedSize(), info.getLocallyStoredSize(), info.getTotalSize(), info.getStructuredSize(), info.getNumReplicas());
   }

   private String getCacheName() {
      CacheSelector selector = (CacheSelector) slaveState.get(CacheSelector.CACHE_SELECTOR);
      return selector == null ? null : selector.getCacheName(-1);
   }

   private class CheckRangeTask implements Callable<CheckResult> {
      private long from, to;

      public CheckRangeTask(long from, long to) {
         this.from = from;
         this.to = to;
      }

      @Override
      public CheckResult call() throws Exception {
         try {
            CheckResult result = new CheckResult();
            long entriesToCheck = to - from;
            long addend = firstEntryOffsetSlaveIndex > 0 ? firstEntryOffsetSlaveIndex * slaveState.getSlaveIndex() : firstEntryOffset;
            for (long i = from * (stepEntryCount / checkEntryCount) + addend; entriesToCheck > 0; i += stepEntryCount) {
               long checkAmount = Math.min(checkEntryCount, entriesToCheck);
               for (long j = 0; j < checkAmount; ++j) {
                  if (!checkKey(basicCache, debugableCache, i + j, result, valueGenerator)) {
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

   protected long getExpectedNumEntries() {
      return numEntries;
   }

   protected boolean checkKey(BasicOperations.Cache basicCache, Debugable.Cache debugableCache, long keyIndex, CheckResult result, ValueGenerator generator) {
      Object key = keyGenerator.generateKey(keyIndex);
      try {
         Object value = basicCache.get(key);
         if (!isDeleted()) {
            if (value != null && generator.checkValue(value, key, entrySize)) {
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
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      long sumOwnedSize = 0, sumLocalSize = 0;
      Long totalSize = null;
      Integer numReplicas = null;
      Map<Object, Map<Integer, Long>> subparts = new HashMap<>();
      for (DistStageAck ack : acks) {
         if (!(ack instanceof InfoAck)) {
            continue;
         }
         InfoAck info = (InfoAck) ack;
         log.debugf("Slave %d has owned size %d, local size %d and total size %d", ack.getSlaveIndex(), info.ownedSize, info.localSize, info.totalSize);
         sumLocalSize += info.localSize;
         sumOwnedSize += info.ownedSize;
         if (info.totalSize >= 0) {
            if (totalSize == null) totalSize = info.totalSize;
            else if (totalSize != info.totalSize) {
               log.errorf("Slave %d reports total size %d but other slave reported %d", ack.getSlaveIndex(), info.totalSize, totalSize);
               result = errorResult();
            }
         } else if (totalSize != null) {
            log.errorf("Slave %d does not report any total size but other slave reported %d", ack.getSlaveIndex(), totalSize);
            result = errorResult();
         }
         if (numReplicas == null) numReplicas = info.numReplicas;
         else if (numReplicas != info.numReplicas) {
            log.errorf("Slave %d reports %d replicas but other slave reported %d replicas", ack.getSlaveIndex(), info.numReplicas, numReplicas);
            result = errorResult();
         }
         long sumSubpartSize = 0;
         for (Map.Entry<?, Long> subpart : info.structuredSize.entrySet()) {
            log.tracef("Subpart %s = %d", subpart.getKey(), subpart.getValue());
            if (subpart.getValue() == 0) continue;

            sumSubpartSize += subpart.getValue();
            Map<Integer, Long> otherSubparts = subparts.get(subpart.getKey());
            if (otherSubparts == null) {
               subparts.put(subpart.getKey(), new HashMap<>(Collections.singletonMap(info.getSlaveIndex(), subpart.getValue())));
            } else if (checkSubpartsEqual) {
               for (Map.Entry<Integer, Long> os : otherSubparts.entrySet()) {
                  if (Long.compare(subpart.getValue(), os.getValue()) != 0) {
                     log.errorf("Slave %d reports %s = %d but slave %d reported size %d",
                        info.getSlaveIndex(), subpart.getKey(), subpart.getValue(), os.getKey(), os.getValue());
                     result = errorResult();
                  }
               }
               otherSubparts.put(info.getSlaveIndex(), subpart.getValue());
            }
         }
         if (checkSubpartsSumLocal && sumSubpartSize != info.localSize) {
            log.errorf("On slave %d sum of subparts sizes (%d) is not the same as local size (%d)",
               info.getSlaveIndex(), sumSubpartSize, info.localSize);
            result = errorResult();
         }
      }
      if (checkSubpartsAreReplicas) {
         for (Map.Entry<Object, Map<Integer, Long>> subpart : subparts.entrySet()) {
            if (subpart.getValue().size() != numReplicas) {
               log.errorf("Subpart %s was found in %s, should have %d replicas.", subpart.getKey(), subpart.getValue().keySet(), numReplicas);
               result = errorResult();
            }
         }
      }
      if (ignoreSum) {
         log.infof("The sum of owned sizes is %d, sum of local sizes is %d", sumOwnedSize, sumLocalSize);
      } else {
         long extraEntries = getExtraEntries();
         long commonEntries = isDeleted() ? 0 : numEntries;
         long expectedOwnedSize = extraEntries + commonEntries;
         if (sumLocalSize >= 0) {
            long expectedLocalSize = expectedOwnedSize * (numReplicas < 0 ? -numReplicas * masterState.getClusterSize() : numReplicas);
            if (expectedLocalSize != sumLocalSize) {
               log.errorf("The cache should contain %d entries (including backups, %d replicas) but contains %d entries.", expectedLocalSize, numReplicas, sumLocalSize);
               result = errorResult();
            } else {
               log.tracef("The sum of local sizes is %d entries as expected", sumLocalSize);
            }
         }
         if (sumOwnedSize >= 0) {
            if (expectedOwnedSize != sumOwnedSize) {
               log.errorf("The cache should contain %d entries but contains %d entries.", expectedOwnedSize, sumOwnedSize);
               result = errorResult();
            } else {
               log.tracef("The sum of owned sizes is %d entries as expected", sumOwnedSize);
            }
         }
         if (totalSize != null) {
            if (expectedOwnedSize != totalSize) {
               log.errorf("The cache should contain %d entries but total size is %d.", expectedOwnedSize, totalSize);
               result = errorResult();
            } else {
               log.tracef("The total size is %d as expected", totalSize);
            }
         }
      }
      return result;
   }

   public long getNumEntries() {
      return this.numEntries;
   }

   private long getExtraEntries() {
      if (extraEntries == null) return 0;

      long sum = 0;
      int multiplicator = 1;
      try {
         for (String entries : extraEntries.split(",")) {
            long count = Long.parseLong(entries);
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
      final long ownedSize, localSize, totalSize;
      final Map<?, Long> structuredSize;
      final int numReplicas;
      final CheckResult checkResult;

      public InfoAck(SlaveState slaveState, long ownedSize, long localSize, long totalSize, Map<?, Long> structuredSize, int numReplicas) {
         super(slaveState);
         this.ownedSize = ownedSize;
         this.localSize = localSize;
         this.totalSize = totalSize;
         this.structuredSize = structuredSize;
         this.numReplicas = numReplicas;
         checkResult = null;
      }

      public InfoAck(SlaveState slaveState, CheckResult checkResult) {
         super(slaveState);
         this.checkResult = checkResult;
         ownedSize = localSize = totalSize = -1;
         structuredSize = null;
         numReplicas = -1;
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
}
