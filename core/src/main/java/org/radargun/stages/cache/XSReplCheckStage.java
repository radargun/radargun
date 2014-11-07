package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.cache.generators.CacheAwareTextGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Debugable;
import org.radargun.utils.Utils;

/**
 * Checks loaded data for their validity.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Checks loaded data for validity. Useful for testing cross-site replication.")
public class XSReplCheckStage extends CheckCacheDataStage {

   @Property(doc = "Full class name of backup value generator. By default, only main (default) cache is chcecked. " +
         "If specified, backup caches will be checked too.")
   protected String backupValueGeneratorClass = null;

   @Property(doc = "Parameter used to initialize backup value generator. Null by default.")
   protected String backupValueGeneratorParam = null;

   @Property(doc = "Comma-separated list of all backup caches to be checked. Ignored if backup-value-generator is not specified.")
   protected List<String> backupCaches = new ArrayList<>();

   private MainCacheValueChecker mainCacheValueChecker;
   private BackupCacheValueChecker backupCacheValueChecker;
   private ValueGenerator valueGenerator;
   private ValueGenerator backupValueGenerator;

   private BasicOperations.Cache[] backupCacheInstances;
   private Debugable.Cache[] backupDebugable;

   @Override
   public DistStageAck executeOnSlave() {
      if (backupValueGeneratorClass != null) {
         backupValueGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), backupValueGeneratorClass, backupValueGeneratorParam);
         backupCacheValueChecker = new BackupCacheValueChecker();
         int numBackups = backupCaches.size();
         backupCacheInstances = new BasicOperations.Cache[numBackups];
         if (debugable != null) {
            backupDebugable = new Debugable.Cache[numBackups];
         }
         for (int i = 0; i < backupCaches.size(); i++) {
            String cacheName = backupCaches.get(i);
            backupCacheInstances[i] = basicOperations.getCache(cacheName);
            if (debugable != null) {
               backupDebugable[i] = debugable.getCache(cacheName);
            }
         }
      }
      valueGenerator = getValueGenerator();
      if (valueGenerator == null) {
         throw new IllegalStateException("Value generator has not been specified");
      }
      mainCacheValueChecker = new MainCacheValueChecker();
      return super.executeOnSlave();
   }

   @Override
   protected ValueGenerator getValueGenerator() {
      ValueGenerator valueGenerator = super.getValueGenerator();
      if (!(valueGenerator instanceof CacheAwareTextGenerator)) {
         throw new IllegalArgumentException("XSReplCheckStage supports only org.radargun.stages.cache.generators.CacheAwareTextGenerator.");
      }
      return valueGenerator;
   }

   @Override
   protected boolean checkKey(BasicOperations.Cache basicCache, Debugable.Cache debugableCache, int keyIndex, CheckResult result, ValueChecker checker) {
      boolean retval = super.checkKey(basicCache, debugableCache, keyIndex, result, mainCacheValueChecker);
      for (int i = 0; i < backupCaches.size(); ++i) {
         retval = retval && super.checkKey(backupCacheInstances[i], backupDebugable[i], keyIndex, result, backupCacheValueChecker);
      }
      return retval;
   }

   @Override
   protected int getExpectedNumEntries() {
      return getNumEntries() * (backupCacheInstances != null ? backupCacheInstances.length + 1 : 1);
   }

   /**
    * Checks the values of main (default) cache
    */
   private class MainCacheValueChecker implements ValueChecker {

      public MainCacheValueChecker() {
         if (valueGenerator == null) {
            throw new IllegalStateException("Value generator has not been initialized.");
         }
      }

      @Override
      public boolean check(Object value, Object key) {
         return valueGenerator.checkValue(value, key, entrySize);
      }
   }

   /**
    * Checks the values of backup cache
    */
   private class BackupCacheValueChecker implements ValueChecker {

      private BackupCacheValueChecker() {
         if (backupValueGenerator == null) {
            throw new IllegalStateException("Backup value generator has not been specified.");
         }
      }

      @Override
      public boolean check(Object value, Object key) {
         return backupValueGenerator.checkValue(value, key, entrySize);
      }
   }

}
