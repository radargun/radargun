package org.radargun.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.TP;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Relayer;
import org.jgroups.stack.ProtocolStack;
import org.radargun.Service;
import org.radargun.traits.Failure;
import org.radargun.traits.ProvidesTrait;

/**
 * Must have only failure stage related methods.
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan110FailureEmbeddedService extends Infinispan110EmbeddedService {

   @ProvidesTrait
   public Failure createFailureService() {
      return new Failure() {
         @Override
         public void createFailure(String action) {

            try {
               DISCARD discard = new DISCARD();
               discard.setDiscardAll(true);
               for (JChannel bridgeChannel : getBridgeChannels()) {
                  bridgeChannel.getProtocolStack().insertProtocol(discard, ProtocolStack.Position.ABOVE, TP.class);
               }
            } catch (Exception e) {
               throw new IllegalStateException("Cannot createFailure");
            }
         }

         @Override
         public void solveFailure(String action) {
            try {
               for (JChannel bridgeChannel : getBridgeChannels()) {
                  bridgeChannel.getProtocolStack().removeProtocol(DISCARD.class);
               }
            } catch (Exception e) {
               throw new IllegalStateException("Cannot solveFailure");
            }
         }

         @Override
         public boolean checkIfFailurePresent(String action, Object expectedValue) {
            int total = Integer.valueOf(expectedValue.toString());
            boolean match = false;
            long max = 60000;
            long now = System.currentTimeMillis();
            while (System.currentTimeMillis() - now <= max) {
               int siteSize = cacheManager.getTransport().getSitesView().size();
               if (siteSize == total) {
                  match = true;
                  break;
               }
               try {
                  Thread.sleep(1000);
               } catch (InterruptedException e) {
                  e.printStackTrace();
               }
            }
            return match;
         }

         private List<JChannel> getBridgeChannels() throws NoSuchFieldException, IllegalAccessException {
            JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
            RELAY2 relay2 = transport.getChannel().getProtocolStack().findProtocol("RELAY2");
            Field field = relay2.getClass().getDeclaredField("relayer");
            field.setAccessible(true);
            Relayer relayer = (Relayer) field.get(relay2);

            field = relayer.getClass().getDeclaredField("bridges");
            field.setAccessible(true);
            Queue<Object> bridges = (Queue<Object>) field.get(relayer);

            Iterator<Object> iterator = bridges.iterator();

            List<JChannel> bridgeChannels = new ArrayList<>();

            while (iterator.hasNext()) {
               Object object = iterator.next();
               field = object.getClass().getDeclaredField("channel");
               field.setAccessible(true);

               JChannel jChannel = (JChannel) field.get(object);
               bridgeChannels.add(jChannel);
            }

            return bridgeChannels;
         }
      };
   }
}
