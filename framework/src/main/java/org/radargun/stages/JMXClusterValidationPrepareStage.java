package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.state.MasterState;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects configuration for JMXClusterValidationStage.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public class JMXClusterValidationPrepareStage extends AbstractDistStage {

   public static final String STATE_JMX_CONN_TIMEOUT = "JMXClusterValidationPrepareStage_jmxConnectionTimeout";
   public static final String STATE_WAIT_TIMEOUT = "JMXClusterValidationPrepareStage_waitTimeout";
   public static final String STATE_PROP1 = "JMXClusterValidationPrepareStage_prop1";
   public static final String STATE_PROP2 = "JMXClusterValidationPrepareStage_prop2";
   public static final String STATE_PROP3 = "JMXClusterValidationPrepareStage_prop3";
   public static final String STATE_SLAVE_JMX_ENDPOINTS = "JMXClusterValidationPrepareStage_slaveJmxEndpoints";

   private long jmxConnectionTimeout = 3000;
   private long waitTimeout = 60000;
   private String prop1;
   private String prop2;
   private String prop3;

   public JMXClusterValidationPrepareStage() {
      // nada
   }

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         log.info("Obtaining JMX endpoint info for master...");
         String portProp = System.getProperty("com.sun.management.jmxremote.port");
         if (portProp == null) {
            ack.setErrorMessage("JMX not enabled on slave " + getSlaveIndex());
            ack.setError(true);
         } else {
            ack.setPayload(new InetSocketAddress(slaveState.getLocalAddress(), Integer.valueOf(portProp)));
         }
         return ack;
      } catch (Exception e) {
         ack.setRemoteException(e);
         ack.setError(true);
         return ack;
      }
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      List<InetSocketAddress> slaveAddrs = new ArrayList<InetSocketAddress>(acks.size());
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dack = (DefaultDistStageAck) ack;
         if (dack.isError()) {
            log.error("Error from slave " + dack.getSlaveIndex() + ": " + dack.getErrorMessage());
            return false;
         } else {
            if (dack.getPayload() != null) {
               slaveAddrs.add((InetSocketAddress) dack.getPayload());
            }
         }
      }
      log.info("Collected JMX endpoints: " + slaveAddrs);
      masterState.put(STATE_JMX_CONN_TIMEOUT, jmxConnectionTimeout);
      masterState.put(STATE_WAIT_TIMEOUT, waitTimeout);
      masterState.put(STATE_SLAVE_JMX_ENDPOINTS, slaveAddrs);
      if (prop1 != null) {
         masterState.put(STATE_PROP1, prop1);
      }
      if (prop2 != null) {
         masterState.put(STATE_PROP2, prop2);
      }
      if (prop3 != null) {
         masterState.put(STATE_PROP3, prop3);
      }
      return true;
   }

   @Override
   public String toString() {
      return "JMXClusterValidationPrepareStage {jmxConnectionTimeout=" + jmxConnectionTimeout + ", waitTimeout="
            + waitTimeout + ", prop1=" + prop1 + ", prop2=" + prop2 + ", prop3=" + prop3 + ", " + super.toString();
   }

   public void setProp1(String prop1) {
      this.prop1 = prop1;
   }

   public void setProp2(String prop2) {
      this.prop2 = prop2;
   }

   public void setProp3(String prop3) {
      this.prop3 = prop3;
   }

   public void setJmxConnectionTimeout(long jmxConnectionTimeout) {
      this.jmxConnectionTimeout = jmxConnectionTimeout;
   }

   public void setWaitTimeout(long waitTimeout) {
      this.waitTimeout = waitTimeout;
   }
}
