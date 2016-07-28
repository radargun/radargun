package org.radargun.stages.test.legacy;

import java.util.Random;

import org.radargun.Operation;
import org.radargun.utils.Selector;

/**
 * Ratio-based selector of operations
 */
public class RatioOperationSelector extends Selector<Operation> implements OperationSelector {

   public RatioOperationSelector(int max, Operation[] operations, int[] ratios) {
      super(max, operations, ratios);
   }

   @Override
   public void start() {
   }

   @Override
   public Operation next(Random random) {
      return select(random.nextInt(max));
   }

   public static class Builder extends Selector.Builder<Operation, RatioOperationSelector> {
      public Builder() {
         super(Operation.class);
      }

      @Override
      protected RatioOperationSelector newSelector(int[] ratios) {
         return new RatioOperationSelector(max, options.toArray(new Operation[options.size()]), ratios);
      }
   }
}
