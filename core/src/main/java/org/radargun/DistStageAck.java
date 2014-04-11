package org.radargun;

import java.io.Serializable;
import java.net.InetAddress;

import org.radargun.state.SlaveState;

/**
 * Asck that is sent from each slave to the master containing the result of the slave's processing for a stage.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class DistStageAck implements Serializable {

   private final int slaveIndex;
   private final InetAddress slaveAddress;
   private boolean isError;
   private String errorMessage;
   private long duration;
   private String remoteExceptionString;

   public DistStageAck(SlaveState slaveState) {
      slaveIndex = slaveState.getSlaveIndex();
      slaveAddress = slaveState.getLocalAddress();
   }

   public <T extends DistStageAck> T error(String message) {
      return error(message, null);
   }

   public <T extends DistStageAck> T error(String message, Throwable exception) {
      isError = true;
      errorMessage = message;
      setRemoteException(exception);
      return (T) this;
   }

   public int getSlaveIndex() {
      return slaveIndex;
   }

   public boolean isError() {
      return isError;
   }

   public void setRemoteException(Throwable remoteException) {
      StringBuilder sb = new StringBuilder();
      while (remoteException != null) {
         sb.append(remoteException.toString());
         StackTraceElement[] stackTraceElements = remoteException.getStackTrace();
         if (stackTraceElements != null && stackTraceElements.length > 0) {
            for (StackTraceElement ste : stackTraceElements) {
               sb.append("\n\t at ").append(ste.toString());
            }
            if (remoteException.getCause() != null) {
               sb.append("\ncaused by: ");
               remoteException = remoteException.getCause();
            } else {
               break;
            }
         }
      }
      remoteExceptionString = sb.append('\n').toString();
   }

   @Override
   public String toString() {
      return "DistStageAck{" +
            "slaveIndex=" + slaveIndex +
            ", slaveAddress=" + slaveAddress +
            ", isError=" + isError +
            ", errorMessage='" + errorMessage + '\'' +
            ", remoteExceptionString=" + remoteExceptionString +
            '}';
   }

   public void setDuration(long duration) {
      this.duration = duration;
   }

   public long getDuration() {
      return duration;
   }
}
