package org.radargun.service;

import org.jgroups.Event;
import org.jgroups.Message;
import org.radargun.protocols.SLAVE_PARTITION;

/**
 * SLAVE_PARTITION adapted for JGroups 3.3.x
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SLAVE_PARTITION_33 extends SLAVE_PARTITION {
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
