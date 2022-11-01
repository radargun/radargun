package org.radargun.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jgroups.JChannel;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Relayer;
import org.radargun.protocols.WORKER_PARTITION;

public class Infinispan52Lifecycle extends InfinispanPartitionableLifecycle {
   public Infinispan52Lifecycle(Infinispan52EmbeddedService service) {
      super(service);
   }

   @Override
   protected List<JChannel> getChannels(JChannel parentChannel) {
      List<JChannel> list;
      if (parentChannel == null) {
         list = super.getChannels(null);
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
         log.info("No RELAY2 protocol in XS service");
      }
      return list;
   }

   @Override
   protected Class<? extends WORKER_PARTITION> getPartitionProtocolClass() {
      return WORKER_PARTITION_33.class;
   }
}
