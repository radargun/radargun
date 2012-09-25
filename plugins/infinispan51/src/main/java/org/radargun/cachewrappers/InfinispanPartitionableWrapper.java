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

import java.util.Set;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.TP;
import org.jgroups.stack.ProtocolStack;
import org.radargun.Partitionable;
import org.radargun.protocols.SLAVE_PARTITION;

public class InfinispanPartitionableWrapper extends InfinispanKillableWrapper implements Partitionable {
   
   private static Log log = LogFactory.getLog(InfinispanPartitionableWrapper.class);
   private int mySlaveIndex = -1;
   private Set<Integer> initiallyReachable;
     
   @Override
   protected void preStartInternal() {
      if (mySlaveIndex >= 0 && initiallyReachable != null) {
         setMembersInPartition(mySlaveIndex, initiallyReachable);
      }
   }
   
   @Override
   public void setMembersInPartition(int slaveIndex, Set<Integer> members) {
      JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();      
      JChannel channel = (JChannel) transport.getChannel();
      SLAVE_PARTITION partition = (SLAVE_PARTITION) channel.getProtocolStack().findProtocol(SLAVE_PARTITION.class);
      if (partition == null) {
         log.info("No SLAVE_PARTITION protocol found, inserting above transport protocol");
         partition = new SLAVE_PARTITION();
         try {
            channel.getProtocolStack().insertProtocol(partition, ProtocolStack.ABOVE, TP.class);
         } catch (Exception e) {
            log.error("Error inserting the SLAVE_PARTITION protocol");
            return;
         }         
      }
      partition.setSlaveIndex(slaveIndex);
      partition.setAllowedSlaves(members);      
   }

   @Override
   public void setStartWithReachable(int slaveIndex, Set<Integer> members) {
      initiallyReachable = members;
   }
}
