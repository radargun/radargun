package org.radargun.stages.cache.test;

import java.util.Random;

import org.radargun.Operation;
import org.radargun.utils.Selector;

/**
 * Ratio-based selector of operations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class OperationSelector extends Selector<Operation> {

   public OperationSelector(int max, Operation[] operations, int[] ratios) {
      super(max, operations, ratios);
   }

   public Operation next(Random random) {
      return select(random.nextInt(max));
   }

   public static class Builder extends Selector.Builder<Operation, OperationSelector> {
      public Builder() {
         super(Operation.class);
      }

      @Override
      protected OperationSelector newSelector(int[] ratios) {
         return new OperationSelector(max, options.toArray(new Operation[options.size()]), ratios);
      }
   }
}
