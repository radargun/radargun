package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Debugable;

/**
 * Checks loaded data for their validity.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Checks loaded data for validity. Useful for testing cross-site replication.")
public class XSReplCheckStage extends CheckCacheDataStage {

   @Property(doc = "Backup value generator. By default, only main (default) cache is checked. " +
      "If specified, backup caches will be checked too.", complexConverter = ValueGenerator.ComplexConverter.class)
   public ValueGenerator backupValueGenerator = null;

   @Property(doc = "Comma-separated list of all backup caches to be checked. Ignored if backup-value-generator is not specified.")
   public List<String> backupCaches = new ArrayList<>();

   private BasicOperations.Cache[] backupCacheInstances;
   private Debugable.Cache[] backupDebugable;

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         return successfulResponse();
      }
      if (backupValueGenerator != null) {
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
      if (valueGenerator == null) {
         valueGenerator = (ValueGenerator) slaveState.get(ValueGenerator.VALUE_GENERATOR);
         if (valueGenerator == null) {
            throw new IllegalStateException("Value generator was not specified and no key generator was used before.");
         }
      }
      return super.executeOnSlave();
   }

   @Override
   protected boolean checkKey(BasicOperations.Cache basicCache, Debugable.Cache debugableCache, long keyIndex, CheckResult result, ValueGenerator valueGenerator) {
      boolean retval = super.checkKey(basicCache, debugableCache, keyIndex, result, valueGenerator);
      for (int i = 0; i < backupCaches.size(); ++i) {
         retval = retval && super.checkKey(backupCacheInstances[i], backupDebugable[i], keyIndex, result, backupValueGenerator);
      }
      return retval;
   }

   @Override
   protected long getExpectedNumEntries() {
      return getNumEntries() * (backupCacheInstances != null ? backupCacheInstances.length + 1 : 1);
   }
}
