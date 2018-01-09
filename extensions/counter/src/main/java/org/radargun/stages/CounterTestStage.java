package org.radargun.stages;


import org.radargun.CounterInvocations;
import org.radargun.Operation;
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
import org.radargun.traits.CounterOperations;
import org.radargun.traits.InjectTrait;


/**
 * @author Martin Gencur
 */
@Namespace(name = CounterTestStage.NAMESPACE)
@Stage(doc = "Tests a clustered/distributed counter")
public class CounterTestStage extends TestStage {

   public static final String NAMESPACE = "urn:radargun:stages:counter:" + Version.SCHEMA_VERSION;

   @Property(doc = "Counter name.", optional = false)
   protected String counterName;

   @Property(doc = "Initial value of the counter expected by this stage. The test will start" +
      "counting from this value. Default is 0.")
   protected long initialValue = 0;

   @Property(doc = "Operation to test. Default is INCREMENT_AND_GET.")
   protected OperationName operationName = OperationName.INCREMENT_AND_GET;

   @Property(doc = "Delta to add for addAndGet operation. Default is 1.")
   protected long delta = 1;

   enum OperationName {
      INCREMENT_AND_GET, DECREMENT_AND_GET, ADD_AND_GET, COMPARE_AND_SET
   }

   @InjectTrait
   protected CounterOperations counterOperations;

   @Override
   protected OperationSelector createOperationSelector() {
      switch (operationName) {
         case INCREMENT_AND_GET:
            return new RatioOperationSelector.Builder().add(CounterOperations.INCREMENT_AND_GET, 1).build();
         case DECREMENT_AND_GET:
            return new RatioOperationSelector.Builder().add(CounterOperations.DECREMENT_AND_GET, 1).build();
         case ADD_AND_GET:
            return new RatioOperationSelector.Builder().add(CounterOperations.ADD_AND_GET, 1).build();
         case COMPARE_AND_SET:
            return new RatioOperationSelector.Builder().add(CounterOperations.COMPARE_AND_SET, 1).build();
         default: throw new IllegalArgumentException("Unknown operation!");
      }
   }

   @Override
   public OperationLogic getLogic() {
      return new CounterLogic();
   }

   protected class CounterLogic extends OperationLogic {
      private CounterOperations.Counter counter;
      private long previousValue;

      public CounterLogic() {
         this.previousValue = initialValue;
      }

      @Override
      public void init(Stressor stressor) {
         super.init(stressor);
         this.counter = counterOperations.getCounter(counterName);
         log.warn("Transactions ignored for Counter operations!");
         stressor.setUseTransactions(false);//transactions for counter do not make sense
      }

      @Override
      public void run(Operation operation) throws RequestException {
         if (operation == CounterOperations.INCREMENT_AND_GET) {
            Invocation<Long> invocation = new CounterInvocations.IncrementAndGet(counter);
            long currentValue = stressor.makeRequest(invocation);
            if (currentValue == previousValue) {
               throw new IllegalStateException("Inconsistent counter! Expected greater than " + previousValue);
            } else {
               previousValue = currentValue;
            }
         } else if (operation == CounterOperations.DECREMENT_AND_GET) {
            Invocation<Long> invocation = new CounterInvocations.DecrementAndGet(counter);
            long currentValue = stressor.makeRequest(invocation);
            if (currentValue == previousValue) {
               throw new IllegalStateException("Inconsistent counter! Expected lesser than " + previousValue);
            } else {
               previousValue = currentValue;
            }
         } else if (operation == CounterOperations.ADD_AND_GET) {
            Invocation<Long> invocation = new CounterInvocations.AddAndGet(counter, delta);
            long currentValue = stressor.makeRequest(invocation);
            if (currentValue == previousValue) {
               throw new IllegalStateException("Inconsistent counter! Expected value different from previous.");
            } else {
               previousValue = currentValue;
            }
         } else if (operation == CounterOperations.COMPARE_AND_SET) {
            long expectedValue = previousValue;
            long update = previousValue + 1;
            Invocation<Boolean> invocation = new CounterInvocations.CompareAndSet(counter, expectedValue, update);
            stressor.makeRequest(invocation);
            previousValue = update;
         } else {
            throw new IllegalArgumentException(operation.name);
         }
      }
   }
}
