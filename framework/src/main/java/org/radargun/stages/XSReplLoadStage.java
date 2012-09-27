package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.features.XSReplicating;
import org.radargun.stages.helpers.RangeHelper;
import org.radargun.state.MasterState;

/**
 * @author Radim Vansa <rvansa@redhat.com>
 */
public class XSReplLoadStage extends AbstractDistStage {

   int numEntries;
   
   public XSReplLoadStage() {
      // nada
   }

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
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
      XSReplicating wrapper = (XSReplicating) slaveState.getCacheWrapper();
      String cacheName = wrapper.getMainCache();
      RangeHelper.Range myRange = RangeHelper.divideRange(numEntries, wrapper.getSlaves().size(), wrapper.getSlaves().indexOf(getSlaveIndex()));
      for (int i = myRange.getStart(); i < myRange.getEnd(); ++i) {
         try {
            wrapper.put(cacheName, "key" + i, "value" + i + "@" + cacheName);
         } catch (Exception e) {
            log.error("Error inserting key " + i + " into " + cacheName);
         }
      }
      return ack;
   }

   @Override
   public String toString() {
      return "XSReplLoadStage {" + super.toString();
   }

   public void setNumEntries(int entries) {
      numEntries = entries;
   }
}
