package org.radargun.service;

import org.jgroups.BytesMessage;
import org.jgroups.JChannel;
import org.jgroups.Message;

/**
 * Code copied from https://github.com/infinispan/infinispan/tree/14.0.x
 */
public class JGroups52CacheOperation implements JGroups42CacheOperation {

   private static final short REQUEST_FLAGS_PER_SENDER = Message.Flag.NO_TOTAL_ORDER.value();
   private static final short REQUEST_FLAGS_UNORDERED =
         (short) (Message.Flag.OOB.value() | Message.Flag.NO_TOTAL_ORDER.value() |
               Message.Flag.DONT_BUNDLE.value());

   private final JGroups42Marshaller marshaller;
   private final JChannel channel;

   public JGroups52CacheOperation(JChannel channel) {
      this.channel = channel;
      this.marshaller = new JGroups42Marshaller();
   }

   @Override
   public void replicatedPut(Object key, Object value) {
      Message message = new BytesMessage();
      byte[] bytes = marshaller.toByteArray(key, value);
      message.setArray(bytes, 0, bytes.length);
      // we are copying the code from Infinispan, in this case we try to keep the var names in order to easy compare
      boolean noRelay = true;
      String deliverOrder = "NONE";
      short flags = encodeDeliverMode(deliverOrder);
      if (noRelay) {
         flags |= Message.Flag.NO_RELAY.value();
      }
      message.setFlag(flags, false);
      message.setFlag(Message.TransientFlag.DONT_LOOPBACK);
      send(message);
   }

   private static short encodeDeliverMode(String deliverOrder) {
      switch (deliverOrder) {
         case "PER_SENDER":
            return REQUEST_FLAGS_PER_SENDER;
         case "NONE":
            return REQUEST_FLAGS_UNORDERED;
         default:
            throw new IllegalArgumentException("Unsupported deliver mode " + deliverOrder);
      }
   }

   private void send(Message message) {
      try {
         channel.send(message);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }
}
