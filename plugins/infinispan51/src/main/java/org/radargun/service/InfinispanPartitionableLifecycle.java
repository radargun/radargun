package org.radargun.service;

import java.util.List;
import java.util.Set;

import org.infinispan.remoting.transport.Transport;
import org.jgroups.JChannel;
import org.jgroups.protocols.TP;
import org.jgroups.stack.ProtocolStack;
import org.radargun.protocols.WORKER_PARTITION;
import org.radargun.traits.Partitionable;

public class InfinispanPartitionableLifecycle extends InfinispanKillableLifecycle implements Partitionable {

   private int myWorkerIndex = -1;
   private Set<Integer> initiallyReachable;

   public InfinispanPartitionableLifecycle(Infinispan51EmbeddedService wrapper) {
      super(wrapper);
   }

   protected Class<? extends WORKER_PARTITION> getPartitionProtocolClass() {
      return WORKER_PARTITION.class;
   }

   @Override
   public void setMembersInPartition(int workerIndex, Set<Integer> members) {
      List<JChannel> channels = getChannels(null);
      log.trace("Found " + channels.size() + " channels");
      for (JChannel channel : channels) {
         setPartitionInChannel(channel, workerIndex, members);
      }
   }

   protected void setPartitionInChannel(JChannel channel, int workerIndex, Set<Integer> members) {
      log.trace("Setting partition in channel " + channel);
      WORKER_PARTITION partition = (WORKER_PARTITION) channel.getProtocolStack().findProtocol(getPartitionProtocolClass());
      if (partition == null) {
         log.info("No WORKER_PARTITION protocol found in stack for " + channel.getName() + ", inserting above transport protocol");
         try {
            partition = getPartitionProtocolClass().newInstance();
         } catch (Exception e) {
            log.error("Error creating WORKER_PARTITION protocol", e);
            return;
         }
         try {
            channel.getProtocolStack().insertProtocol(partition, ProtocolStack.ABOVE, TP.class);
         } catch (Exception e) {
            log.error("Error inserting the WORKER_PARTITION protocol to stack for " + channel.getName());
            return;
         }
      }
      partition.setWorkerIndex(workerIndex);
      partition.setAllowedWorkers(members);
      log.trace("Finished setting partition in channel " + channel);
   }

   @Override
   public void setStartWithReachable(int workerIndex, Set<Integer> members) {
      myWorkerIndex = workerIndex;
      initiallyReachable = members;
   }

   public Transport createTransport() {
      return new Infinispan51HookedJGroupsTransport(this);
   }

   public void handleStartJGroupsChannelIfNeeded(JChannel channel) {
      log.trace("My index is " + myWorkerIndex + " and these workers should be reachable: " + initiallyReachable);
      if (myWorkerIndex >= 0 && initiallyReachable != null) {
         List<JChannel> channels = getChannels(channel);
         log.trace("Found " + channels.size() + " channels");
         for (JChannel c : channels) {
            setPartitionInChannel(c, myWorkerIndex, initiallyReachable);
         }
      }
   }
}
