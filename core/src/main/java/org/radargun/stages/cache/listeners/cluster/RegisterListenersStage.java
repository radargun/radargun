package org.radargun.stages.cache.listeners.cluster;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.CacheListeners;
import org.radargun.traits.InjectTrait;

import java.util.Arrays;
import java.util.Collection;

/**
 * Run this stage if you want to compare performance with enabled/disabled cluster listenersTrait
 *
 * @author vchepeli@redhat.com
 * @since 2.0
 */
@Stage(doc = "Benchmark operations performance where cluster listenersTrait are enabled or disabled.")
public class RegisterListenersStage extends AbstractDistStage {

   @Property(doc = "Before stress stage, cluster listeners would be enabled. This is flag to turn them on. Default is false.")
   protected boolean registerListeners = false;

   @Property(doc = "Before stress stage, cluster listeners would be disabled. This is flag to turn them off. Default is false.")
   protected boolean unregisterListeners = false;

   @InjectTrait // with infinispan70 plugin
   private CacheListeners listenersTrait;

   private CacheListeners.CreatedListener createdListener;
   private CacheListeners.EvictedListener evictedListener;
   private CacheListeners.RemovedListener removedListener;
   private CacheListeners.UpdatedListener updatedListener;

   @Override
   public void initOnSlave(SlaveState slaveState) {
      super.initOnSlave(slaveState);
      if (registerListeners) {
         createdListener = new CacheListeners.CreatedListener() {
            @Override
            public void created(Object key, Object value) {
               log.trace("Created " + key + " -> " + value);
            }
         };

         evictedListener = new CacheListeners.EvictedListener() {
            @Override
            public void evicted(Object key, Object value) {
               log.trace("Evicted " + key + " -> " + value);
            }
         };

         removedListener = new CacheListeners.RemovedListener() {
            @Override
            public void removed(Object key, Object value) {
               log.trace("Removed " + key + " -> " + value);
            }
         };

         updatedListener = new CacheListeners.UpdatedListener() {
            @Override
            public void updated(Object key, Object value) {
               log.trace("Updated " + key + " -> " + value);
            }
         };
      }
   }

   @Override
   public DistStageAck executeOnSlave() {
      if (registerListeners)
         registerListeners();

      if (unregisterListeners)
         unregisterListeners();

      return successfulResponse();
   }

   public void registerListeners() {

      checkListenersSupported();
      if (createdListener != null)
         listenersTrait.addCreatedListener(null, createdListener);
      if (evictedListener != null)
         listenersTrait.addEvictedListener(null, evictedListener);
      if (removedListener != null)
         listenersTrait.addRemovedListener(null, removedListener);
      if (updatedListener != null)
         listenersTrait.addUpdatedListener(null, updatedListener);
   }

   public void unregisterListeners() {

      checkListenersSupported();
      listenersTrait.removeCreatedListeners(null);
      listenersTrait.removeEvictedListeners(null);
      listenersTrait.removeRemovedListeners(null);
      listenersTrait.removeUpdatedListeners(null);
   }

   private void checkListenersSupported() {
      if (listenersTrait == null) {
         throw new IllegalArgumentException("Service does not support cache listeners");
      }
      Collection<CacheListeners.Type> supported = listenersTrait.getSupportedListeners();
      if (!supported.containsAll(Arrays.asList(CacheListeners.Type.CREATED, CacheListeners.Type.EVICTED, CacheListeners.Type.REMOVED, CacheListeners.Type.UPDATED))) {
         throw new IllegalArgumentException("Service does not support required listener types; supported are: " + supported);
      }
   }
}
