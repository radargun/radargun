package org.radargun.protocols;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Set;

import org.jgroups.Event;
import org.jgroups.Global;
import org.jgroups.Header;
import org.jgroups.Message;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Streamable;

public class WORKER_PARTITION extends Protocol {

   protected static final short PROTOCOL_ID = (short) 0x51A7;
   protected static Log log = LogFactory.getLog(WORKER_PARTITION.class);

   protected int workerIndex = -1;
   protected Set<Integer> allowedWorkers;

   static {
      log.info("Registering WORKER_PARTITION with id " + PROTOCOL_ID);
      ClassConfigurator.add(PROTOCOL_ID, WorkerHeader.class);
   }

   public void setAllowedWorkers(Set<Integer> workers) {
      allowedWorkers = workers;
   }

   public void setWorkerIndex(int workerIndex) {
      this.workerIndex = workerIndex;
   }

   @Override
   public Object up(Event evt) {
      switch (evt.getType()) {
         case Event.MSG:
            Message msg = (Message) evt.getArg();
            WorkerHeader header = (WorkerHeader) msg.getHeader(PROTOCOL_ID);
            if (header != null && header.getIndex() < 0) {
               log.trace("Message " + msg.getSrc() + " -> " + msg.getDest() + " with workerIndex -1");
            } else if (header != null && allowedWorkers != null) {
               if (!allowedWorkers.contains(header.getIndex())) {
                  log.trace("Discarding message " + msg.getSrc() + " -> " + msg.getDest() + " with workerIndex " + header.getIndex());
                  return null;
               }
            }
      }

      return up_prot.up(evt);
   }

   @Override
   public Object down(Event evt) {
      switch (evt.getType()) {
         case Event.MSG:
            Message msg = (Message) evt.getArg();
            msg.putHeader(PROTOCOL_ID, new WorkerHeader(this.workerIndex));
      }
      return down_prot.down(evt);
   }

   public static class WorkerHeader extends Header implements Streamable {
      int index = -1;

      public WorkerHeader() {
      }

      public WorkerHeader(int index) {
         this.index = index;
      }

      public int getIndex() {
         return index;
      }

      public String toString() {
         return "workerIndex=" + index;
      }

      public int size() {
         return Global.INT_SIZE;
      }

      @Override
      public void writeTo(DataOutput out) throws Exception {
         out.writeInt(index);
      }

      @Override
      public void readFrom(DataInput in) throws Exception {
         index = in.readInt();
      }
   }
}
