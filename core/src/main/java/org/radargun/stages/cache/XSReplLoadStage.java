package org.radargun.stages.cache;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.helpers.Range;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.InjectTrait;

/**
 * Loads data into the cache with input cache name encoded into the value.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Loads data into the cache with input cache name encoded into the value.")
public class XSReplLoadStage extends AbstractDistStage {

   @Property(optional = false, doc = "Amount of entries that should be inserted into the cache.")
   private int numEntries;

   @Property(doc = "If set to true, the entries are removed instead of being inserted. Default is false.")
   private boolean delete = false;

   @Property(doc = "String encoded into the value so that the entry may be distinguished from entries loaded in " +
         "different load stages. Default is empty string.")
   private String valuePostFix = "";

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private CacheInformation cacheInformation;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;


   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
            KeyGenerator keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
      if (keyGenerator == null) {
         keyGenerator = new StringKeyGenerator();
      }
      String cacheName = cacheInformation.getDefaultCacheName();
      BasicOperations.Cache cache = basicOperations.getCache(cacheName);
      Range myRange = Range.divideRange(numEntries, slaveState.getGroupSize(), slaveState.getIndexInGroup());
      for (int i = myRange.getStart(); i < myRange.getEnd(); ++i) {
         try {
            if (!delete) {
               cache.put(keyGenerator.generateKey(i), "value" + i + valuePostFix + "@" + cacheName);
            } else {
               cache.remove(keyGenerator.generateKey(i));
            }
         } catch (Exception e) {
            log.error("Error inserting key " + i + " into " + cacheName);
         }
      }
      return ack;
   }
}
