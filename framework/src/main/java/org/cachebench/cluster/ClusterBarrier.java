package org.cachebench.cluster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.config.ClusterConfig;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Use for making the nodes in the cluster to hold until ALL nodes reached the barrier.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClusterBarrier implements Receiver
{
   private static Log log = LogFactory.getLog(ClusterBarrier.class);

   private ClusterConfig config;
   public final Map<SocketAddress, Object> receivedMessages = new HashMap<SocketAddress, Object>();
   private TcpTransport transport;
   private Object message;
   private int numMembers;
   private boolean acknowledge;
   private static final String ACK = "_ACK";
   private boolean failOnWrongMessaages;
   private String errorMessage;


   /**
    * Returns the messages sent between the nodes in the cluster.
    */
   public Map<SocketAddress, Object> getReceivedMessages()
   {
      return receivedMessages;
   }

   /**
    * Setting this to true would tide up barrier delays.
    */
   public void setAcknowledge(boolean acknowledge)
   {
      this.acknowledge = acknowledge;
      this.failOnWrongMessaages = acknowledge;
   }

   /**
    * Message sent between cluster members to ack on the barrier.
    */
   public void barrier(Object message) throws Exception
   {
      log.trace("Started processing a message cluster, message='" + message + "'");
      receivedMessages.clear();
      this.message = message;
      transport = new TcpTransport();
      numMembers = config.getClusterSize();
      transport.create(config);
      transport.setReceiver(this);
      transport.start();
      log.trace("Transport started, local address is: " + transport.getLocalAddress());
      log.trace("Waiting for " + numMembers + " member(s) to join");
      waitForAllMemebers();
      transport.stop();
      //just to make sure all other nodes closed their resources
      //needed e.g. when a new barrier is immediately initiated on this barrier which might connect
      //to a port on a barrier that is about to close, so messages sent by new barrier might be lost
      // a nicer way to implement this is to accept connections only if the sender uses same barrier name
      Thread.sleep(2000);
   }

   private void waitForAllMemebers() throws Exception
   {
      boolean receivedAllMessages = false;
      while (!receivedAllMessages)
      {
         synchronized (receivedMessages)
         {
            receivedAllMessages = receivedMessages.size() >= numMembers;
            if (!receivedAllMessages)
            {
               if (errorMessage != null)
               {
                  //might be that I am the intruder, give other members a chance to fail aswell
                  transport.send(message);
                  transport.stop();
                  Thread.sleep(2000);
                  throw new IllegalStateException(errorMessage);
               }
               receivedMessages.wait(2000);
            }
         }
         log.trace("sending message " + message + ", expecting " + getMissingMembersCount() + " member(s)");
         transport.send(message);
         if (acknowledge)
         {
            log.trace("Send ack also");
            transport.send(getAcknowledgeMessage(message));
         }
      }
   }

   public void receive(SocketAddress sender, Object payload) throws Exception
   {
      log.trace("Received '" + payload + "' from " + formatName(sender) + " still expecting " + getMissingMembersCount() + " member(s)");
      if (payload == null)
      {
         log.warn("payload is incorrect (sender=" + sender + "): " + payload);
         return;
      }
      if (acknowledge && !isAcknowledgeMessage(payload))
      {
         log.trace("Sending ack, still expecting " + getMissingMembersCount() + " members.");
         transport.send(getAcknowledgeMessage(message));
         return;
      }

      if (failOnWrongMessaages && !message.equals(payload) && !getAcknowledgeMessage(message).equals(payload))
      {
         errorMessage = "We recieved an message from a differenet barrier. This normally means that there is an stale " +
               "barrier running somewhere.The source of the message is '" + sender + "', message is:'" + payload + "', " +
               "and we were expecting '" + message + "'";
         log.error(errorMessage);
         this.receivedMessages.notifyAll();

      }      

      //we are here if either no ack or ack the message is an ack message
      synchronized (this.receivedMessages)
      {
         if (!this.receivedMessages.containsKey(sender))
         {
            this.receivedMessages.put(sender, getMessage(payload));
            int expected = getMissingMembersCount();
            log.trace("Sender " + sender + " registered, still waiting for " + expected + " member(s)");
            this.receivedMessages.notifyAll();
         } else {
            log.trace("Sender '" + formatName(sender) + "' is already registered in the list of known senders!");
         }
         log.trace("Current list of senders is: " + receivedMessages.keySet());
      }
   }

   private String formatName(SocketAddress sender)
   {
      return transport.isLocal(sender) ? "<local(" + sender + ")>" : String.valueOf(sender);
   }

   private int getMissingMembersCount()
   {
      return numMembers - receivedMessages.size();
   }

   private Object getMessage(Object payload)
   {
      if (!acknowledge)
      {
         return payload;
      }
      String payloadStr = payload.toString();
      int endIndex = payloadStr.length() - ACK.length();
      return payloadStr.substring(0, endIndex);
   }

   private String getAcknowledgeMessage(Object message)
   {
      return message.toString() + ACK;
   }

   private boolean isAcknowledgeMessage(Object payload)
   {
      return payload == null ? false : (payload.toString().indexOf(ACK) >= 0);
   }

   public void setConfig(ClusterConfig config)
   {
      this.config = config;
   }
}
