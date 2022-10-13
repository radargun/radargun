package org.radargun.stages;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.traits.CounterOperations;
import org.radargun.traits.InjectTrait;

/**
 * Checks the resulting value of the counter on each node. This stage is supposed to
 * be called after {@link CounterTestStage} is finished.
 *
 * @author Martin Gencur
 */
@Stage(doc = "Stage for checking resulting value of given counter.")
@Namespace(name = CounterTestStage.NAMESPACE)
public class CheckCounterStage extends AbstractDistStage {

   @Property(doc = "Expected value of the counter.", optional = false)
   public long expectedValue;

   @Property(doc = "Counter name.", optional = false)
   public String counterName;

   @InjectTrait
   protected CounterOperations counterOperations;

   @Override
   public DistStageAck executeOnWorker() {
      if (!isServiceRunning()) {
         log.info("Not running test on this worker as service is not running.");
         return successfulResponse();
      }
      CounterOperations.Counter counter = counterOperations.getCounter(counterName);
      try {
         long value = counter.getValue();
         if (value != expectedValue) {
            return errorResponse("Unexpected value of the counter: " + value + ", expected: " + expectedValue);
         }
      } catch (Exception e) {
         return errorResponse("Couldn't get the counter value", e);
      }
      return successfulResponse();
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      return super.processAckOnMain(acks);
   }
}
