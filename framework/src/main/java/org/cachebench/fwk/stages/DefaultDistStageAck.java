package org.cachebench.fwk.stages;

import org.cachebench.fwk.DistStageAck;

import java.net.InetAddress;

/**
 * // TODO: Mircea - Document this!
 * //todo - add duration monitoring
 *
 * @author Mircea.Markus@jboss.com
 */
public class DefaultDistStageAck implements DistStageAck {

   private int nodeIndex;
   private InetAddress nodeAddress;

   private boolean isError;
   private Throwable remoteException;
   private String errorMessage;
   private Object payload;


   public DefaultDistStageAck(int nodeIndex, InetAddress nodeAddress) {
      this.nodeIndex = nodeIndex;
      this.nodeAddress = nodeAddress;
   }

   public int getNodeIndex() {
      return nodeIndex;
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
            "nodeIndex=" + nodeIndex +
            ", nodeAddress=" + nodeAddress +
            ", isError=" + isError +
            ", remoteException=" + remoteException +
            ", errorMessage='" + errorMessage + '\'' +
            '}';
   }

   public String getNodeDescription() {
      return nodeAddress + "(" + nodeIndex + ")";
   }
}
