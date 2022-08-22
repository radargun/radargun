package org.radargun.service;

import org.jgroups.BytesMessage;
import org.jgroups.Header;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.RequestCorrelator;
import org.radargun.Service;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups52Service extends JGroups42Service {

   private static final short REPLY_FLAGS =
         (short) (Message.Flag.NO_FC.value() | Message.Flag.OOB.value() |
               Message.Flag.NO_TOTAL_ORDER.value());

   protected void setReceiver() {
      ch.setReceiver(new Receiver() {
         @Override
         public void receive(Message message) {
            if (sendResponse && message.getSrc() != null && !message.getSrc().equals(ch.getAddress())) {
               RequestCorrelator.Header header = message.getHeader(HEADER_ID);
               if (header != null && header.requestId() > 0) {
                  Message response = new BytesMessage(message.getSrc()).setFlag(REPLY_FLAGS, false);
                  try {
                     ch.send(response);
                  } catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               }
            }
         }
         @Override
         public void viewAccepted(View newView) {
            receiver.viewAccepted(newView);
         }
      });
   }

   @Override
   protected void sendMessage(Message message) {
      try {
         ch.send(message);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   protected Message newMessage(Object object) {
      Message message = new BytesMessage();
      for (String flag : flags) {
         message.setFlag(Message.Flag.valueOf(flag));
      }
      for (String transientFlag : transientFlags) {
         message.setFlag(Message.TransientFlag.valueOf(transientFlag));
      }
      Header header = new RequestCorrelator.Header((byte) 0, requestId.getAndIncrement(), (short) 0);
      message.putHeader(HEADER_ID, header);
      message.setObject(object);
      return message;
   }
}
