package org.radargun.service;

import org.jgroups.Event;
import org.jgroups.Message;
import org.radargun.protocols.WORKER_PARTITION;

/**
 * WORKER_PARTITION adapted for JGroups 3.3.x
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class WORKER_PARTITION_33 extends WORKER_PARTITION {
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
