package org.radargun.stages;

import org.radargun.config.Stage;

import java.util.List;

/**
 * Abstract stage that handles error messages from multiple threads
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "")
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
