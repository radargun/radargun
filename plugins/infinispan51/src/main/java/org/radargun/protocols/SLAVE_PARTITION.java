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

public class SLAVE_PARTITION extends Protocol {

   protected static final short PROTOCOL_ID = (short)0x51A7;
   protected static Log log = LogFactory.getLog(SLAVE_PARTITION.class);
   
   protected int slaveIndex = -1;
   protected Set<Integer> allowedSlaves;
   
   static {
      log.info("Registering SLAVE_PARTITION with id " + PROTOCOL_ID);
      ClassConfigurator.add(PROTOCOL_ID, SlaveHeader.class);
   }
   
   public void setAllowedSlaves(Set<Integer> slaves) {
      allowedSlaves = slaves;
   }
   
   public void setSlaveIndex(int slaveIndex) {
      this.slaveIndex = slaveIndex;
   }
   
   @Override
   public Object up(Event evt) {
      switch (evt.getType()) {
      case Event.MSG:
         Message msg = (Message) evt.getArg();
         SlaveHeader header = (SlaveHeader)msg.getHeader(PROTOCOL_ID);
         if (header != null && header.getIndex() < 0) {
            log.trace("Message " + msg.getSrc() + " -> " + msg.getDest() + " with slaveIndex -1");
         } else if (header != null && allowedSlaves != null) {
            if (!allowedSlaves.contains(header.getIndex())) {
               log.trace("Discarding message " + msg.getSrc() + " -> " + msg.getDest() + " with slaveIndex " + header.getIndex());
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
         msg.putHeader(PROTOCOL_ID, new SlaveHeader(this.slaveIndex));         
      }
      return down_prot.down(evt);
   }
   
   public static class SlaveHeader extends Header implements Streamable {
      int index = -1;

      public SlaveHeader() {
      }

      public SlaveHeader(int index) {
          this.index=index;
      }
      
      public int getIndex() {
         return index;
      }

      public String toString() {
          return "slaveIndex=" + index;
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
         index=in.readInt();
      }
   }
}
