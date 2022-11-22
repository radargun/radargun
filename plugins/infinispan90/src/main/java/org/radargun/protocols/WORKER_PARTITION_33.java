package org.radargun.protocols;

import org.jgroups.Message;

/**
 * WORKER_PARTITION adapted for JGroups 3.3.x
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class WORKER_PARTITION_33 extends WORKER_PARTITION {
   @Override
   public Object down(Message msg) {
      msg.putHeader(PROTOCOL_ID, new WorkerHeader(this.workerIndex));
      return super.down(msg);
   }
}
