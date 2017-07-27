package org.radargun.stages;

import org.radargun.Operation;
import org.radargun.StrongCounterInvocations;
import org.radargun.Version;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.OperationSelector;
import org.radargun.stages.test.RatioOperationSelector;
import org.radargun.stages.test.Stressor;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.StrongCounterOperations;

/**
 * @author Martin Gencur
 */
@Namespace(name = StrongCounterTestStage.NAMESPACE)
@Stage(doc = "Tests a strong clustered counter")
public class StrongCounterTestStage extends TestStage {

   public static final String NAMESPACE = "urn:radargun:stages:counter:" + Version.SCHEMA_VERSION;

   @Property(doc = "Counter name.", optional = false)
   protected String counterName;

   @Property(doc = "Initial value of the counter expected by this stage. The test will start" +
      "counting from this value. Default is 0.")
   protected int initialValue = 0;

   @Property(doc = "Operation to test. Default is INCREMENT_AND_GET.")
   protected OperationName operationName = OperationName.INCREMENT_AND_GET;

   @Property(doc = "Delta to add for addAndGet operation. Default is 1.")
   protected int delta = 1;

   enum OperationName {
      INCREMENT_AND_GET, DECREMENT_AND_GET, ADD_AND_GET, COMPARE_AND_SET
   }

   @InjectTrait
   protected StrongCounterOperations counterOperations;

   @Override
   protected OperationSelector createOperationSelector() {
      //TODO: replace this with direct selection
      if (operationName.equals(OperationName.INCREMENT_AND_GET)) {
         return new RatioOperationSelector.Builder().add(StrongCounterOperations.INCREMENT_AND_GET, 1).build();
      } else if (operationName.equals(OperationName.DECREMENT_AND_GET)) {
         return new RatioOperationSelector.Builder().add(StrongCounterOperations.DECREMENT_AND_GET, 1).build();
      } else if (operationName.equals(OperationName.ADD_AND_GET)) {
         return new RatioOperationSelector.Builder().add(StrongCounterOperations.ADD_AND_GET, 1).build();
      } else if (operationName.equals(OperationName.COMPARE_AND_SET)) {
         return new RatioOperationSelector.Builder().add(StrongCounterOperations.COMPARE_AND_SET, 1).build();
      } else {
         throw new IllegalArgumentException("Unknown operation!");
      }
   }

   @Override
   public OperationLogic getLogic() {
      return new StrongCounterLogic();
   }

   /**
    *
    */
   protected class StrongCounterLogic extends OperationLogic {
      private StrongCounterOperations.StrongCounter counter;
      private long previousValue;

      public StrongCounterLogic() {
         this.previousValue = initialValue;
      }

      @Override
      public void init(Stressor stressor) {
         super.init(stressor);
         this.counter = counterOperations.getStrongCounter(counterName);
         log.warn("Transactions ignored for Counter operations!");
         stressor.setUseTransactions(false);//transactions for counter do not make sense
      }

      @Override
      public void run(Operation operation) throws RequestException {
         if (operation == StrongCounterOperations.INCREMENT_AND_GET) {
            Invocation<Long> invocation = new StrongCounterInvocations.IncrementAndGet(counter);
            long expectedValue = previousValue + 1;
            long currentValue = stressor.makeRequest(invocation);
            if (currentValue != expectedValue) {
               throw new IllegalStateException("Inconsistent counter! Expected value: "
                  + expectedValue + ", actual: " + currentValue);
            } else {
               previousValue = currentValue;
            }
         } else if (operation == StrongCounterOperations.DECREMENT_AND_GET) {
            Invocation<Long> invocation = new StrongCounterInvocations.DecrementAndGet(counter);
            long expectedValue = previousValue - 1;
            long currentValue = stressor.makeRequest(invocation);
            if (currentValue != expectedValue) {
               throw new IllegalStateException("Inconsistent counter! Expected value: "
                  + expectedValue + ", actual: " + currentValue);
            } else {
               previousValue = currentValue;
            }
         } else if (operation == StrongCounterOperations.ADD_AND_GET) {
            Invocation<Long> invocation = new StrongCounterInvocations.AddAndGet(counter, delta);
            long expectedValue = previousValue + delta;
            long currentValue = stressor.makeRequest(invocation);
            if (currentValue != expectedValue) {
               throw new IllegalStateException("Inconsistent counter! Expected value: "
                  + expectedValue + ", actual: " + currentValue);
            } else {
               previousValue = currentValue;
            }
         } else if (operation == StrongCounterOperations.COMPARE_AND_SET) {
            long expectedValue = previousValue;
            long update = previousValue + 1; //the update will be just previous value plus 1
            Invocation<Boolean> invocation = new StrongCounterInvocations.CompareAndSet(counter, expectedValue, update);
            boolean success = stressor.makeRequest(invocation);
            if (!success) {
               throw new IllegalStateException("Inconsistent counter! Expected value: " + expectedValue);
            } else {
               previousValue = update;
            }
         } else throw new IllegalArgumentException(operation.name);
      }
   }
}
