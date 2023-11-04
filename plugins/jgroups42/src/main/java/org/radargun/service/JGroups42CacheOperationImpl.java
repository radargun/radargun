package org.radargun.service;

import org.jgroups.JChannel;
import org.jgroups.Message;

/**
 * Code copied from https://github.com/infinispan/infinispan/tree/13.0.x
 */
public class JGroups42CacheOperationImpl implements JGroupsCacheOperation {

   public static final short REQUEST_FLAGS_PER_SENDER = Message.Flag.NO_TOTAL_ORDER.value();
   public static final short REQUEST_FLAGS_UNORDERED =
         (short) (Message.Flag.OOB.value() | Message.Flag.NO_TOTAL_ORDER.value());

   private final JGroupsMarshaller marshaller;
   private final JChannel channel;

   public JGroups42CacheOperationImpl(JChannel channel) {
      this.channel = channel;
      this.marshaller = new JGroupsMarshaller();
   }

   @Override
   public void replicatedPut(Object key, Object value) {
      Message message = new Message();
      byte[] bytes = marshaller.toByteArray(key, value);
      message.setBuffer(bytes, 0, bytes.length);
      // we are copying the code from Infinispan, in this case we try to keep the var names in order to easy compare
      boolean noRelay = true;
      if (noRelay) {
         message.setFlag(Message.Flag.NO_RELAY.value());
      }
      String deliverOrder = "NONE";
      short flags = encodeDeliverMode(deliverOrder);
      message.setFlag(flags);
      message.setTransientFlag(Message.TransientFlag.DONT_LOOPBACK.value());
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
