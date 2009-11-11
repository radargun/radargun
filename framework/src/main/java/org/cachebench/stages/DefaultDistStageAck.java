package org.cachebench.stages;

import org.cachebench.DistStageAck;

import java.net.InetAddress;

/**
 * Default implementation for a distributed stage.
 *
 * @author Mircea.Markus@jboss.com
 */
public class DefaultDistStageAck implements DistStageAck {

   private int slaveIndex;
   private InetAddress slaveAddress;

   private boolean isError;
   private Throwable remoteException;
   private String errorMessage;
   private Object payload;

   private long duration;


   public DefaultDistStageAck(int slaveIndex, InetAddress slaveAddress) {
      this.slaveIndex = slaveIndex;
      this.slaveAddress = slaveAddress;
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

   public Throwable getRemoteException() {
      return remoteException;
   }

   public void setRemoteException(Throwable remoteException) {
      this.remoteException = remoteException;
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
            ", remoteException=" + remoteException +
            ", errorMessage='" + errorMessage + '\'' +
            ", payload=" + payload +
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
