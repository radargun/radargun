package org.radargun.service;

import java.util.List;
import java.util.Set;

import org.infinispan.remoting.transport.Transport;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.protocols.TP;
import org.jgroups.stack.ProtocolStack;
import org.radargun.traits.Partitionable;
import org.radargun.protocols.SLAVE_PARTITION;

public class InfinispanPartitionableLifecycle extends InfinispanKillableLifecycle implements Partitionable {
   
   private int mySlaveIndex = -1;
   private Set<Integer> initiallyReachable;

   public InfinispanPartitionableLifecycle(Infinispan51EmbeddedService wrapper) {
      super(wrapper);
   }

   protected Class<? extends SLAVE_PARTITION> getPartitionProtocolClass() {
      return SLAVE_PARTITION.class;
   }

   @Override
   public void setMembersInPartition(int slaveIndex, Set<Integer> members) {
      List<JChannel> channels = getChannels(null, true);
      log.trace("Found " + channels.size() + " channels");
      for (JChannel channel : channels) {
         setPartitionInChannel(channel, slaveIndex, members);
      }
   }

   private void setPartitionInChannel(JChannel channel, int slaveIndex, Set<Integer> members) {
      log.trace("Setting partition in channel " + channel);
      SLAVE_PARTITION partition = (SLAVE_PARTITION) channel.getProtocolStack().findProtocol(getPartitionProtocolClass());
      if (partition == null) {
         log.info("No SLAVE_PARTITION protocol found in stack for " + channel.getName() + ", inserting above transport protocol");
         try {
            partition = getPartitionProtocolClass().newInstance();
         } catch (Exception e) {
            log.error("Error creating SLAVE_PARTITION protocol", e);
            return;
         }
         try {
            channel.getProtocolStack().insertProtocol(partition, ProtocolStack.ABOVE, TP.class);
         } catch (Exception e) {
            log.error("Error inserting the SLAVE_PARTITION protocol to stack for " + channel.getName());
            return;
         }
      }
      partition.setSlaveIndex(slaveIndex);
      partition.setAllowedSlaves(members);
      log.trace("Finished setting partition in channel " + channel);
   }

   @Override
   public void setStartWithReachable(int slaveIndex, Set<Integer> members) {
      mySlaveIndex = slaveIndex;
      initiallyReachable = members;
   }

   public Transport createTransport() {
      return new HookedJGroupsTransport();
   }

   private class HookedJGroupsTransport extends JGroupsTransport {
      /**
       * This is called after the channel is initialized but before it is connected
       */
      @Override
      protected void startJGroupsChannelIfNeeded() {
         log.trace("My index is " + mySlaveIndex + " and these slaves should be reachable: " + initiallyReachable);
         if (mySlaveIndex >= 0 && initiallyReachable != null) {
            List<JChannel> channels = getChannels((JChannel) this.channel, true);
            log.trace("Found " + channels.size() + " channels");
            for (JChannel channel : channels) {
               setPartitionInChannel(channel, mySlaveIndex, initiallyReachable);
            }
         }
         super.startJGroupsChannelIfNeeded();
      }
   }
}
