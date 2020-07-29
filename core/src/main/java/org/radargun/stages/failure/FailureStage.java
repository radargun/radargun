package org.radargun.stages.failure;

import org.radargun.DistStageAck;
import org.radargun.config.Experimental;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.Failure;
import org.radargun.traits.InjectTrait;

/**
 * Introduce a failure. We can start or stop the failure.
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Stage(doc = "Introduce a failure. We can start or stop the failure.")
@Experimental
public class FailureStage  extends AbstractDistStage {

   @InjectTrait
   Failure failure;

   @Property(optional = false, doc = "Action")
   private String action;

   @Property(doc = "Expected value")
   private String expectedValue;

   @Override
   public DistStageAck executeOnWorker() {
      if ("createFailure".equals(action)) {
         failure.createFailure(action);
      } else if ("solveFailure".equals(action)) {
         failure.solveFailure(action);
      } else if ("checkIfFailurePresent".equals(action)) {
         if (expectedValue == null) {
            throw new NullPointerException("expectedValue must not be null");
         }
         boolean match = failure.checkIfFailurePresent(action, expectedValue);
         if (!match) {
            return errorResponse("Wrong expectedValue");
         }
      }
      return successfulResponse();
   }
}
