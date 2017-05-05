package org.radargun.protocols;

import org.jgroups.Event;
import org.jgroups.Message;
import org.radargun.service.SLAVE_PARTITION_33;

public class SLAVE_PARTITION_36 extends SLAVE_PARTITION_33 {
   
   @Override
   public Object down(Event evt) {
      switch (evt.getType()) {
         case Event.MSG:
            Message msg = (Message) evt.getArg();
            // putHeader signature has changed
            msg.putHeader(PROTOCOL_ID, new SlaveHeader(this.slaveIndex));
      }
      return down_prot.down(evt);
   }
}
