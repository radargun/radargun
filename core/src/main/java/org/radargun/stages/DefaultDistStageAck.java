package org.radargun.stages;

import java.net.InetAddress;

import org.radargun.DistStageAck;

/**
 * Default implementation for a distributed stage.
 *
 * @author Mircea.Markus@jboss.com
 */
public class DefaultDistStageAck implements DistStageAck {

   private int slaveIndex;
   private InetAddress slaveAddress;

   private boolean isError;
   private String errorMessage;
   private Object payload;

   private long duration;

   private String remoteExceptionString;

   public DefaultDistStageAck(int slaveIndex, InetAddress slaveAddress) {
      this.slaveIndex = slaveIndex;
      this.slaveAddress = slaveAddress;
   }

   public DefaultDistStageAck error(String message, Throwable exception) {
      isError = true;
      errorMessage = message;
      setRemoteException(exception);
      return this;
   }

   public int getSlaveIndex() {
      return slaveIndex;
   }

   public boolean isError() {
      return isError;
   }

   public void setError(boolean error) {
      isError = error;
   }

   public String getRemoteExceptionString() {
      return remoteExceptionString;
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

   public String getErrorMessage() {
      return errorMessage;
   }

   public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
   }

   public Object getPayload() {
      return payload;
   }

   public void setPayload(Object payload) {
      this.payload = payload;
   }

   @Override
   public String toString() {
      return "DefaultDistStageAck{" +
            "slaveIndex=" + slaveIndex +
            ", slaveAddress=" + slaveAddress +
            ", isError=" + isError +
            ", errorMessage='" + errorMessage + '\'' +
            ", payload=" + payload +
            ", remoteExceptionString=" + remoteExceptionString +
            '}';
   }

   public String getSlaveDescription() {
      return slaveAddress + "(" + slaveIndex + ")";
   }

   public void setDuration(long duration) {
      this.duration = duration;
   }

   public long getDuration() {
      return duration;
   }
}
