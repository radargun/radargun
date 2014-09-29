package org.radargun.stages.cache.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.radargun.Operation;

/**
 * Ratio-based selector of operations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class OperationSelector {
   public final int[] ratios;
   public final int max;
   public final Operation[] operations;

   public OperationSelector(int[] ratios, int max, Operation[] operations) {
      this.ratios = ratios;
      this.max = max;
      this.operations = operations;
   }

   public Operation next(Random random) {
      int x = random.nextInt(max);
      int index = Arrays.binarySearch(ratios, x);
      if (index < 0) {
         index = -index - 1;
      } else {
         index = index + 1;
      }
      return operations[index];
   }

   public static class Builder {
      private int max = 0;
      private ArrayList<Integer> ratios = new ArrayList<>();
      private ArrayList<Operation> operations = new ArrayList<>();

      public Builder add(Operation op, int ratio) {
         if (ratio == 0) return this;
         if (ratio < 0) throw new IllegalArgumentException("Ratio must be >= 0: " + ratio);
         ratios.add(max + ratio);
         max += ratio;
         operations.add(op);
         return this;
      }

      public OperationSelector build() {
         if (max == 0) throw new IllegalStateException("No operations/ratios defined");
         int[] ratios = new int[this.ratios.size()];
         for (int i = 0; i < ratios.length; ++i) ratios[i] = this.ratios.get(i);
         return new OperationSelector(ratios, max, operations.toArray(new Operation[operations.size()]));
      }
   }
}
