package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.XSReplicating;
import org.radargun.stages.helpers.Range;
import org.radargun.stressors.KeyGenerator;
import org.radargun.stressors.StringKeyGenerator;

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

   public XSReplLoadStage() {
      // nada
   }

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (!(slaveState.getCacheWrapper() instanceof XSReplicating)) {
         String error = "This stage requires wrapper that supports cross-site replication";
         log.error(error);
         ack.setError(true);
         ack.setErrorMessage(error);
         return ack;
      }
      KeyGenerator keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
      if (keyGenerator == null) {
         keyGenerator = new StringKeyGenerator();
      }
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      XSReplicating xsReplicating = (XSReplicating) wrapper;
      String cacheName = xsReplicating.getMainCache();
      Range myRange = Range.divideRange(numEntries, xsReplicating.getSlaves().size(), xsReplicating.getSlaves().indexOf(slaveState.getSlaveIndex()));
      for (int i = myRange.getStart(); i < myRange.getEnd(); ++i) {
         try {
            if (!delete) {
               wrapper.put(cacheName, keyGenerator.generateKey(i), "value" + i + valuePostFix + "@" + cacheName);
            } else {
               wrapper.remove(cacheName, keyGenerator.generateKey(i));
            }
         } catch (Exception e) {
            log.error("Error inserting key " + i + " into " + cacheName);
         }
      }
      return ack;
   }
}
