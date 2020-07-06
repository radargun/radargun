package org.radargun.protocols;

import org.jgroups.Event;
import org.jgroups.Message;
import org.radargun.service.WORKER_PARTITION_33;

public class WORKER_PARTITION_36 extends WORKER_PARTITION_33 {
   
   @Override
   public Object down(Event evt) {
      switch (evt.getType()) {
         case Event.MSG:
            Message msg = (Message) evt.getArg();
            // putHeader signature has changed
            msg.putHeader(PROTOCOL_ID, new WorkerHeader(this.workerIndex));
      }
      return down_prot.down(evt);
   }
}
