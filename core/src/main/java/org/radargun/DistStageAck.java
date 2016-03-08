package org.radargun;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.radargun.logging.LogFactory;
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
      Set<Throwable> causes = new HashSet();

      while (remoteException != null) {
         sb.append(remoteException.toString());
         StackTraceElement[] stackTraceElements = remoteException.getStackTrace();
         if (stackTraceElements != null && stackTraceElements.length > 0) {
            for (StackTraceElement ste : stackTraceElements) {
               sb.append("\n\t at ").append(ste.toString());
            }
            sb.append("\nsuppressed: ");
            sb.append(Arrays.toString(remoteException.getSuppressed()));

            if (remoteException.getCause() != null) {
               sb.append("\ncaused by: ");
               remoteException = remoteException.getCause();
               // break if causes already contains exception to stop infinite loop
               if (!causes.add(remoteException)) {
                  break;
               }
            } else {
               break;
            }
         }
      }
      remoteExceptionString = sb.append('\n').toString();
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      Class<?> clazz = getClass();
      sb.append(clazz.getSimpleName()).append('{');
      while (clazz != Object.class && clazz != null) {
         for (Field f : clazz.getDeclaredFields()) {
            sb.append(f.getName()).append('=');
            try {
               f.setAccessible(true);
               sb.append(String.valueOf(f.get(this)));
            } catch (IllegalAccessException e) {
               LogFactory.getLog(clazz).warn("Failed to retrieve " + clazz.getName() + "." + f.getName(), e);
               sb.append("<unknown>");
            }
            sb.append(", ");
         }
         clazz = clazz.getSuperclass();
      }
      return sb.append('}').toString();
   }

   public void setDuration(long duration) {
      this.duration = duration;
   }

   public long getDuration() {
      return duration;
   }
}
