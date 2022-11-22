package org.radargun.protocols;

import org.jgroups.Message;

public class WORKER_PARTITION_36 extends WORKER_PARTITION_33 {

   @Override
   public Object down(Message msg) {
      msg.putHeader(PROTOCOL_ID, new WORKER_PARTITION.WorkerHeader(this.workerIndex));
      return super.down(msg);
   }
}
