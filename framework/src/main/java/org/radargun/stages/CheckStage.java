package org.radargun.stages;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: rvansa
 * Date: 11/12/12
 * Time: 3:55 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class CheckStage extends AbstractDistStage {
   protected DefaultDistStageAck exception(DefaultDistStageAck ack, String message, Exception e) {
      log.error(message, e);
      ack.setError(true);
      ack.setErrorMessage(message);
      if (e != null) {
         ack.setRemoteException(e);
      }
      return ack;
   }

   protected boolean checkThreads(DefaultDistStageAck ack, List<ClientThread> threads) {
      for (ClientThread t : threads) {
         try {
            t.join();
            if (t.exception != null) {
               exception(ack, "Error in client thread", t.exception);
               return false;
            }
         } catch (InterruptedException e) {
            exception(ack, "Failed to join thread", e);
            return false;
         }
      }
      return true;
   }

   protected abstract class ClientThread extends Thread {
      public Exception exception;
   }
}
