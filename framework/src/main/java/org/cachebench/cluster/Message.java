package org.cachebench.cluster;

import java.io.Serializable;
import java.net.SocketAddress;
import java.net.InetSocketAddress;

/**
 * Encapsulates an message to be send on the wire.
 * @author Mircea.Markus@jboss.com
 */
public class Message implements Serializable {
   private InetSocketAddress source;
   private Object payload;

   public Message(InetSocketAddress source, Object payload) {
      this.source = source;
      this.payload = payload;
   }

   public InetSocketAddress getSource() {
      return source;
   }

   public Object getPayload() {
      return payload;
   }
}
