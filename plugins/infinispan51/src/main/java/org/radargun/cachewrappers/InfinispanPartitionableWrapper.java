/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.cachewrappers;

import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.Parser;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.protocols.TP;
import org.jgroups.stack.ProtocolStack;
import org.radargun.features.Partitionable;
import org.radargun.protocols.SLAVE_PARTITION;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class InfinispanPartitionableWrapper extends InfinispanKillableWrapper implements Partitionable {
   
   private int mySlaveIndex = -1;
   private Set<Integer> initiallyReachable;

   @Override
   protected DefaultCacheManager createCacheManager(String configFile) throws IOException {
      ConfigurationBuilderHolder cbh = createConfiguration(configFile);
      cbh.getGlobalConfigurationBuilder().transport().transport(new HookedJGroupsTransport());
      return new DefaultCacheManager(cbh, true);
   }

   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws IOException {
      return new Parser(Thread.currentThread().getContextClassLoader()).parseFile(configFile);
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
      SLAVE_PARTITION partition = (SLAVE_PARTITION) channel.getProtocolStack().findProtocol(SLAVE_PARTITION.class);
      if (partition == null) {
         log.info("No SLAVE_PARTITION protocol found in stack for " + channel.getName() + ", inserting above transport protocol");
         partition = new SLAVE_PARTITION();
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
