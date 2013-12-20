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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jgroups.JChannel;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Relayer;
import org.radargun.protocols.SLAVE_PARTITION;

public class Infinispan52Lifecycle extends InfinispanPartitionableLifecycle {
   public Infinispan52Lifecycle(Infinispan52Wrapper wrapper) {
      super(wrapper);
   }

   @Override
   protected List<JChannel> getChannels(JChannel parentChannel, boolean failOnNotReady) {
      List<JChannel> list;
      if (parentChannel == null) {
         list = super.getChannels(null, failOnNotReady);
      } else {
         list = new ArrayList<JChannel>();
         list.add(parentChannel);
      }
      if (list.size() == 0) {
         log.info("No JGroups channels available");
         return list;
      }
      RELAY2 relay = (RELAY2) list.get(0).getProtocolStack().findProtocol(RELAY2.class);
      if (relay != null) {
         try {
            Field relayerField = RELAY2.class.getDeclaredField("relayer");
            relayerField.setAccessible(true);
            Relayer relayer = (Relayer) relayerField.get(relay);
            if (relayer == null) {
               log.debug("No relayer found");
               return list;
            }
            Field bridgesField = Relayer.class.getDeclaredField("bridges");
            bridgesField.setAccessible(true);
            Collection<?> bridges = (Collection<?>) bridgesField.get(relayer);
            if (bridges == null) {
               return list;
            }
            Field channelField = null;
            for (Object bridge : bridges) {
               if (channelField == null) {
                  channelField = bridge.getClass().getDeclaredField("channel");
                  channelField.setAccessible(true);                  
               }
               JChannel bridgeChannel = (JChannel) channelField.get(bridge);
               if (bridgeChannel.isOpen()) {
                  list.add(bridgeChannel);
               }
            }
         } catch (Exception e) {
            log.error("Failed to get channel from RELAY2 protocol", e);
         }
      } else {
         log.info("No RELAY2 protocol in XS wrapper");
      }
      return list;
   }

   @Override
   protected Class<? extends SLAVE_PARTITION> getPartitionProtocolClass() {
      return SLAVE_PARTITION_33.class;
   }
}
