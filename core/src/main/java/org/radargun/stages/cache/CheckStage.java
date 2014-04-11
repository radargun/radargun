package org.radargun.stages.cache;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;

/**
 * Abstract stage that handles error messages from multiple threads
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "")
public abstract class CheckStage extends AbstractDistStage {

   protected DistStageAck checkThreads(List<ClientThread> threads) {
      for (ClientThread t : threads) {
         try {
            t.join();
            if (t.exception != null) {
               return errorResponse("Error in client thread", t.exception);
            }
         } catch (InterruptedException e) {
            return errorResponse("Failed to join thread", e);
         }
      }
      return null;
   }

   protected abstract class ClientThread extends Thread {
      public Exception exception;
   }
}
